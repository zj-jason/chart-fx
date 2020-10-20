package de.gsi.microservice.aggregate.lsa;

import com.squareup.moshi.*;
import com.squareup.moshi.adapters.Rfc3339DateJsonAdapter;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import okhttp3.sse.EventSources;
import okio.BufferedSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Type;
import java.time.Duration;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * LSA REST Client for Middle Tier Services.
 * Tracks LSA trims and allows to resolve timing contexts to LSA indices.
 * Also allows to retrieve current settings and get notified if settings change.
 * Trims can be performed using the RMI interface.
 * 
 * @author Alexander Krimm
 */
public class LsaRestContextService {
    public static final Logger LOGGER = LoggerFactory.getLogger(LsaRestContextService.class);
    public static final String LSA_REST_VERSION = "v2";
    public static final String LSA_REST_URL = "https://restpro00a.acc.gsi.de/lsa/client/" + LSA_REST_VERSION + '/';
    public static final String LSA_REST_SSE_ENDPOINT = "resident_patterns_change";
    private static final int MAX_BACK_OFF = 10_000; // maximum time between SSE connection retries

    private final Map<String, LsaPattern> knownContexts = new TreeMap<>(); // store all contexts known by the service
    private final Map<String, LsaSettingSubscription<?>> settingSubscriptions = new TreeMap<>(); // store all setting subscriptions
    private final char multiplexingType = 'C';

    private Consumer<ResidentPatternUpdate> updateCallback;

    private final OkHttpClient httpClient;
    private int backOff = 0; // back of time in ms
    private final Moshi moshi; // json serializer
    ScheduledExecutorService scheduledExecutor = new ScheduledThreadPoolExecutor(4);
    // private boolean performDrive; // whether to perform the drive after performing a trim
    // private final boolean persist = false; // whether to persist LSA changes
    // private SettingPartEnum settingPart = SettingPartEnum.TARGET;

    public LsaRestContextService() {
        httpClient = new OkHttpClient.Builder().retryOnConnectionFailure(true).readTimeout(Duration.ZERO).build();
        moshi = new Moshi.Builder()//
                .add(Date.class, new Rfc3339DateJsonAdapter()) // adapter to parse dates from the json adapter
                .add(FairSelector.class, new JsonAdapter<FairSelector>() {
                    @Override
                    public FairSelector fromJson(JsonReader reader) throws IOException {
                        if (reader.peek() == JsonReader.Token.NULL) {
                            return reader.nextNull();
                        }
                        String string = reader.nextString();
                        return new FairSelector(string);
                    }

                    @Override
                    public void toJson(JsonWriter writer, FairSelector value) throws IOException {
                        if (value == null) {
                            writer.nullValue();
                        } else {
                            String string = value.toString();
                            writer.value(string);
                        }
                    }
                }).build();
    }

    /**
     * Call a function with an exponential back-off.
     * 
     * @param callback what to run
     */
    private void backOff(Runnable callback) {
        if (backOff == 0) {
            backOff += 100;
            callback.run();
        } else {
            scheduledExecutor.schedule(callback, backOff, TimeUnit.MILLISECONDS);
            backOff = Math.min(backOff * 2, MAX_BACK_OFF);
        }
    }

    public Map<Long, String> getAvailableContexts() {
        return knownContexts.entrySet().stream().collect(Collectors.<Entry<String, LsaPattern>, Long, String> toMap(
                entry -> entry.getValue().chains.values().stream().findFirst().get().lsaChainId, Entry::getKey));
    }

    /**
     * Update all patterns in changed using the LSA REST interface
     */
    private void getPatterns(List<String> changed) {
        JsonAdapter<LsaStatus> statusAdapter = moshi.adapter(LsaStatus.class);
        Request request = new Request.Builder().url(LSA_REST_URL + "status").build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful())
                throw new IOException("Unexpected code " + response);
            LsaStatus lsaStatus = statusAdapter.fromJson(response.body().source());
            lsaStatus.residentPatterns.forEach((patternName, pattern) -> {
                if (!changed.contains(patternName)) {
                    return;
                }
                if (pattern.chains == null) {
                    return;
                }
                pattern.chains.forEach((chainName, chain) -> {
                    if (chain.processes != null) {
                        chain.processes.forEach((processName, process) -> {
                            chain.chainId = (int) process.selector.get('C'); // TODO: verify that data is consistent
                            process.processId = (int) process.selector.get('P');
                            process.lsaProcessId = processName.hashCode();
                        });
                    }
                    if (chain.subchains != null) {
                        chain.subchains.forEach((subChainName, subChain) -> {
                            if (subChain.processes != null) {
                                subChain.processes.forEach((processName, process) -> {
                                    chain.chainId = (int) process.selector.get('C'); // TODO: verify that data is consistent
                                    subChain.sequenceId = (int) process.selector.get('S'); // TODO: verify that data is consistent
                                    process.processId = (int) process.selector.get('P');
                                    process.lsaProcessId = processName.hashCode();
                                });
                            }
                            subChain.lsaSequenceId = subChainName.hashCode(); // hack, because real id cannot be accessed
                        });
                    }
                    chain.lsaChainId = chainName.hashCode(); // hack because we cannot access the real chain id
                });
                pattern.resident = true;
                for (LsaSettingSubscription<?> sub : settingSubscriptions.values()) {
                    Object oldValue = sub.value;
                    getSettingDouble(sub.setting, sub.context, sub.process);
                    // notify subscriptions
                    if (!oldValue.equals(sub.value)) {
                        // sub.callback.accept(oldValue, (Object) sub.value);
                        LOGGER.atDebug().addArgument(sub).addArgument(oldValue).addArgument(sub.value) .log("Subscription {} changed: {} -> {}");
                    } else {
                        LOGGER.atDebug().addArgument(sub).addArgument(oldValue) .log("Subscription {} did not change: {}");
                    }
                }
                // update known Context
                knownContexts.put(patternName, pattern);
            });
        } catch (IOException e) {
            LOGGER.atError().log("Error: " + e.getMessage());
        }
    }

    public int getSettingInt(String setting, String context, String process) {
        // TODO: cache values
        final Type settingType = Types.newParameterizedTypeWithOwner(LsaRestContextService.class, LsaSetting.class,
                Integer.class);
        LsaSetting<Integer> lsaSetting = getLsaSetting(settingType, setting, context, process);
        if (lsaSetting != null)
            return lsaSetting.value;
        return 0;
    }

    public double getSettingDouble(String setting, String context, String process) {
        // TODO: cache values
        final Type settingType = Types.newParameterizedTypeWithOwner(LsaRestContextService.class, LsaSetting.class,
                Double.class);
        LsaSetting<Double> lsaSetting = getLsaSetting(settingType, setting, context, process);
        if (lsaSetting != null)
            return lsaSetting.value;
        return 0.0;
    }

    public LsaSettingFunction getSettingFunction(String setting, String context, String process) {
        // TODO: cache values
        final Type settingType = Types.newParameterizedTypeWithOwner(LsaRestContextService.class, LsaSetting.class,
                LsaSettingFunction.class);
        LsaSetting<LsaSettingFunction> lsaSetting = getLsaSetting(settingType, setting, context, process);
        if (lsaSetting != null)
            return lsaSetting.value;
        return null;
    }

    public LsaSettingFunction[] getSettingFunctionArray(String setting, String context, String process) {
        // TODO: cache values
        final Type settingType = Types.newParameterizedTypeWithOwner(LsaRestContextService.class, LsaSetting.class,
                LsaSettingFunction[].class);
        LsaSetting<LsaSettingFunction[]> lsaSetting = getLsaSetting(settingType, setting, context, process);
        if (lsaSetting != null)
            return lsaSetting.value;
        return new LsaSettingFunction[0];
    }

    public boolean[][] getSettingBoolArray2D(String setting, String context, String process) {
        // TODO: cache values
        final Type settingType = Types.newParameterizedTypeWithOwner(LsaRestContextService.class, LsaSetting.class,
                boolean[][].class);
        LsaSetting<boolean[][]> lsaSetting = getLsaSetting(settingType, setting, context, process);
        if (lsaSetting != null)
            return lsaSetting.value;
        return new boolean[0][0];
    }

    public int[][] getSettingIntArray2D(String setting, String context, String process) {
        // TODO: cache values
        final Type settingType = Types.newParameterizedTypeWithOwner(LsaRestContextService.class, LsaSetting.class,
                int[][].class);
        LsaSetting<int[][]> lsaSetting = getLsaSetting(settingType, setting, context, process);
        if (lsaSetting != null)
            return lsaSetting.value;
        return new int[0][0];
    }

    public String[] getSettingStringArray(String setting, String context, String process) {
        // TODO: cache values
        final Type settingType = Types.newParameterizedTypeWithOwner(LsaRestContextService.class, LsaSetting.class,
                String[].class);
        LsaSetting<String[]> lsaSetting = getLsaSetting(settingType, setting, context, process);
        if (lsaSetting != null)
            return lsaSetting.value;
        return new String[0];
    }

    private <T> LsaSetting<T> getLsaSetting(final Type settingType, String setting, String context, String process) {
        final JsonAdapter<LsaSetting<T>> statusAdapter = moshi.adapter(settingType);
        final FairLsaSelector ctx = new FairLsaSelector(context);
        long ctxId = ctx.get(multiplexingType);
        Optional<Entry<String, LsaPattern>> result = knownContexts.entrySet().stream()
                .filter(e -> e.getValue().chains.values().stream().findFirst().get().chainId == ctxId).findAny();
        final String pattern = result.map(Entry::getKey)
                .orElseThrow(() -> new IllegalArgumentException("Context not available"));
        final String seq = pattern + ".C1";
        final String processString = seq + process;
        final String contextString = pattern + '/' + seq + '/' + processString + '/';
        Request request = new Request.Builder().url(LSA_REST_URL + "settings/" + contextString + setting).build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful())
                throw new IOException("Unexpected code " + response);
            LsaSetting<T> lsaSetting;
            try (ResponseBody body = response.body(); BufferedSource source = body.source()) {
                lsaSetting = statusAdapter.fromJson(source);
            }
            return lsaSetting;
        } catch (IOException e) {
            LOGGER.atError().log("Error: " + e.getMessage());
            return null;
        }
    }

    public long lookup(String context) {
        if (multiplexingType != 'C') {
            throw new UnsupportedOperationException("lookup not implemented for multiplexing types other than chain");
        }
        FairSelector selector = new FairSelector(context);
        final long chainId = selector.get(multiplexingType);
        Optional<Entry<String, LsaPattern>> result = knownContexts.entrySet().stream()
                .filter(e -> e.getValue().chains.values().stream().findFirst().get().chainId == chainId).findAny();
        if (result.isPresent()) {
            return result.get().getValue().chains.values().stream().findAny().map(e -> e.lsaChainId).orElse(0L);
        }
        return 0;
    }

    // /**
    //  * Performs an LSA trim using the RMI interface
    //  */
    // @Override
    // public void performTrim(String setting, Object value, String context, String process) {
    //     LOGGER.atDebug().addArgument(setting).addArgument(context).addArgument(process).addArgument(value)
    //             .log("Trimming {}@{}.{} = {}");
    //     final String bpc = context;
    //     final String beamProcessName = context + '.' + process;
    //     final String parameterName = setting;
    //     final BpcSettingsCache bpcCache = LsaSettingsCache.getBpcSettingsCache(bpc);
    //     ImmutableValue immutableValue;
    //     if (value instanceof Double) {
    //         immutableValue = ValueFactory.createScalar(((Double) value).doubleValue());
    //     } else if (value instanceof double[][] && ((double[][]) value).length == 2) {
    //         immutableValue = ValueFactory.createFunction(((double[][]) value)[0], ((double[][]) value)[1]);
    //     } else {
    //         throw new UnsupportedOperationException(
    //                 "Value is of unsupported type: " + value.getClass().getSimpleName());
    //     }

    //     final Setting oldSetting = bpcCache.getSetting(beamProcessName, parameterName);
    //     if (oldSetting == null) {
    //         LOGGER.atWarn().addArgument(beamProcessName).log("oldSetting for beamProcessName= '{}'is null");
    //     }

    //     bpcCache.trim(beamProcessName, parameterName, settingPart, persist, performDrive, immutableValue);
    // }

    /**
     * Flags patterns that are not resident any more.
     * They can later be removed.
     * 
     * @param keySet The names of the currently resident patterns.
     */
    private void purgeOldPatterns(Set<String> keySet) {
        knownContexts.entrySet().stream() //
                .filter(entry -> entry.getValue().resident && !keySet.contains(entry.getKey())) //
                .forEach(entry -> entry.getValue().resident = false); //
    }

    /**
     * Connects to the LSA REST/SSE endpoint and reconnects automatically if the connection is terminated.
     * Uses an exponential back-off strategy for cases where the endpoint is temporarily unavailable.
     */
    public void reconnect() {
        JsonAdapter<ResidentPatternUpdate> trimUpdatesAdapter = moshi.adapter(ResidentPatternUpdate.class);
        Request sseRequest = new Request.Builder().url(LSA_REST_URL + LSA_REST_SSE_ENDPOINT)
                .addHeader("Accept", "text/event-stream").build();
        EventSources.createFactory(httpClient).newEventSource(sseRequest, new EventSourceListener() {
            @Override
            public void onClosed(EventSource eventSource) {
                LOGGER.atDebug().log("Closed SSE Subscription");
                backOff(LsaRestContextService.this::reconnect);
            }

            @Override
            public void onEvent(EventSource eventSource, String id, String type, String data) {
                try {
                    ResidentPatternUpdate resPatterns = trimUpdatesAdapter.fromJson(data);
                    LOGGER.atTrace().addArgument(resPatterns.residentPatterns.size()).log("Received SSE Event, number of resident patterns: {}");
                    updatePatterns(resPatterns.residentPatterns);
                    purgeOldPatterns(resPatterns.residentPatterns.keySet());
                    if (updateCallback != null) {
                        updateCallback.accept(resPatterns);
                    }
                } catch (IOException e) {
                    LOGGER.atError().log("Error parsing json");
                }
            }

            @Override
            public void onFailure(EventSource eventSource, Throwable t, Response response) {
                LOGGER.atError().log("SSE Subscription Failed: " + t);
                backOff(LsaRestContextService.this::reconnect);
            }

            @Override
            public void onOpen(EventSource eventSource, Response response) {
                backOff = 0; // reset exponential back-off
                LOGGER.atDebug().log("Opened SSE Subscription");
            }
        });
    }

    public <T> void subscribeToSetting(String setting, BiConsumer<T, T> callback, String process) {
        LsaSettingSubscription<T> subscription = new LsaSettingSubscription<>();
        subscription.callback = callback;
        subscription.value = null;
        // initialize setting
        for (LsaPattern pattern: knownContexts.values()) {
            for (LsaChainContext chain : pattern.chains.values()) {
                String selector = new FairSelector(chain.chainId, -1, -1, -1).toString();
                try {
                    getSettingDouble(setting, selector, process);
                } catch (IllegalArgumentException e) {
                    LOGGER.atDebug().addArgument(e.getMessage()).log("could not get setting: {}");
                }
            }
        }
        settingSubscriptions.put(setting + '#' + process, subscription);
    }

    /**
     * Check if the pattern was updated and if it was, it updates all information for the pattern.
     */
    private void updatePatterns(Map<String, TrimUpdate> updates) {
        List<String> changed = new ArrayList<>();
        for (Entry<String, TrimUpdate> entry : updates.entrySet()) {
            if (!knownContexts.containsKey(entry.getKey())
                    || entry.getValue().lastTrimmed.after(knownContexts.get(entry.getKey()).lastTrimmed)) {
                LOGGER.atDebug().log("updating:" + entry);
                changed.add(entry.getKey());
            }
        }
        if (!changed.isEmpty()) { // update all patterns which where trimmed since the last update
            getPatterns(changed);
        }
    }

    public void setUpdateCallback(final Consumer<ResidentPatternUpdate> updateCallback) {
        this.updateCallback = updateCallback;
    }

    // POJOs for the LSA updates SSE endpoint
    public static class ResidentPatternUpdate implements Serializable{
        private static final long serialVersionUID = 1L;
        @Json(name = "RESIDENT_PATTERNS")
        public Map<String, TrimUpdate> residentPatterns;
    }

    public static class TrimUpdate implements Serializable{
        private static final long serialVersionUID = 1L;
        @Json(name = "LAST_TRIMMED")
        public Date lastTrimmed;

        @Override
        public String toString() {
            return "lastTrimmed=" + lastTrimmed.toString();
        }
    }

    // POJOs for LSA status endpoint
    static class LsaStatus implements Serializable{
        private static final long serialVersionUID = 1L;
        @Json(name = "RESIDENT_PATTERNS")
        public Map<String, LsaPattern> residentPatterns;
    }

    static class LsaPattern implements Serializable{
        private static final long serialVersionUID = 1L;
        @Json(name = "LAST_TRIMMED")
        public Date lastTrimmed;
        @Json(name = "REPETITION_COUNT")
        public String repetitionCount;
        @Json(name = "CHAINS")
        public Map<String, LsaChainContext> chains;
        public transient boolean resident;
    }

    static class LsaChainContext implements Serializable{
        private static final long serialVersionUID = 1L;
        public transient long lsaChainId;
        @Json(name = "BEAMPROCESSES")
        public Map<String, LsaProcessContext> processes;
        @Json(name = "CHAINS")
        public Map<String, LsaSubChainContext> subchains;
        public transient int chainId; // timing
    }

    static class LsaSubChainContext implements Serializable{
        private static final long serialVersionUID = 1L;
        public transient int sequenceId;
        public transient long lsaSequenceId;
        @Json(name = "BEAMPROCESSES")
        public Map<String, LsaProcessContext> processes;
        public transient int chainId; // timing
        @Json(name = "IS_SKIPPABLE")
        public boolean isSkippable;
        @Json(name = "IS_REPEATABLE")
        public boolean isRepeatable;
    }

    static class LsaProcessContext implements Serializable{
        private static final long serialVersionUID = 1L;
        public long lsaProcessId;
        public transient int processId; // timing
        @Json(name = "USER")
        public FairSelector selector;
    }

    /**
     * LSA Settings REST endpoint pojo
     */
    static class LsaSetting<T> implements Serializable{
        private static final long serialVersionUID = 1L;
        @Json(name = "accelerator")
        public String accelerator;
        @Json(name = "particle_transfer")
        public String particleTransfer;
        @Json(name = "device")
        public String device;
        @Json(name = "parameter_type")
        public String parameterType;
        @Json(name = " value_type")
        public String valueType;
        @Json(name = "trimable")
        public boolean trimable;
        @Json(name = "virtual")
        public boolean virtual;
        @Json(name = "value")
        public T value;
    }

    public static class LsaSettingFunction implements Serializable{
        private static final long serialVersionUID = 1L;
        @Json(name = "x")
        public double[] x;
        @Json(name = "y")
        public double[] y;

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder();
            sb.append("LSA Function: \n");
            sb.append("  x: ").append(Arrays.toString(x)).append('\n');
            sb.append("  y: ").append(Arrays.toString(y));
            return sb.toString();
        }
    }

    /**
     * Helper class for information about callbacks.
     */
    private static class LsaSettingSubscription<T> implements Serializable{
        private static final long serialVersionUID = 1L;
        public BiConsumer<T, T> callback; // callback oldValue, newValue
        public String setting;
        public String process;
        public String context;
        public T value; // last updated value per context
    }
}

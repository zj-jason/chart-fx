package de.gsi.mircoservice.aggregate.disruptor;

import de.gsi.dataset.spi.utils.MultiArrayDouble;
import de.gsi.microservice.aggregate.disruptor.AccAggregator;
import de.gsi.microservice.aggregate.disruptor.ContextDispatcher;
import de.gsi.microservice.cmwlight.CmwLightClient;
import de.gsi.microservice.cmwlight.DirectoryLightClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Map;

public class AggregationSample {
    private static final Logger LOGGER = LoggerFactory.getLogger(AggregationSample.class);
    public static final String SELECTOR_ALL = "FAIR.SELECTOR.ALL";
    public static final String DIGITIZER = "GSCD001";
    public static final String SNOOP_PROP = "SnoopTriggerEvents";
    public static final String DAQ_PROP = "AcquisitionDAQ";
    public static final String DIGITIZER_CHANNEL = "GS02P:SumY:Triggered@28MHz";
    public static final int ACQUISITION_MODE_TRIGGERED = 4;
    public static final String ACCT_SIS = "GS09DT_S";
    public static final String ACQUISITION_PROP = "Acquisition";

    public static void main(String[] args) throws DirectoryLightClient.DirectoryClientException, InterruptedException, CmwLightClient.RdaLightException {
        final AccAggregator aggregator = new AccAggregator();

        // debug output print all events which are published to the event store
        aggregator.addProcessor((event, sequence, endOfBatch) -> {
            if (event.data instanceof AccAggregator.CmwUpdate) {
                LOGGER.atDebug().addArgument(event.data).log("cmw update: {}");
            } else if (event.data instanceof AccAggregator.LsaUpdate) {
                LOGGER.atDebug().addArgument(((AccAggregator.LsaUpdate) event.data).trims).log("trims update: {}");
            } else {
                LOGGER.atDebug().addArgument(event.data).log("received unknown Event {}");
            }
        });

        // add processor which spins of a dedicated aggregator for each active BPC
        aggregator.addProcessor(new ContextDispatcher());

        // start the aggregation process
        aggregator.start();

        // digitizer snoop events
        aggregator.addCmwLightSubscription(DIGITIZER, SNOOP_PROP+"nonexistent", SELECTOR_ALL, SnoopAcquisition.class);

        // digitizer acquisition
        //aggregator.addCmwLightSubscription(DIGITIZER, DAQ_PROP, SELECTOR_ALL,
        //        Map.of("acquisitionModeFilter", ACQUISITION_MODE_TRIGGERED, "channelNameFilter", DIGITIZER_CHANNEL), AcquisitionDAQ.class);

        // acct acquisition
        //aggregator.addCmwLightSubscription(ACCT_SIS, ACQUISITION_PROP, SELECTOR_ALL, Map.of("requestPartialData", false), AcctAcquisition.class);

        // LsaTrimsAndRescheduling updates
        // aggregator.startLsaUpdateSubscription();
    }

     public static class AcctAcquisition {
        public boolean partialData;
        int gainSet;
        double range;
        double actualParticleRange;
        double frequency;
        double gateLength;
        String gateLength_units;
        int multiTurnCount;
        MultiArrayDouble rawData;
        String rawData_units;
        MultiArrayDouble intensity;
        String intensity_units;
        MultiArrayDouble intensityError;
        String intensityError_units;
        MultiArrayDouble current;
        String current_units;
        MultiArrayDouble currentError;
        String currentError_units;
        int acqStatus;
        int ionChargeState;
        double maxIntensity;
        boolean underrage;
        boolean overrange;
        int processIndex;
        int sequenceIndex;
        int chainIndex;
        int eventNumber;
        int timingGroupId;
        long acquisitionStamp;
        long eventStamp;
        long processStartStamp;
        long sequenceStartStamp;
        long chainStartStamp;

        @Override
        public String toString() {
            return "AcctAcquisition{" +
                    "partialData=" + partialData +
                    ", gainSet=" + gainSet +
                    ", range=" + range +
                    ", actualParticleRange=" + actualParticleRange +
                    ", frequency=" + frequency +
                    ", gateLength=" + gateLength +
                    ", gateLength_units='" + gateLength_units + '\'' +
                    ", multiTurnCount=" + multiTurnCount +
                    ", rawData=" + rawData +
                    ", rawData_units='" + rawData_units + '\'' +
                    ", intensity=" + intensity +
                    ", intensity_units='" + intensity_units + '\'' +
                    ", intensityError=" + intensityError +
                    ", intensityError_units='" + intensityError_units + '\'' +
                    ", current=" + current +
                    ", current_units='" + current_units + '\'' +
                    ", currentError=" + currentError +
                    ", currentError_units='" + currentError_units + '\'' +
                    ", acqStatus=" + acqStatus +
                    ", ionChargeState=" + ionChargeState +
                    ", maxIntensity=" + maxIntensity +
                    ", underrage=" + underrage +
                    ", overrange=" + overrange +
                    ", processIndex=" + processIndex +
                    ", sequenceIndex=" + sequenceIndex +
                    ", chainIndex=" + chainIndex +
                    ", eventNumber=" + eventNumber +
                    ", timingGroupId=" + timingGroupId +
                    ", acquisitionStamp=" + acquisitionStamp +
                    ", eventStamp=" + eventStamp +
                    ", processStartStamp=" + processStartStamp +
                    ", sequenceStartStamp=" + sequenceStartStamp +
                    ", chainStartStamp=" + chainStartStamp +
                    '}';
        }
    }

    public static class AcquisitionDAQ {
        public String refTriggerName;
        public long refTriggerStamp;
        public float[] channelTimeSinceRefTrigger;
        public float channelUserDelay;
        public float channelActualDelay;
        public String channelName;
        public float[] channelValue;
        public float[] channelError;
        public String channelUnit;
        public int status;
        public float channelRangeMin;
        public float channelRangeMax;
        public float temperature;
        public int processIndex;
        public int sequenceIndex;
        public int chainIndex;
        public int eventNumber;
        public int timingGroupId;
        public long acquisitionStamp;
        public long eventStamp;
        public long processStartStamp;
        public long sequenceStartStamp;
        public long chainStartStamp;

        @Override
        public String toString() {
            return "AcquisitionDAQ{" +
                    "refTriggerName='" + refTriggerName + '\'' +
                    ", refTriggerStamp=" + refTriggerStamp +
                    ", channelTimeSinceRefTrigger(n=" + channelTimeSinceRefTrigger.length + ")=" + Arrays.toString(Arrays.copyOfRange(channelTimeSinceRefTrigger,0,3)) +
                    ", channelUserDelay=" + channelUserDelay +
                    ", channelActualDelay=" + channelActualDelay +
                    ", channelName='" + channelName + '\'' +
                    ", channelValue(n=" + channelValue.length + ")=" + Arrays.toString(Arrays.copyOfRange(channelValue,0,3)) +
                    ", channelError(n=" + channelError.length + ")=" + Arrays.toString(Arrays.copyOfRange(channelError,0,3)) +
                    ", channelUnit='" + channelUnit + '\'' +
                    ", status=" + status +
                    ", channelRangeMin=" + channelRangeMin +
                    ", channelRangeMax=" + channelRangeMax +
                    ", temperature=" + temperature +
                    ", processIndex=" + processIndex +
                    ", sequenceIndex=" + sequenceIndex +
                    ", chainIndex=" + chainIndex +
                    ", eventNumber=" + eventNumber +
                    ", timingGroupId=" + timingGroupId +
                    ", acquisitionStamp=" + acquisitionStamp +
                    ", eventStamp=" + eventStamp +
                    ", processStartStamp=" + processStartStamp +
                    ", sequenceStartStamp=" + sequenceStartStamp +
                    ", chainStartStamp=" + chainStartStamp +
                    '}';
        }
    }

    public static class SnoopAcquisition {
        public String TriggerEventName;
        public long acquisitionStamp;
        public int chainIndex;
        public long chainStartStamp;
        public int eventNumber;
        public long eventStamp;
        public int processIndex;
        public long processStartStamp;
        public int sequenceIndex;
        public long sequenceStartStamp;
        public int timingGroupID;

        @Override
        public String toString() {
            return "SnoopAcquisition{" +
                    "TriggerEventName='" + TriggerEventName + '\'' +
                    ", acquisitionStamp=" + acquisitionStamp +
                    ", chainIndex=" + chainIndex +
                    ", chainStartStamp=" + chainStartStamp +
                    ", eventNumber=" + eventNumber +
                    ", eventStamp=" + eventStamp +
                    ", processIndex=" + processIndex +
                    ", processStartStamp=" + processStartStamp +
                    ", sequenceIndex=" + sequenceIndex +
                    ", sequenceStartStamp=" + sequenceStartStamp +
                    ", timingGroupID=" + timingGroupID +
                    '}';
        }
    }
}

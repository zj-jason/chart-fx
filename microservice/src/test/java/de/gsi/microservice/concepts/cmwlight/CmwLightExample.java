package de.gsi.microservice.concepts.cmwlight;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import de.gsi.serializer.IoClassSerialiser;
import de.gsi.serializer.spi.CmwLightSerialiser;
import de.gsi.serializer.spi.FastByteBuffer;
import org.zeromq.ZMQ;

public class CmwLightExample { // NOPMD is not a utility class but a sample
    private final static String CMW_NAMESERVER = "cmwpro00a.acc.gsi.de:5021";
    private final static String DEVICE = "GSCD001";
    private final static String PROPERTY = "SnoopTriggerEvents";
    private final static String SELECTOR = "FAIR.SELECTOR.ALL";

    public static void main(String[] args) throws DirectoryLightClient.DirectoryClientException, CmwLightProtocol.RdaLightException {
        subscribeAcqFromDigitizer();
    }

    public static void subscribeAcqFromDigitizer() throws DirectoryLightClient.DirectoryClientException, CmwLightProtocol.RdaLightException {
        final DirectoryLightClient directoryClient = new DirectoryLightClient(CMW_NAMESERVER);
        DirectoryLightClient.Device device = directoryClient.getDeviceInfo(Collections.singletonList(DEVICE)).get(0);
        System.out.println(device);
        final String address = device.servers.stream().findFirst().orElseThrow().get("Address:");
        System.out.println("connect client to " + address);
        final CmwLightClient client = new CmwLightClient(address, ZMQ.context(1));
        final ZMQ.Poller poller = client.getContext().poller();
        poller.register(client.getSocket(), ZMQ.Poller.POLLIN);
        client.connect();

        System.out.println("starting subscription");
        Map<String, Object> filters = new HashMap<>();
        filters.put("acquisitionModeFilter", 4); // 4 = Triggered Acquisition Mode
        filters.put("channelNameFilter", "GS02P:SumY:Triggered@28MHz");
        final CmwLightClient.Subscription subscription = new CmwLightClient.Subscription(DEVICE, "AcquisitionDAQf", SELECTOR, filters);
        client.subscribe(subscription);
        final CmwLightClient.Subscription subscription2 = new CmwLightClient.Subscription(DEVICE, "AcquisitionDAQ", "FAIR.SELECTOR.S=2", filters);
        client.subscribe(subscription2);
        final CmwLightClient.Subscription subscription3 = new CmwLightClient.Subscription(DEVICE, "NonexistentProperty", "bogusSelector", filters);
        client.subscribe(subscription3);
        client.subscriptions.forEach((id, c) -> System.out.println(id + " -> " + c));

        int i = 0;
        while (i < 15) {
            poller.poll();
            final CmwLightProtocol.Reply result = client.receiveData();
            if (result != null && result.requestType == CmwLightProtocol.RequestType.NOTIFICATION_DATA) {
                final byte[] bytes = result.bodyData.getData();
                final IoClassSerialiser classSerialiser = new IoClassSerialiser(FastByteBuffer.wrap(bytes), CmwLightSerialiser.class);
                final AcquisitionDAQ acq = classSerialiser.deserialiseObject(AcquisitionDAQ.class);
                System.out.println("body: " + acq);
                i++;
            } else {
                if (result != null)
                    System.out.println(result);
            }
            i++;
        }
        client.subscriptions.forEach((id, c) -> System.out.println(id + " -> " + c));

        System.out.println("unsubscribe");
        // client.unsubscribe(subscription);
    }

    private static class AcquisitionDAQ {
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
            return "AcquisitionDAQ{"
                    + "refTriggerName='" + refTriggerName + '\'' + ", refTriggerStamp=" + refTriggerStamp + ", channelTimeSinceRefTrigger(n=" + channelTimeSinceRefTrigger.length + ")=" + Arrays.toString(Arrays.copyOfRange(channelTimeSinceRefTrigger, 0, 3)) + ", channelUserDelay=" + channelUserDelay + ", channelActualDelay=" + channelActualDelay + ", channelName='" + channelName + '\'' + ", channelValue(n=" + channelValue.length + ")=" + Arrays.toString(Arrays.copyOfRange(channelValue, 0, 3)) + ", channelError(n=" + channelError.length + ")=" + Arrays.toString(Arrays.copyOfRange(channelError, 0, 3)) + ", channelUnit='" + channelUnit + '\'' + ", status=" + status + ", channelRangeMin=" + channelRangeMin + ", channelRangeMax=" + channelRangeMax + ", temperature=" + temperature + ", processIndex=" + processIndex + ", sequenceIndex=" + sequenceIndex + ", chainIndex=" + chainIndex + ", eventNumber=" + eventNumber + ", timingGroupId=" + timingGroupId + ", acquisitionStamp=" + acquisitionStamp + ", eventStamp=" + eventStamp + ", processStartStamp=" + processStartStamp + ", sequenceStartStamp=" + sequenceStartStamp + ", chainStartStamp=" + chainStartStamp + '}';
        }
    }

    private static class SnoopAcquisition {
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
            return "SnoopAcquisition{"
                    + "TriggerEventName='" + TriggerEventName + '\'' + ", acquisitionStamp=" + acquisitionStamp + ", chainIndex=" + chainIndex + ", chainStartStamp=" + chainStartStamp + ", eventNumber=" + eventNumber + ", eventStamp=" + eventStamp + ", processIndex=" + processIndex + ", processStartStamp=" + processStartStamp + ", sequenceIndex=" + sequenceIndex + ", sequenceStartStamp=" + sequenceStartStamp + ", timingGroupID=" + timingGroupID + '}';
        }
    }
}

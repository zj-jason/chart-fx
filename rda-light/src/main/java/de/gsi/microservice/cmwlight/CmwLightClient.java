package de.gsi.microservice.cmwlight;

import de.gsi.serializer.DataType;
import de.gsi.serializer.FieldDescription;
import de.gsi.serializer.IoClassSerialiser;
import de.gsi.serializer.spi.CmwLightSerialiser;
import de.gsi.serializer.spi.FastByteBuffer;
import de.gsi.serializer.spi.WireDataFieldDescription;
import org.zeromq.*;
import zmq.util.Wire;

import java.lang.reflect.Type;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class CmwLightClient {
    private static final AtomicInteger connectionId = new AtomicInteger(0); // global counter incremented for each connection
    private final AtomicInteger channelId = new AtomicInteger(0); // connection local counter incremented for each channel
    private final ZContext context = new ZContext();
    private final ZMQ.Socket controlChannel;
    private final AtomicReference<ConnectionState> connectionState = new AtomicReference<>(ConnectionState.DISCONNECTED);
    private final String address;
    private final IoClassSerialiser serialiser = new IoClassSerialiser(new FastByteBuffer(0), CmwLightSerialiser.class);

    public ZMQ.Socket getSocket() {
        return controlChannel;
    }

    public enum ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED;
    }

    // Contents of the first frame determine the type of the message
    private final byte SERVER_CONNECT_ACK = (byte) 0x01;
    private final byte SERVER_REP = (byte) 0x02;
    private final byte SERVER_HB = (byte) 0x03;
    private final byte CLIENT_CONNECT = (byte) 0x20;
    private final byte CLIENT_REQ = (byte) 0x21;
    private final byte CLIENT_HB = (byte) 0x22;

    // Message Types in the descriptor (Last part of a message containing the type of each sub message)
    private static final byte MT_HEADER = 0;
    private static final byte MT_BODY = 1; //
    private static final byte MT_BODY_DATA_CONTEXT = 2;
    private static final byte MT_BODY_REQUEST_CONTEXT = 3;
    private static final byte MT_BODY_EXCEPTION = 4;

    // Field names for the Request Header
    public static final String EVENT_TYPE_TAG = "eventType";
    public static final String MESSAGE_TAG = "message";
    public static final String ID_TAG = "0";
    public static final String DEVICE_NAME_TAG = "1";
    public static final String REQ_TYPE_TAG = "2";
    public static final String OPTIONS_TAG = "3";
    public static final String CYCLE_NAME_TAG = "4";
    public static final String ACQ_STAMP_TAG = "5";
    public static final String CYCLE_STAMP_TAG = "6";
    public static final String UPDATE_TYPE_TAG = "7";
    public static final String SELECTOR_TAG = "8";
    public static final String CLIENT_INFO_TAG = "9";
    public static final String NOTIFICATION_ID_TAG = "a";
    public static final String SOURCE_ID_TAG = "b";
    public static final String FILTERS_TAG = "c";
    public static final String DATA_TAG = "x";
    public static final String SESSION_ID_TAG = "d";
    public static final String SESSION_BODY_TAG = "e";
    public static final String PROPERTY_NAME_TAG = "f";

    // request type used in request header REQ_TYPE_TAG
    public static final byte RT_GET = 0;
    public static final byte RT_SET = 1;
    public static final byte RT_CONNECT = 2;
    public static final byte RT_REPLY = 3;
    public static final byte RT_EXCEPTION = 4;
    public static final byte RT_SUBSCRIBE = 5;
    public static final byte RT_UNSUBSCRIBE = 6;
    public static final byte RT_NOTIFICATION_DATA = 7;
    public static final byte RT_NOTIFICATION_EXC = 8;
    public static final byte RT_SUBSCRIBE_EXCEPTION = 9;
    public static final byte RT_EVENT = 10; // Also used as close
    public static final byte RT_SESSION_CONFIRM = 11;

    // UpdateType
    public static final byte UT_NORMAL = (byte) 0;
    public static final byte UT_FIRST_UPDATE = (byte) 1; // Initial update sent when the subscription is created.
    public static final byte UT_IMMEDIATE_UPDATE = (byte) 2; //Update sent after the value has been modified by a set call.

    public CmwLightClient(String address) {
        controlChannel = context.createSocket(SocketType.DEALER);
        controlChannel.setIdentity(getIdentity().getBytes()); // hostname/process/id/channel
        controlChannel.setSndHWM(0);
        controlChannel.setRcvHWM(0);
        controlChannel.setLinger(0);
        this.address = address;
    }

    private String getIdentity() {
        String hostname;
        try {
            hostname = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            hostname = "localhost";
        }
        final long processId = ProcessHandle.current().pid();
        final int connectionId = CmwLightClient.connectionId.incrementAndGet();
        final int channelId = this.channelId.incrementAndGet();
        return hostname + '/' + processId + '/' + connectionId + '/' + channelId;
    }

    public void connect() throws RdaLightException {
        controlChannel.connect(address);
        if (connectionState.getAndSet(ConnectionState.CONNECTING) != ConnectionState.DISCONNECTED) {
            return;
        }
        controlChannel.send(new byte[] {CLIENT_CONNECT}, ZMQ.SNDMORE);
        controlChannel.send("1.0.0".getBytes());
        final ZMsg conAck = ZMsg.recvMsg(controlChannel);
        if (!Arrays.equals(conAck.pollFirst().getData(), new byte[] {SERVER_CONNECT_ACK})) {
            throw new RdaLightException(conAck.toString());
        }
    }

    public Reply receiveData() throws RdaLightException {
        final ZMsg data = ZMsg.recvMsg(controlChannel);
        final ZFrame firstFrame = data.pollFirst();
        if (Arrays.equals(firstFrame.getData(), new byte[]{SERVER_HB})) {
            return null;
        }
        if (!Arrays.equals(firstFrame.getData(), new byte[]{SERVER_REP})) {
            throw new RdaLightException("Expecting only messages of type Heartbeat or Reply but got: " + data);
        }
        final byte[] descriptor = data.pollLast().getData();
        if (descriptor[0] != MT_HEADER) {
            throw new RdaLightException("First message of SERVER_REP has to be of type MT_HEADER but is: "+ descriptor[0]);
        }
        final byte[] header = data.pollFirst().getData();
        serialiser.setDataBuffer(FastByteBuffer.wrap(header));
        final FieldDescription headerMap;
        try {
            headerMap = serialiser.parseWireFormat().getChildren().get(0);
        } catch (IllegalStateException e) {
            throw new RdaLightException("unparsable header: " + Arrays.toString(header) + "(" + new String(header) +")");
        }
        final FieldDescription reqTypeField = headerMap.findChildField(REQ_TYPE_TAG); // byte
        assertType(reqTypeField, byte.class);
        final byte reqType = (byte) ((WireDataFieldDescription) reqTypeField).data();
        switch (reqType) {
            case RT_SESSION_CONFIRM:
                System.out.println("received session confirm");
                return null;
            case RT_EVENT:
                System.out.println("received received event");
                return null;
            case RT_REPLY:
                System.out.println("received reply");
                return null;
            case RT_EXCEPTION:
                System.out.println("received exc");
                return null;
            case RT_SUBSCRIBE:
                // seems to be sent after subscription is accepted
                System.out.println("received subscription reply: " + Arrays.toString(header) + "(" + new String(header) +")");
                return null;
            case RT_SUBSCRIBE_EXCEPTION:
                System.out.println("received subscription exception");
                return null;
            case RT_NOTIFICATION_EXC:
                System.out.println("received notification exc");
                return null;
            case RT_NOTIFICATION_DATA:
                final FieldDescription idField = headerMap.findChildField(ID_TAG); //long
                assertType(idField, long.class);
                final long id = (long) ((WireDataFieldDescription) idField).data();
                final FieldDescription deviceNameField = headerMap.findChildField(DEVICE_NAME_TAG); // string
                assertType(deviceNameField, String.class);
                final String devName = (String) ((WireDataFieldDescription) deviceNameField).data();
                assert devName.length() == 0 ;
                final FieldDescription optionsField = headerMap.findChildField(OPTIONS_TAG);
                long notificationId = -1;
                if (optionsField != null) {
                    final FieldDescription notificationIdField = optionsField.findChildField(NOTIFICATION_ID_TAG); //long
                    assertType(notificationIdField, long.class);
                    notificationId = (long) ((WireDataFieldDescription) notificationIdField).data();
                }
                final FieldDescription updateTypeField = headerMap.findChildField(UPDATE_TYPE_TAG); // byte
                assertType(updateTypeField, byte.class);
                final byte updateType = (byte) ((WireDataFieldDescription) updateTypeField).data();
                final FieldDescription sessionIdField = headerMap.findChildField(SESSION_ID_TAG); //
                assertType(sessionIdField, String.class);
                final String sessionId = (String) ((WireDataFieldDescription) sessionIdField).data();
                assert sessionId.length() == 0;
                final FieldDescription propNameField = headerMap.findChildField(PROPERTY_NAME_TAG);
                assertType(propNameField, String.class);
                final String propName = (String) ((WireDataFieldDescription) propNameField).data();
                assert propName.length() == 0;
                if (descriptor.length != 3 || descriptor[1] != MT_BODY || descriptor[2] != MT_BODY_DATA_CONTEXT) {
                    throw new RdaLightException("Notification update does not contain the proper data");
                }
                return new CmwLightClient.SubscriptionUpdate(id, updateType, notificationId, data.pollFirst(), data.pollFirst());
            default:
                System.out.println("received unknown request type: " + reqType);
                return null;
        }

        // Reply result;
        // if (Arrays.equals(descriptor, new byte[] {MT_HEADER})) {
        //     result = new HeaderReply(); // this should probably just set some subscription status
        // } else if (Arrays.equals(descriptor , new byte[] {MT_HEADER, MT_BODY_EXCEPTION})) {
        //         result = new ExceptionReply();
        // } else if (!Arrays.equals(descriptor, new byte[] {MT_HEADER, MT_BODY, MT_BODY_DATA_CONTEXT})) {
        //     throw new RdaLightException("expected reply message, but got: "+ Arrays.toString(descriptor) +  " - " + Arrays.toString(data.getLast().getData()));
        // } else {
        //     result = new DataReply();
        // }
        // for (final byte desc : descriptor) {
        //     switch (desc) {
        //         case MT_HEADER:
        //             result.header = data.pollFirst();
        //             break;
        //         case MT_BODY:
        //             ((DataReply) result).dataBody = data.pollFirst();
        //             break;
        //         case MT_BODY_DATA_CONTEXT:
        //             ((DataReply) result).dataContext= data.pollFirst();
        //             break;
        //         case MT_BODY_REQUEST_CONTEXT:
        //             throw new RdaLightException("Unexpected body request context frame"); // we should never receive this
        //         case MT_BODY_EXCEPTION:
        //             ((ExceptionReply) result).exceptionBody = data.pollFirst();
        //             break;
        //         default:
        //             throw new RdaLightException("invalid message type (" + desc + "): "+ new String(data.pollFirst().getData()));
        //     }
        // }
        // return result;
    }

    private void assertType(final FieldDescription field, final Type type) {
        if (field.getType() != type) {
            throw new IllegalStateException("idField has wrong type");
        }
    }

    public void sendHeartBeat() {
        controlChannel.send(new byte[] {CLIENT_HB});
    }

    public void subscribe(final String testdevice, final String testprop, final String selector) {
        subscribe(testdevice, testprop, selector);
    }

    public void subscribe(final String testdevice, final String testprop, final String selector, final Map<String, Object> filters) throws RdaLightException {
        controlChannel.send(new byte[] {CLIENT_REQ}, ZMQ.SNDMORE);
        final CmwLightSerialiser serialiser = new CmwLightSerialiser(new FastByteBuffer(1024));
        serialiser.putHeaderInfo();
        serialiser.put(REQ_TYPE_TAG, RT_SUBSCRIBE);
        serialiser.put(ID_TAG, 1L); // todo: id
        serialiser.put(DEVICE_NAME_TAG, testdevice);
        serialiser.put(PROPERTY_NAME_TAG, testprop);
        serialiser.put(UPDATE_TYPE_TAG, UT_NORMAL);
        serialiser.put(SESSION_ID_TAG,"asdf"); // todo session id
        // StartMarker marks start of Data Object
        serialiser.putStartMarker(new WireDataFieldDescription(serialiser, serialiser.getParent(), -1,
                OPTIONS_TAG, DataType.START_MARKER, -1,-1,-1));
        serialiser.putStartMarker(new WireDataFieldDescription(serialiser, serialiser.getParent(), -1,
                "e", DataType.START_MARKER, -1, -1, -1));
        serialiser.getBuffer().flip();
        controlChannel.send(serialiser.getBuffer().elements(), 0, serialiser.getBuffer().limit(), ZMQ.SNDMORE);
        serialiser.getBuffer().reset();
        serialiser.putHeaderInfo();
        serialiser.put(SELECTOR_TAG, selector);
        if (filters != null && !filters.isEmpty()) {
            final WireDataFieldDescription filterFieldMarker = new WireDataFieldDescription(serialiser, serialiser.getParent(), -1,
                    FILTERS_TAG, DataType.START_MARKER, -1, -1, -1);
            serialiser.putStartMarker(filterFieldMarker);
            for (final Map.Entry<String, Object> entry : filters.entrySet()) {
                if (entry.getValue() instanceof String) {
                    serialiser.put(entry.getKey(), (String) entry.getValue());
                } else if (entry.getValue() instanceof Integer) {
                    serialiser.put(entry.getKey(), (Integer) entry.getValue());
                } else if (entry.getValue() instanceof Long) {
                    serialiser.put(entry.getKey(), (Long) entry.getValue());
                } else if (entry.getValue() instanceof Boolean) {
                    serialiser.put(entry.getKey(), (Boolean) entry.getValue());
                } else {
                    throw new CmwLightClient.RdaLightException("unsupported filter type: " + entry.getValue().getClass().getCanonicalName());
                }
            }
            serialiser.putEndMarker(filterFieldMarker);
        }
        // x: data
        serialiser.getBuffer().flip();
        controlChannel.send(serialiser.getBuffer().elements(), 0, serialiser.getBuffer().limit(), ZMQ.SNDMORE);
        controlChannel.send(new byte[] {MT_HEADER, MT_BODY_REQUEST_CONTEXT});
    }

    public void unsubscribe(final String testdevice, final String testprop, final String selector) {
        controlChannel.send(new byte[] {CLIENT_REQ}, ZMQ.SNDMORE);
        final CmwLightSerialiser serialiser = new CmwLightSerialiser(new FastByteBuffer(1024));
        serialiser.putHeaderInfo();
        serialiser.put(REQ_TYPE_TAG, RT_UNSUBSCRIBE);
        serialiser.put(ID_TAG, 1L); // todo: id
        serialiser.put(DEVICE_NAME_TAG, testdevice);
        serialiser.put(PROPERTY_NAME_TAG, testprop);
        serialiser.put(UPDATE_TYPE_TAG, UT_NORMAL);
        serialiser.put(SESSION_ID_TAG,"asdf"); // todo session id
        // StartMarker marks start of Data Object
        serialiser.putStartMarker(new WireDataFieldDescription(serialiser, serialiser.getParent(), -1,
                OPTIONS_TAG, DataType.START_MARKER, -1,-1,-1));
        serialiser.putStartMarker(new WireDataFieldDescription(serialiser, serialiser.getParent(), -1,
                "e", DataType.START_MARKER, -1, -1, -1));
        serialiser.getBuffer().flip();
        controlChannel.send(serialiser.getBuffer().elements(), 0, serialiser.getBuffer().limit(), ZMQ.SNDMORE);
        serialiser.getBuffer().reset();
        serialiser.putHeaderInfo();
        serialiser.put(SELECTOR_TAG, selector); // 8: Context c : filters, x: data
        serialiser.getBuffer().flip();
        controlChannel.send(serialiser.getBuffer().elements(), 0, serialiser.getBuffer().limit(), ZMQ.SNDMORE);
        controlChannel.send(new byte[] {MT_HEADER, MT_BODY_REQUEST_CONTEXT});
    }

    public void get(final String devNmae, final String prop, final String selector) {
        controlChannel.send(new byte[] {CLIENT_REQ}, ZMQ.SNDMORE);
        final CmwLightSerialiser serialiser = new CmwLightSerialiser(new FastByteBuffer(1024));
        serialiser.putHeaderInfo();
        serialiser.put(REQ_TYPE_TAG, RT_GET); // GET
        serialiser.put(ID_TAG, 1l);
        serialiser.put(DEVICE_NAME_TAG, devNmae);
        serialiser.put(PROPERTY_NAME_TAG, prop);
        serialiser.put(UPDATE_TYPE_TAG, UT_NORMAL);
        serialiser.put(SESSION_ID_TAG,"asdf");
        // StartMarker marks start of Data Object
        serialiser.putStartMarker(new WireDataFieldDescription(serialiser, serialiser.getParent(), -1,
                OPTIONS_TAG, DataType.START_MARKER, -1,-1,-1));
        serialiser.putStartMarker(new WireDataFieldDescription(serialiser, serialiser.getParent(), -1,
                "e", DataType.START_MARKER, -1, -1, -1));
        serialiser.getBuffer().flip();
        controlChannel.send(serialiser.getBuffer().elements(), 0, serialiser.getBuffer().limit(), ZMQ.SNDMORE);
        serialiser.getBuffer().reset();
        serialiser.putHeaderInfo();
        serialiser.put("8", selector); // 8: Context c : filters, x: data
        serialiser.getBuffer().flip();
        controlChannel.send(serialiser.getBuffer().elements(), 0, serialiser.getBuffer().limit(), ZMQ.SNDMORE);
        controlChannel.send(new byte[] {MT_HEADER, MT_BODY_REQUEST_CONTEXT});
    }

    public abstract static class Reply {
        public ZFrame header; // todo: replace by parsed header contents
        // description.printFieldStructure(); // 0=ID,1=DEVICE_NAME,2=REQ_TYPE,3=OPTIONS(a=NOTIFICATION_ID),7=UPDATE_TYPE,d=SESSION_ID,f=PROPERTY_NAME
    }

    public static class ExceptionReply extends Reply {
        public ZFrame exceptionBody; // todo: replace by parsed header contents
    }

    public static class DataReply extends Reply {
        public ZFrame dataBody; // todo: replace by deserialized contents
        public ZFrame dataContext; // todo: replace by parsed header contents
        // description.printFieldStructure(); // 4=CYCLE_NAME,5=ACQ_STAMP,6=CYCLE_STAMP,x=DATA(acqStamp, cycleName,cycleStamp,type,version))
    }

    public static class HeaderReply extends Reply {
    }

    public static class RdaLightException extends Exception {
        public RdaLightException(final String msg) {
            super(msg);
        }
    }

    public class SubscriptionUpdate extends Reply {
        public long id;
        public byte updateType;
        public long notificationId;
        public ZFrame contextData;
        public ZFrame bodyData;

        public SubscriptionUpdate(final long id, final byte updateType, final long notificationId, final ZFrame botdyData, final ZFrame contextData) {
            super();
            this.id = id;
            this.updateType = updateType;
            this.notificationId = notificationId;
            this.bodyData = botdyData;
            this.contextData = contextData;
        }
    }
}

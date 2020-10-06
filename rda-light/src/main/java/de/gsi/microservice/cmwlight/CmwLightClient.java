package de.gsi.microservice.cmwlight;

import de.gsi.serializer.DataType;
import de.gsi.serializer.spi.CmwLightSerialiser;
import de.gsi.serializer.spi.FastByteBuffer;
import de.gsi.serializer.spi.WireDataFieldDescription;
import org.zeromq.*;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class CmwLightClient {
    private static final AtomicInteger connectionId = new AtomicInteger(0); // global counter incremented for each connection
    private final AtomicInteger channelId = new AtomicInteger(0); // connection local counter incremented for each channel
    private final ZContext context = new ZContext();
    private final ZMQ.Socket controlChannel;
    private final AtomicReference<ConnectionState> connectionState = new AtomicReference<>(ConnectionState.DISCONNECTED);
    private final String address;

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
             //System.out.println("Heartbeat received");
             // TODO: reset Heartbeat
             return null;
         }
         if (!Arrays.equals(firstFrame.getData(), new byte[]{SERVER_REP})) {
             throw new RdaLightException("Expecting only messages of type Heartbeat or Reply but got: " + data);
         }
         Reply result;
         final byte[]
                 descriptor = data.pollLast().getData();
         if (Arrays.equals(descriptor, new byte[] {MT_HEADER})) {
             result = new HeaderReply(); // this should probably just set some subscription status
         } else if (Arrays.equals(descriptor , new byte[] {MT_HEADER, MT_BODY_EXCEPTION})) {
                 result = new ExceptionReply();
         } else if (!Arrays.equals(descriptor, new byte[] {MT_HEADER, MT_BODY, MT_BODY_DATA_CONTEXT})) {
             throw new RdaLightException("expected reply message, but got: "+ Arrays.toString(descriptor) +  " - " + Arrays.toString(data.getLast().getData()));
         } else {
             result = new DataReply();
         }
         for (final byte desc : descriptor) {
             switch (desc) {
                 case MT_HEADER:
                     //System.out.println("header: "+ new String(data.pollFirst().getData()));
                     result.header = data.pollFirst();
                     break;
                 case MT_BODY:
                     ((DataReply) result).dataBody = data.pollFirst();
                     break;
                 case MT_BODY_DATA_CONTEXT:
                     //System.out.println("body data context: "+ new String(data.pollFirst().getData()));
                     ((DataReply) result).dataContext= data.pollFirst();
                     break;
                 case MT_BODY_REQUEST_CONTEXT:
                     throw new RdaLightException("Unexpected body request context frame"); // we should never receive this
                 case MT_BODY_EXCEPTION:
                     ((ExceptionReply) result).exceptionBody = data.pollFirst();
                     break;
                 default:
                     throw new RdaLightException("invalid message type (" + desc + "): "+ new String(data.pollFirst().getData()));
             }
         }
         return result;
    }

    public void sendHeartBeat() {
        controlChannel.send(new byte[] {CLIENT_HB});
    }

    public void subscribe(final String testdevice, final String testprop, final String selector) {
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
        serialiser.put(SELECTOR_TAG, selector); // 8: Context c : filters, x: data
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
    }

    public static class ExceptionReply extends Reply {
        public ZFrame exceptionBody; // todo: replace by parsed header contents
    }

    public static class DataReply extends Reply {
        public ZFrame dataBody; // todo: replace by deserialized contents
        public ZFrame dataContext; // todo: replace by parsed header contents
    }

    public static class HeaderReply extends Reply {
    }

    public static class RdaLightException extends Exception {
        public RdaLightException(final String msg) {
            super(msg);
        }
    }
}

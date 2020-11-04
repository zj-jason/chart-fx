package de.gsi.microservice.concepts.cmwlight;

import de.gsi.dataset.utils.AssertUtils;
import de.gsi.serializer.*;
import de.gsi.serializer.spi.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.*;

import java.util.Arrays;
import java.util.Map;

/**
 * A lightweight implementation of the CMW RDA client protocol part.
 */
@SuppressWarnings("PMD.UnusedLocalVariable") // Unused variables are taken from the protocol and should be available for reference
public class CmwLightProtocol {
    private static final Logger LOGGER = LoggerFactory.getLogger(CmwLightProtocol.class);
    private static final int MAX_MSG_SIZE = 512;
    private static final IoBuffer outBuffer = new FastByteBuffer(MAX_MSG_SIZE);
    private static final CmwLightSerialiser serialiser = new CmwLightSerialiser(outBuffer);
    private static final IoClassSerialiser classSerialiser = new IoClassSerialiser(new FastByteBuffer(0));

    // Contents of the first frame determine the type of the message
    private static final byte SERVER_CONNECT_ACK = (byte) 0x01;
    private static final byte SERVER_REP = (byte) 0x02;
    private static final byte SERVER_HB = (byte) 0x03;
    private static final byte CLIENT_CONNECT = (byte) 0x20;
    private static final byte CLIENT_REQ = (byte) 0x21;
    private static final byte CLIENT_HB = (byte) 0x22;

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
    public static final byte UT_IMMEDIATE_UPDATE = (byte) 2; // Update sent after the value has been modified by a set call.

    public static ZMsg connectReq() {
        final ZMsg result = new ZMsg();
        result.add(new byte[]{CLIENT_CONNECT});
        result.add("1.0.0".getBytes());
        return result;
    }

    public static Reply parseMsg(final ZMsg data) throws RdaLightException {
        AssertUtils.notNull("data", data);
        final ZFrame firstFrame = data.pollFirst();
        if (firstFrame != null && Arrays.equals(firstFrame.getData(), new byte[] { SERVER_CONNECT_ACK })) {
            return ConnectAckReply.CON_ACK_REPLY;
        }
        if (firstFrame != null && Arrays.equals(firstFrame.getData(), new byte[] { SERVER_HB })) {
            return ServerHeartbeatReply.SERVER_HB_REPLY;
        }
        byte[] descriptor = checkDescriptor(data.pollLast(), firstFrame);
        final byte[] header = checkHeader(data.poll());
        classSerialiser.setDataBuffer(FastByteBuffer.wrap(header));
        final FieldDescription headerMap;
        byte reqType = -1;
        long id = -1;
        String deviceName;
        WireDataFieldDescription options = null;
        byte updateType = -1;
        String sessionId;
        String propName;

        try {
            headerMap = classSerialiser.parseWireFormat().getChildren().get(0);
            for (FieldDescription field : headerMap.getChildren()) {
                if (field.getFieldName().equals(REQ_TYPE_TAG) && field.getType() == byte.class) {
                    reqType = (byte) ((WireDataFieldDescription) field).data();
                } else if (field.getFieldName().equals(ID_TAG) && field.getType() == long.class) {
                    id = (long) ((WireDataFieldDescription) field).data();
                } else if (field.getFieldName().equals(DEVICE_NAME_TAG) && field.getType() == String.class) {
                    deviceName = (String) ((WireDataFieldDescription) field).data();
                    assert deviceName.equals(""); // this field is not used
                } else if (field.getFieldName().equals(OPTIONS_TAG)) {
                    options = (WireDataFieldDescription) field;
                } else if (field.getFieldName().equals(UPDATE_TYPE_TAG) && field.getType() == byte.class) {
                    updateType = (byte) ((WireDataFieldDescription) field).data();
                } else if (field.getFieldName().equals(SESSION_ID_TAG) && field.getType() == String.class) {
                    sessionId = (String) ((WireDataFieldDescription) field).data();
                    assert sessionId.equals(""); // this field is not used
                } else if (field.getFieldName().equals(PROPERTY_NAME_TAG) && field.getType() == String.class) {
                    propName = (String) ((WireDataFieldDescription) field).data();
                    assert propName.equals(""); // this field is not used
                } else {
                    throw new RdaLightException("Unknown CMW header field: " + field.getFieldName());
                }
            }
        } catch (IllegalStateException e) {
            throw new RdaLightException("unparsable header: " + Arrays.toString(header) + "(" + new String(header) + ")", e);
        }
        switch (reqType) {
            case RT_REPLY: // answer to get request
                LOGGER.atDebug().log("received reply");
                if (descriptor.length != 3 || descriptor[1] != MT_BODY || descriptor[2] != MT_BODY_DATA_CONTEXT) {
                    throw new RdaLightException("Notification update does not contain the proper data");
                }
                return createDataReply(id, updateType, data.pollFirst(), data.pollFirst());
            case RT_NOTIFICATION_DATA: // notification update
                if (descriptor.length != 3 || descriptor[1] != MT_BODY || descriptor[2] != MT_BODY_DATA_CONTEXT) {
                    throw new RdaLightException("Notification update does not contain the proper data");
                }
                long notificationId = -1;
                if (options != null) {
                    final FieldDescription notificationIdField = options.findChildField(NOTIFICATION_ID_TAG); //long
                    notificationId = (long) ((WireDataFieldDescription) notificationIdField).data();
                }
                return createSubscriptionUpdateReply(id, updateType, notificationId, data.pollFirst(), data.pollFirst());
            case RT_EXCEPTION: // exception on get/set request
            case RT_NOTIFICATION_EXC: // exception on notification, e.g null pointer in server notify code
            case RT_SUBSCRIBE_EXCEPTION: // exception on subscribe e.g. nonexistent property, wrong filters
                if (descriptor.length != 2 || descriptor[1] != MT_BODY_EXCEPTION) {
                    throw new RdaLightException("Exception does not contain the proper data");
                }
                return createSubscriptionExceptionReply(reqType, id, data.pollFirst());
            case RT_SUBSCRIBE: // seems to be sent after subscription is accepted
                LOGGER.atDebug().log("received subscription reply: " + Arrays.toString(header) + "(" + new String(header) + ")");
                return new SubscribeAckReply();
            case RT_SESSION_CONFIRM:
                LOGGER.atDebug().log("received session confirm");
                // update session state
                return new SessionConfirmReply();
            case RT_EVENT:
                LOGGER.atDebug().log("received event");
                return new EventReply();
            case RT_CONNECT:
            case RT_SET:
            default:
                throw new RdaLightException("received unknown or non-client request type: " + reqType);
        }
    }

    private static byte[] checkHeader(final ZFrame headerMsg) throws RdaLightException {
        if (headerMsg == null) {
            throw new RdaLightException("Message does not contain header");
        }
        return headerMsg.getData();
    }

    private static byte[] checkDescriptor(final ZFrame descriptorMsg, final ZFrame firstFrame) throws RdaLightException {
        if (firstFrame == null || !Arrays.equals(firstFrame.getData(), new byte[] { SERVER_REP })) {
            throw new RdaLightException("Expecting only messages of type Heartbeat or Reply but got: " + firstFrame);
        }
        if (descriptorMsg == null) {
            throw new RdaLightException("Message does not contain descriptor");
        }
        final byte[] descriptor = descriptorMsg.getData();
        if (descriptor[0] != MT_HEADER) {
            throw new RdaLightException("First message of SERVER_REP has to be of type MT_HEADER but is: " + descriptor[0]);
        }
        return descriptor;
    }

    private static Reply createDataReply(final long id, final byte updateType, final ZFrame bodyData, final ZFrame contextData) throws RdaLightException {
        if (bodyData == null || contextData == null) {
            throw new RdaLightException("malformed get reply");
        }
        final GetReply reply = new GetReply();
        reply.id = id;
        reply.updateType = updateType;
        reply.bodyData = bodyData;

        classSerialiser.setDataBuffer(FastByteBuffer.wrap(contextData.getData()));
        final FieldDescription contextMap;
        try {
            contextMap = classSerialiser.parseWireFormat().getChildren().get(0);
        } catch (IllegalStateException e) {
            final byte[] contextBytes = classSerialiser.getDataBuffer().elements();
            throw new RdaLightException("unparsable header: " + Arrays.toString(contextBytes) + "(" + new String(contextBytes) + ")");
        }
        for (FieldDescription field : contextMap.getChildren()) {
            if (field.getFieldName().equals(CYCLE_NAME_TAG) && field.getType() == String.class) {
                reply.cycleName = (String) ((WireDataFieldDescription) field).data();
            } else if (field.getFieldName().equals(ACQ_STAMP_TAG) && field.getType() == long.class) {
                reply.acqStamp = (long) ((WireDataFieldDescription) field).data();
            } else if (field.getFieldName().equals(CYCLE_STAMP_TAG) && field.getType() == long.class) {
                reply.cycleStamp = (long) ((WireDataFieldDescription) field).data();
            } else if (field.getFieldName().equals(DATA_TAG)) {
                for (FieldDescription dataField : field.getChildren()) {
                    if (dataField.getFieldName().equals("acqStamp") && dataField.getType() == long.class) {
                        reply.acqStamp2 = (long) ((WireDataFieldDescription) dataField).data();
                    } else if (dataField.getFieldName().equals("cycleName") && dataField.getType() == String.class) {
                        reply.cycleName2 = (String) ((WireDataFieldDescription) dataField).data();
                    } else if (dataField.getFieldName().equals("cycleStamp") && dataField.getType() == long.class) {
                        reply.cycleStamp2 = (long) ((WireDataFieldDescription) dataField).data();
                    } else if (dataField.getFieldName().equals("type") && dataField.getType() == int.class) {
                        reply.type = (int) ((WireDataFieldDescription) dataField).data();
                    } else if (dataField.getFieldName().equals("version") && dataField.getType() == int.class) {
                        reply.version = (int) ((WireDataFieldDescription) dataField).data();
                    } else {
                        throw new UnsupportedOperationException("Unknown data field: " + field.getFieldName());
                    }
                }
            } else {
                throw new UnsupportedOperationException("Unknown field: " + field.getFieldName());
            }
        }
        assert reply.acqStamp == reply.acqStamp2;
        assert reply.cycleName.equals(reply.cycleName2);
        assert reply.cycleStamp == reply.cycleStamp2;
        return reply;
    }

    private static Reply createSubscriptionExceptionReply(final byte requestType, final long id, final ZFrame exceptionBody) throws RdaLightException {
        if (exceptionBody == null) {
            throw new RdaLightException("malformed subscription exception");
        }
        final ExceptionReply reply = new ExceptionReply();
        reply.id = id;
        reply.requestType = requestType;
        classSerialiser.setDataBuffer(FastByteBuffer.wrap(exceptionBody.getData()));
        final FieldDescription exceptionFields = classSerialiser.parseWireFormat().getChildren().get(0);
        for (FieldDescription field : exceptionFields.getChildren()) {
            if (field.getFieldName().equals("ContextAcqStamp") && field.getType() == long.class) {
                reply.contextAcqStamp = (long) ((WireDataFieldDescription) field).data();
            } else if (field.getFieldName().equals("ContextCycleStamp") && field.getType() == long.class) {
                reply.contextCycleStamp = (long) ((WireDataFieldDescription) field).data();
            } else if (field.getFieldName().equals("Message") && field.getType() == String.class) {
                reply.message = (String) ((WireDataFieldDescription) field).data();
            } else if (field.getFieldName().equals("Type") && field.getType() == byte.class) {
                reply.type = (byte) ((WireDataFieldDescription) field).data();
            } else {
                throw new RdaLightException("Unsupported field in exception body: " + field.getFieldName());
            }
        }
        return reply;
    }

    private static Reply createSubscriptionUpdateReply(final long id, final byte updateType, final long notificationId, final ZFrame bodyData, final ZFrame contextData) throws RdaLightException {
        if (bodyData == null || contextData == null) {
            throw new RdaLightException("malformed subscription update");
        }
        final SubscriptionUpdate reply = new SubscriptionUpdate();
        reply.id = id;
        reply.updateType = updateType;
        reply.notificationId = notificationId;
        reply.bodyData = bodyData;

        classSerialiser.setDataBuffer(FastByteBuffer.wrap(contextData.getData()));
        final FieldDescription contextMap;
        try {
            contextMap = classSerialiser.parseWireFormat().getChildren().get(0);
        } catch (IllegalStateException e) {
            final byte[] contextBytes = classSerialiser.getDataBuffer().elements();
            throw new RdaLightException("unparsable header: " + Arrays.toString(contextBytes) + "(" + new String(contextBytes) + ")");
        }
        for (FieldDescription field : contextMap.getChildren()) {
            if (field.getFieldName().equals(CYCLE_NAME_TAG) && field.getType() == String.class) {
                reply.cycleName = (String) ((WireDataFieldDescription) field).data();
            } else if (field.getFieldName().equals(ACQ_STAMP_TAG) && field.getType() == long.class) {
                reply.acqStamp = (long) ((WireDataFieldDescription) field).data();
            } else if (field.getFieldName().equals(CYCLE_STAMP_TAG) && field.getType() == long.class) {
                reply.cycleStamp = (long) ((WireDataFieldDescription) field).data();
            } else if (field.getFieldName().equals(DATA_TAG)) {
                for (FieldDescription dataField : field.getChildren()) {
                    if (dataField.getFieldName().equals("acqStamp") && dataField.getType() == long.class) {
                        reply.acqStamp2 = (long) ((WireDataFieldDescription) dataField).data();
                    } else if (dataField.getFieldName().equals("cycleName") && dataField.getType() == String.class) {
                        reply.cycleName2 = (String) ((WireDataFieldDescription) dataField).data();
                    } else if (dataField.getFieldName().equals("cycleStamp") && dataField.getType() == long.class) {
                        reply.cycleStamp2 = (long) ((WireDataFieldDescription) dataField).data();
                    } else if (dataField.getFieldName().equals("type") && dataField.getType() == int.class) {
                        reply.type = (int) ((WireDataFieldDescription) dataField).data();
                    } else if (dataField.getFieldName().equals("version") && dataField.getType() == int.class) {
                        reply.version = (int) ((WireDataFieldDescription) dataField).data();
                    } else {
                        throw new UnsupportedOperationException("Unknown data field: " + field.getFieldName());
                    }
                }
            } else {
                throw new UnsupportedOperationException("Unknown field: " + field.getFieldName());
            }
        }
        assert reply.acqStamp == reply.acqStamp2;
        assert reply.cycleName.equals(reply.cycleName2);
        assert reply.cycleStamp == reply.cycleStamp2;
        return reply;
    }

    public static ZMsg subsReq(final String sessionId, final long id, final String device, final String property, final String selector, final Map<String, Object> filters) throws RdaLightException {
        final ZMsg result = new ZMsg();
        result.add(new byte[] {CLIENT_REQ});
        outBuffer.reset();
        serialiser.setBuffer(outBuffer);
        serialiser.putHeaderInfo();
        serialiser.put(REQ_TYPE_TAG, RT_SUBSCRIBE);
        serialiser.put(ID_TAG, id);
        serialiser.put(DEVICE_NAME_TAG, device);
        serialiser.put(PROPERTY_NAME_TAG, property);
        serialiser.put(UPDATE_TYPE_TAG, UT_NORMAL);
        serialiser.put(SESSION_ID_TAG, sessionId);
        // StartMarker marks start of Data Object
        serialiser.putStartMarker(new WireDataFieldDescription(serialiser, serialiser.getParent(), -1,
                OPTIONS_TAG, DataType.START_MARKER, -1, -1, -1));
        serialiser.putStartMarker(new WireDataFieldDescription(serialiser, serialiser.getParent(), -1,
                "e", DataType.START_MARKER, -1, -1, -1));
        outBuffer.flip();
        result.add(Arrays.copyOfRange(outBuffer.elements(), 0, outBuffer.limit()));
        outBuffer.reset();
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
                    throw new RdaLightException("unsupported filter type: " + entry.getValue().getClass().getCanonicalName());
                }
            }
            serialiser.putEndMarker(filterFieldMarker);
        }
        // x: data
        outBuffer.flip();
        result.add(Arrays.copyOfRange(outBuffer.elements(), 0, outBuffer.limit()));
        outBuffer.reset();
        result.add(new byte[] { MT_HEADER, MT_BODY_REQUEST_CONTEXT });
        return result;
    }

    public static ZMsg unsubReq(final String sessionId, final long id, final String device, final String property, final String selector, final Map<String, Object> filters) throws RdaLightException {
        final ZMsg result = new ZMsg();
        result.add(new byte[] {CLIENT_REQ});
        outBuffer.reset();
        serialiser.setBuffer(outBuffer);
        serialiser.putHeaderInfo();
        serialiser.put(REQ_TYPE_TAG, RT_UNSUBSCRIBE);
        serialiser.put(ID_TAG, id);
        serialiser.put(DEVICE_NAME_TAG, device);
        serialiser.put(PROPERTY_NAME_TAG, property);
        serialiser.put(UPDATE_TYPE_TAG, UT_NORMAL);
        serialiser.put(SESSION_ID_TAG, sessionId);
        // StartMarker marks start of Data Object
        serialiser.putStartMarker(new WireDataFieldDescription(serialiser, serialiser.getParent(), -1,
                OPTIONS_TAG, DataType.START_MARKER, -1, -1, -1));
        serialiser.putStartMarker(new WireDataFieldDescription(serialiser, serialiser.getParent(), -1,
                "e", DataType.START_MARKER, -1, -1, -1));
        outBuffer.flip();
        result.add(Arrays.copyOfRange(outBuffer.elements(), 0, outBuffer.limit()));
        outBuffer.reset();
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
                    throw new RdaLightException("unsupported filter type: " + entry.getValue().getClass().getCanonicalName());
                }
            }
            serialiser.putEndMarker(filterFieldMarker);
        }
        // x: data
        outBuffer.flip();
        result.add(Arrays.copyOfRange(outBuffer.elements(), 0, outBuffer.limit()));
        outBuffer.reset();
        result.add(new byte[] { MT_HEADER, MT_BODY_REQUEST_CONTEXT });
        return result;
    }

    public static ZMsg getReq(final String sessionId, final long id, final String devName, final String prop, final String selector) {
        final ZMsg result = new ZMsg();
        result.add(new byte[] {CLIENT_REQ});
        // Header
        outBuffer.reset();
        serialiser.setBuffer(outBuffer);
        final CmwLightSerialiser serialiser = new CmwLightSerialiser(new FastByteBuffer(1024));
        serialiser.putHeaderInfo();
        serialiser.put(REQ_TYPE_TAG, RT_GET); // GET
        serialiser.put(ID_TAG, id);
        serialiser.put(DEVICE_NAME_TAG, devName);
        serialiser.put(PROPERTY_NAME_TAG, prop);
        serialiser.put(UPDATE_TYPE_TAG, UT_NORMAL);
        serialiser.put(SESSION_ID_TAG, sessionId);
        // StartMarker marks start of Data Object
        final WireDataFieldDescription optionsFiledDesc = new WireDataFieldDescription(serialiser, serialiser.getParent(), -1,
                OPTIONS_TAG, DataType.START_MARKER, -1, -1, -1);
        serialiser.putStartMarker(optionsFiledDesc);
        final WireDataFieldDescription sessionBodyFieldDesc = new WireDataFieldDescription(serialiser, serialiser.getParent(), -1,
                SESSION_BODY_TAG, DataType.START_MARKER, -1, -1, -1);
        serialiser.putStartMarker(sessionBodyFieldDesc);
        serialiser.putEndMarker(sessionBodyFieldDesc);
        serialiser.putEndMarker(optionsFiledDesc);
        outBuffer.flip();
        result.add(Arrays.copyOfRange(outBuffer.elements(), 0, outBuffer.limit()));
        // Request Context
        outBuffer.reset();
        serialiser.putHeaderInfo();
        serialiser.put("8", selector); // 8: Context c : filters, x: data
        outBuffer.flip();
        result.add(Arrays.copyOfRange(outBuffer.elements(), 0, outBuffer.limit()));
        outBuffer.reset();
        // descriptor
        result.add(new byte[] { MT_HEADER, MT_BODY_REQUEST_CONTEXT });
        return result;
    }

    public static ZMsg hbReq() {
        final ZMsg result = new ZMsg();
        result.add(new byte[] { CLIENT_HB });
        return result;
    }

    public abstract static class Reply {
    }

    public static class ConnectAckReply extends Reply {
        public static final ConnectAckReply CON_ACK_REPLY = new ConnectAckReply();
    }

    public static class ServerHeartbeatReply extends Reply {
        public static final ServerHeartbeatReply SERVER_HB_REPLY = new ServerHeartbeatReply();
    }

    public static class SubscribeAckReply extends Reply {
    }

    public static class SessionConfirmReply extends Reply {
    }

    public static class EventReply extends Reply {
    }

    public static class ExceptionReply extends Reply {
        public long contextAcqStamp;
        public long contextCycleStamp;
        public String message;
        public byte type;
        public long id;
        public byte requestType;

        @Override
        public String toString() {
            return "SubscriptionExceptionReply: " + message;
        }
    }

    public static class GetReply extends Reply {
        public String cycleName;
        public String cycleName2;
        public long cycleStamp;
        public long cycleStamp2;
        public long acqStamp;
        public long acqStamp2;
        public int type;
        public int version;
        public long id;
        public byte updateType;
        public ZFrame bodyData;
    }

    public static class SubscriptionUpdate extends Reply {
        public String cycleName;
        public String cycleName2;
        public long cycleStamp;
        public long cycleStamp2;
        public long acqStamp;
        public long acqStamp2;
        public int type;
        public int version;
        public long id;
        public byte updateType;
        public long notificationId;
        public ZFrame bodyData;
    }

    public static class RdaLightException extends Exception {
        public RdaLightException(final String msg) {
            super(msg);
        }

        public RdaLightException(final String msg, final Throwable e) {
            super(msg, e);
        }
    }
}

package de.gsi.microservice.concepts.cmwlight;

import de.gsi.dataset.utils.AssertUtils;
import de.gsi.serializer.*;
import de.gsi.serializer.spi.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.*;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashMap;
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

    /**
     * The message specified by the byte contained in the first frame of a message defines what type of message is present
     */
    public enum MessageType {
        SERVER_CONNECT_ACK(0x01), SERVER_REP(0x02), SERVER_HB(0x03), CLIENT_CONNECT(0x20),
        CLIENT_REQ(0x21), CLIENT_HB(0x22);

        private final byte value;

        MessageType(int value) {
            this.value = (byte) value;
        }

        public byte value() {
            return value;
        }
    }

    /**
     * Frame Types in the descriptor (Last frame of a message containing the type of each sub message)
     */
    public enum FrameType {
        HEADER(0), BODY(1), BODY_DATA_CONTEXT(2), BODY_REQUEST_CONTEXT(3), BODY_EXCEPTION(4);

        private final byte value;

        FrameType(int value) {
            this.value = (byte) value;
        }

        public byte value() {
            return value;
        }
    }

    /**
     * Field names for the Request Header
     */
    public enum FieldName{
        EVENT_TYPE_TAG("eventType"), MESSAGE_TAG("message"), ID_TAG("0"), DEVICE_NAME_TAG("1"), REQ_TYPE_TAG("2"),
        OPTIONS_TAG("3"), CYCLE_NAME_TAG("4"), ACQ_STAMP_TAG("5"), CYCLE_STAMP_TAG("6"), UPDATE_TYPE_TAG("7"),
        SELECTOR_TAG("8"), CLIENT_INFO_TAG("9"), NOTIFICATION_ID_TAG("a"), SOURCE_ID_TAG("b"), FILTERS_TAG("c"),
        DATA_TAG("x"), SESSION_ID_TAG("d"), SESSION_BODY_TAG("e"), PROPERTY_NAME_TAG("f");

        private final String name;

        FieldName(String name) {
            this.name = name;
        }

        public String value() {
            return name;
        }
    }

    /**
     * request type used in request header REQ_TYPE_TAG
     */
    public enum RequestType {
        GET(0), SET(1), CONNECT(2), REPLY(3), EXCEPTION(4), SUBSCRIBE(5), UNSUBSCRIBE(6), NOTIFICATION_DATA(7),
        NOTIFICATION_EXC(8), SUBSCRIBE_EXCEPTION(9), EVENT(10), SESSION_CONFIRM(11);

        private final byte value;

        RequestType(int value) {
            this.value = (byte) value;
        }

        public static RequestType of(int value) {
            return values()[value];
        }

        public byte value() {
            return value;
        }
    }

    /**
     * UpdateType
     */
    public enum UpdateType {
        NORMAL(0),
        FIRST_UPDATE(1), // Initial update sent when the subscription is created.
        IMMEDIATE_UPDATE(2); // Update sent after the value has been modified by a set call.

        private final byte value;

        UpdateType(int value) {
            this.value = (byte) value;
        }

        public static UpdateType of(int value) {
            return values()[value];
        }

        public byte value() {
            return value;
        }
    }

    public static Reply parseMsg(final ZMsg data) throws RdaLightException {
        AssertUtils.notNull("data", data);
        final ZFrame firstFrame = data.pollFirst();
        if (firstFrame != null && Arrays.equals(firstFrame.getData(), new byte[] { MessageType.SERVER_CONNECT_ACK.value()})) {
            final Reply reply = new Reply(MessageType.SERVER_CONNECT_ACK);
            final ZFrame versionData = data.pollFirst();
            AssertUtils.notNull("version data in connection acknowledgement frame", versionData);
            reply.version = versionData.getString(Charset.defaultCharset());
            return reply;
        }
        if (firstFrame != null && Arrays.equals(firstFrame.getData(), new byte[] { MessageType.SERVER_HB.value()})) {
            return Reply.SERVER_HB;
        }
        byte[] descriptor = checkDescriptor(data.pollLast(), firstFrame);

        Reply reply = getReplyFromHeader(checkHeader(data.poll()));
        switch (reply.requestType) {
            case REPLY:
                assertDescriptor(descriptor, FrameType.HEADER, FrameType.BODY, FrameType.BODY_DATA_CONTEXT);
                reply.bodyData = data.pollFirst();
                reply.dataContext = getContextData(data.pollFirst());
                return reply;
            case NOTIFICATION_DATA: // notification update
                assertDescriptor(descriptor, FrameType.HEADER, FrameType.BODY, FrameType.BODY_DATA_CONTEXT);
                reply.notificationId = (long) reply.options.get(FieldName.NOTIFICATION_ID_TAG.name());
                reply.bodyData = data.pollFirst();
                reply.dataContext = getContextData(data.pollFirst());
                return reply;
            case EXCEPTION: // exception on get/set request
            case NOTIFICATION_EXC: // exception on notification, e.g null pointer in server notify code
            case SUBSCRIBE_EXCEPTION: // exception on subscribe e.g. nonexistent property, wrong filters
                assertDescriptor(descriptor, FrameType.HEADER, FrameType.BODY_EXCEPTION);
                reply.exceptionMessage = getExceptionMessage(data.pollFirst());
                return reply;
            case SUBSCRIBE:  // descriptor: [0] options: SOURCE_ID_TAG // seems to be sent after subscription is accepted
                assertDescriptor(descriptor, FrameType.HEADER);
                reply.souceId = (long) reply.options.get(FieldName.SOURCE_ID_TAG.name());
                return reply;
            case SESSION_CONFIRM: // descriptor: [0] options: SESSION_BODY_TAG
                assertDescriptor(descriptor, FrameType.HEADER);
                reply.sessionBody = (String) reply.options.get(FieldName.SESSION_BODY_TAG.name());
                return reply;
            case EVENT:
                assertDescriptor(descriptor, FrameType.HEADER);
                return reply;
            case CONNECT:
            case SET:
            case GET:
            case UNSUBSCRIBE:
            default:
                 throw new RdaLightException("received unknown or non-client request type: " + reply.requestType);
        }
    }

    private static Reply getReplyFromHeader(final ZFrame header) throws RdaLightException {
        Reply reply = new Reply();
        classSerialiser.setDataBuffer(FastByteBuffer.wrap(header.getData()));
        final FieldDescription headerMap;
        try {
            headerMap = classSerialiser.parseWireFormat().getChildren().get(0);
            for (FieldDescription field : headerMap.getChildren()) {
                if (field.getFieldName().equals(FieldName.REQ_TYPE_TAG.value()) && field.getType() == byte.class) {
                    reply.requestType = RequestType.of((int) ((WireDataFieldDescription) field).data());
                } else if (field.getFieldName().equals(FieldName.ID_TAG.value()) && field.getType() == long.class) {
                    reply.id = (long) ((WireDataFieldDescription) field).data();
                } else if (field.getFieldName().equals(FieldName.DEVICE_NAME_TAG.value()) && field.getType() == String.class) {
                    reply.deviceName = (String) ((WireDataFieldDescription) field).data();
                } else if (field.getFieldName().equals(FieldName.OPTIONS_TAG.value())) {
                    for (FieldDescription dataField : field.getChildren()) {
                        if (reply.options == null) {
                            reply.options = new HashMap<>();
                        }
                        reply.options.put(dataField.getFieldName(), ((WireDataFieldDescription) dataField).data());
                    }

                } else if (field.getFieldName().equals(FieldName.UPDATE_TYPE_TAG.value()) && field.getType() == byte.class) {
                    reply.updateType = UpdateType.of((int) ((WireDataFieldDescription) field).data());
                } else if (field.getFieldName().equals(FieldName.SESSION_ID_TAG.value()) && field.getType() == String.class) {
                    reply.sessionId = (String) ((WireDataFieldDescription) field).data();
                } else if (field.getFieldName().equals(FieldName.PROPERTY_NAME_TAG.value()) && field.getType() == String.class) {
                    reply.propertyName = (String) ((WireDataFieldDescription) field).data();
                } else {
                    throw new RdaLightException("Unknown CMW header field: " + field.getFieldName());
                }
            }
        } catch (IllegalStateException e) {
            throw new RdaLightException("unparsable header: " + Arrays.toString(header.getData()) + "(" + header.toString() + ")", e);
        }
        if (reply.requestType == null) {
            throw new RdaLightException("Header does not contain request type field");
        }
        return reply;
    }

    private static ExceptionMessage getExceptionMessage(final ZFrame exceptionBody) throws RdaLightException {
        if (exceptionBody == null) {
            throw new RdaLightException("malformed subscription exception");
        }
        final ExceptionMessage exceptionMessage = new ExceptionMessage();
        classSerialiser.setDataBuffer(FastByteBuffer.wrap(exceptionBody.getData()));
        final FieldDescription exceptionFields = classSerialiser.parseWireFormat().getChildren().get(0);
        for (FieldDescription field : exceptionFields.getChildren()) {
            if (field.getFieldName().equals("ContextAcqStamp") && field.getType() == long.class) {
                exceptionMessage.contextAcqStamp = (long) ((WireDataFieldDescription) field).data();
            } else if (field.getFieldName().equals("ContextCycleStamp") && field.getType() == long.class) {
                exceptionMessage.contextCycleStamp = (long) ((WireDataFieldDescription) field).data();
            } else if (field.getFieldName().equals("Message") && field.getType() == String.class) {
                exceptionMessage.message = (String) ((WireDataFieldDescription) field).data();
            } else if (field.getFieldName().equals("Type") && field.getType() == byte.class) {
                exceptionMessage.type = (byte) ((WireDataFieldDescription) field).data();
            } else {
                throw new RdaLightException("Unsupported field in exception body: " + field.getFieldName());
            }
        }
        return exceptionMessage;
    }

    private static DataContext getContextData(final ZFrame contextData) throws RdaLightException {
        AssertUtils.notNull("contextData", contextData);
        DataContext dataContext = new DataContext();
        classSerialiser.setDataBuffer(FastByteBuffer.wrap(contextData.getData()));
        final FieldDescription contextMap;
        try {
            contextMap = classSerialiser.parseWireFormat().getChildren().get(0);
            for (FieldDescription field : contextMap.getChildren()) {
                if (field.getFieldName().equals(FieldName.CYCLE_NAME_TAG.name()) && field.getType() == String.class) {
                    dataContext.cycleName = (String) ((WireDataFieldDescription) field).data();
                } else if (field.getFieldName().equals(FieldName.ACQ_STAMP_TAG.name()) && field.getType() == long.class) {
                    dataContext.acqStamp = (long) ((WireDataFieldDescription) field).data();
                } else if (field.getFieldName().equals(FieldName.CYCLE_STAMP_TAG.name()) && field.getType() == long.class) {
                    dataContext.cycleStamp = (long) ((WireDataFieldDescription) field).data();
                } else if (field.getFieldName().equals(FieldName.DATA_TAG.name())) {
                    for (FieldDescription dataField : field.getChildren()) {
                        if (dataContext.data == null) {
                            dataContext.data = new HashMap<>();
                        }
                        dataContext.data.put(dataField.getFieldName(), ((WireDataFieldDescription) dataField).data());
                    }
                } else {
                    throw new UnsupportedOperationException("Unknown field: " + field.getFieldName());
                }
            }
        } catch (IllegalStateException e) {
            throw new RdaLightException("unparsable context data: " + Arrays.toString(contextData.getData()) + "(" + new String(contextData.getData()) + ")");
        }
        return dataContext;
    }

    private static void assertDescriptor(final byte[] descriptor, final FrameType ... frameTypes) throws RdaLightException {
        if (descriptor.length != frameTypes.length) {
            throw new RdaLightException("descriptor does not match message type: \n  " + Arrays.toString(descriptor) + "\n  " + Arrays.toString(frameTypes));
        }
        for (int i = 1; i < descriptor.length; i++) {
            if (descriptor[i] != frameTypes[i].value()){
                throw new RdaLightException("descriptor does not match message type: \n  " + Arrays.toString(descriptor) + "\n  " + Arrays.toString(frameTypes));
            }
        }
    }

    private static ZFrame checkHeader(final ZFrame headerMsg) throws RdaLightException {
        if (headerMsg == null) {
            throw new RdaLightException("Message does not contain header");
        }
        return headerMsg;
    }

    private static byte[] checkDescriptor(final ZFrame descriptorMsg, final ZFrame firstFrame) throws RdaLightException {
        if (firstFrame == null || !Arrays.equals(firstFrame.getData(), new byte[] { MessageType.SERVER_REP.value() })) {
            throw new RdaLightException("Expecting only messages of type Heartbeat or Reply but got: " + firstFrame);
        }
        if (descriptorMsg == null) {
            throw new RdaLightException("Message does not contain descriptor");
        }
        final byte[] descriptor = descriptorMsg.getData();
        if (descriptor[0] != FrameType.HEADER.value()) {
            throw new RdaLightException("First message of SERVER_REP has to be of type MT_HEADER but is: " + descriptor[0]);
        }
        return descriptor;
    }

    public static ZMsg connectReq() {
        final ZMsg result = new ZMsg();
        result.add(new byte[]{MessageType.CLIENT_CONNECT.value()});
        result.add("1.0.0".getBytes());
        return result;
    }

    public static ZMsg subsReq(final String sessionId, final long id, final String device, final String property, final String selector, final Map<String, Object> filters) throws RdaLightException {
        final ZMsg result = new ZMsg();
        result.add(new byte[] {MessageType.CLIENT_REQ.value()});
        outBuffer.reset();
        serialiser.setBuffer(outBuffer);
        serialiser.putHeaderInfo();
        serialiser.put(FieldName.REQ_TYPE_TAG.name(), RequestType.SUBSCRIBE.value());
        serialiser.put(FieldName.ID_TAG.name(), id);
        serialiser.put(FieldName.DEVICE_NAME_TAG.name(), device);
        serialiser.put(FieldName.PROPERTY_NAME_TAG.name(), property);
        serialiser.put(FieldName.UPDATE_TYPE_TAG.name(), UpdateType.NORMAL.value());
        serialiser.put(FieldName.SESSION_ID_TAG.name(), sessionId);
        // StartMarker marks start of Data Object
        serialiser.putStartMarker(new WireDataFieldDescription(serialiser, serialiser.getParent(), -1,
                FieldName.OPTIONS_TAG.name(), DataType.START_MARKER, -1, -1, -1));
        serialiser.putStartMarker(new WireDataFieldDescription(serialiser, serialiser.getParent(), -1,
                "e", DataType.START_MARKER, -1, -1, -1));
        outBuffer.flip();
        result.add(Arrays.copyOfRange(outBuffer.elements(), 0, outBuffer.limit()));
        outBuffer.reset();
        serialiser.putHeaderInfo();
        serialiser.put(FieldName.SELECTOR_TAG.name(), selector);
        if (filters != null && !filters.isEmpty()) {
            final WireDataFieldDescription filterFieldMarker = new WireDataFieldDescription(serialiser, serialiser.getParent(), -1,
                    FieldName.FILTERS_TAG.name(), DataType.START_MARKER, -1, -1, -1);
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
        result.add(new byte[] { FrameType.HEADER.value(), FrameType.BODY_REQUEST_CONTEXT.value() });
        return result;
    }

    public static ZMsg unsubReq(final String sessionId, final long id, final String device, final String property, final String selector, final Map<String, Object> filters) throws RdaLightException {
        final ZMsg result = new ZMsg();
        result.add(new byte[] {MessageType.CLIENT_REQ.value()});
        outBuffer.reset();
        serialiser.setBuffer(outBuffer);
        serialiser.putHeaderInfo();
        serialiser.put(FieldName.REQ_TYPE_TAG.name(), RequestType.UNSUBSCRIBE.value());
        serialiser.put(FieldName.ID_TAG.name(), id);
        serialiser.put(FieldName.DEVICE_NAME_TAG.name(), device);
        serialiser.put(FieldName.PROPERTY_NAME_TAG.name(), property);
        serialiser.put(FieldName.UPDATE_TYPE_TAG.name(), UpdateType.NORMAL.value());
        serialiser.put(FieldName.SESSION_ID_TAG.name(), sessionId);
        // StartMarker marks start of Data Object
        serialiser.putStartMarker(new WireDataFieldDescription(serialiser, serialiser.getParent(), -1,
                FieldName.OPTIONS_TAG.name(), DataType.START_MARKER, -1, -1, -1));
        serialiser.putStartMarker(new WireDataFieldDescription(serialiser, serialiser.getParent(), -1,
                "e", DataType.START_MARKER, -1, -1, -1));
        outBuffer.flip();
        result.add(Arrays.copyOfRange(outBuffer.elements(), 0, outBuffer.limit()));
        outBuffer.reset();
        serialiser.putHeaderInfo();
        serialiser.put(FieldName.SELECTOR_TAG.name(), selector);
        if (filters != null && !filters.isEmpty()) {
            final WireDataFieldDescription filterFieldMarker = new WireDataFieldDescription(serialiser, serialiser.getParent(), -1,
                    FieldName.FILTERS_TAG.name(), DataType.START_MARKER, -1, -1, -1);
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
        result.add(new byte[] { FrameType.HEADER.value(), FrameType.BODY_REQUEST_CONTEXT.value() });
        return result;
    }

    public static ZMsg getReq(final String sessionId, final long id, final String devName, final String prop, final String selector) {
        final ZMsg result = new ZMsg();
        result.add(new byte[] {MessageType.CLIENT_REQ.value()});
        // Header
        outBuffer.reset();
        serialiser.setBuffer(outBuffer);
        final CmwLightSerialiser serialiser = new CmwLightSerialiser(new FastByteBuffer(1024));
        serialiser.putHeaderInfo();
        serialiser.put(FieldName.REQ_TYPE_TAG.name(), RequestType.GET.value()); // GET
        serialiser.put(FieldName.ID_TAG.name(), id);
        serialiser.put(FieldName.DEVICE_NAME_TAG.name(), devName);
        serialiser.put(FieldName.PROPERTY_NAME_TAG.name(), prop);
        serialiser.put(FieldName.UPDATE_TYPE_TAG.name(), UpdateType.NORMAL.value());
        serialiser.put(FieldName.SESSION_ID_TAG.name(), sessionId);
        // StartMarker marks start of Data Object
        final WireDataFieldDescription optionsFiledDesc = new WireDataFieldDescription(serialiser, serialiser.getParent(), -1,
                FieldName.OPTIONS_TAG.name(), DataType.START_MARKER, -1, -1, -1);
        serialiser.putStartMarker(optionsFiledDesc);
        final WireDataFieldDescription sessionBodyFieldDesc = new WireDataFieldDescription(serialiser, serialiser.getParent(), -1,
                FieldName.SESSION_BODY_TAG.name(), DataType.START_MARKER, -1, -1, -1);
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
        result.add(new byte[] { FrameType.HEADER.value(), FrameType.BODY_REQUEST_CONTEXT.value()});
        return result;
    }

    public static ZMsg hbReq() {
        final ZMsg result = new ZMsg();
        result.add(new byte[] { MessageType.CLIENT_HB.value() });
        return result;
    }

    public static class Reply {
        // static instances for low level message types
        public static final Reply SERVER_HB = new Reply(MessageType.SERVER_HB);
        public static final Reply CLIENT_HB = new Reply(MessageType.CLIENT_HB);

        public MessageType messageType;

        // Connection Req/Ack
        public String version;

        // header data
        public RequestType requestType;
        public long id;
        public String deviceName;
        public UpdateType updateType;
        public String sessionId;
        public String propertyName;
        public Map<String, Object> options;
        public Map<String, Object> data;

        // additional data
        public ZFrame bodyData;
        public ExceptionMessage exceptionMessage;
        public RequestContext requestContext;
        public DataContext dataContext;

        // Subscription Update
        public long notificationId;

        // subscription established
        public long souceId;
        public String sessionBody;

        public Reply(final MessageType messageType) {
            this.messageType = messageType;
        }

        public Reply() {
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("CmwMessage: ");
            switch (messageType) {
                case CLIENT_CONNECT:
                    sb.append("Conection request, client version='").append(version).append('\'');
                    break;
                case SERVER_CONNECT_ACK:
                    sb.append("Conection ack, server version='").append(version).append('\'');
                    break;
                case CLIENT_HB:
                    sb.append("client heartbeat");
                    break;
                case SERVER_HB:
                    sb.append("server heartbeat");
                    break;
                case SERVER_REP:
                    sb.append("server reply: ");
                case CLIENT_REQ:
                    if (messageType == MessageType.CLIENT_REQ) sb.append("server reply: ");
                    sb.append("id: ").append(id)
                            .append(" deviceName=").append(deviceName)
                            .append(", updateType=").append(updateType)
                            .append(", sessionId='").append(sessionId)
                            .append(", propertyName='").append(propertyName)
                            .append(", options=").append(options)
                            .append(", data=").append(data)
                            .append(", souceId=").append(souceId);
                    switch (requestType) {
                        case GET:
                        case SET:
                            sb.append("\n  requestContext=").append(requestContext);
                            break;
                        case CONNECT:
                            break;
                        case REPLY:
                        case NOTIFICATION_DATA:
                            sb.append(", notificationId=").append(notificationId)
                                    .append("\n  bodyData=").append(bodyData)
                                    .append("\n  dataContext=").append(dataContext);
                            break;
                        case EXCEPTION:
                        case NOTIFICATION_EXC:
                        case SUBSCRIBE_EXCEPTION:
                            sb.append("\n  exceptionMessage=").append(exceptionMessage);
                            break;
                        case SUBSCRIBE:
                        case UNSUBSCRIBE:
                            break;
                        case EVENT:
                            break;
                        case SESSION_CONFIRM:
                            sb.append(", sessionBody='").append(sessionBody).append('\'');
                            break;
                    }
            }
            return sb.toString();
        }
    }

    public static class RequestContext {
        public String selector;
        public Map<String, Object> data;
        public Map<String, Object> filters;

        @Override
        public String toString() {
            return "RequestContext{" +
                    "selector='" + selector + '\'' +
                    ", data=" + data +
                    ", filters=" + filters +
                    '}';
        }
    }

    public static class DataContext {
        public String cycleName;
        public long cycleStamp;
        public long acqStamp;
        public Map<String, Object> data;

        @Override
        public String toString() {
            return "DataContext{" +
                    "cycleName='" + cycleName + '\'' +
                    ", cycleStamp=" + cycleStamp +
                    ", acqStamp=" + acqStamp +
                    ", data=" + data +
                    '}';
        }
    }

    public static class ExceptionMessage {
        public long contextAcqStamp;
        public long contextCycleStamp;
        public String message;
        public byte type;

        @Override
        public String toString() {
            return "ExceptionMessage{" +
                    "contextAcqStamp=" + contextAcqStamp +
                    ", contextCycleStamp=" + contextCycleStamp +
                    ", message='" + message + '\'' +
                    ", type=" + type +
                    '}';
        }
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

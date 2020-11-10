package de.gsi.microservice.concepts.cmwlight;

import org.zeromq.ZFrame;

import java.util.Map;

/**
 * Data representation for all Messages exchanged between CMW client and server
 */
public class CmwLightMessage {
    // static instances for low level message types
    public static final CmwLightMessage SERVER_HB = new CmwLightMessage(CmwLightProtocol.MessageType.SERVER_HB);
    public static final CmwLightMessage CLIENT_HB = new CmwLightMessage(CmwLightProtocol.MessageType.CLIENT_HB);

    // static functions to get certain message types
    public static CmwLightMessage subscriptionRequest(String sessionId, long id, String device, String property, RequestContext requestContext, CmwLightProtocol.UpdateType updateType) {
        final CmwLightMessage msg = new CmwLightMessage();
        msg.messageType = CmwLightProtocol.MessageType.CLIENT_REQ;
        msg.requestType = CmwLightProtocol.RequestType.SUBSCRIBE;
        msg.id = id;
        msg.sessionId = sessionId;
        msg.deviceName = device;
        msg.propertyName = property;
        msg.requestContext = requestContext;
        msg.updateType = updateType;
        return msg;
    }
    public static CmwLightMessage unsubscriptionRequest(String sessionId, long id, String device, String property, RequestContext requestContext, CmwLightProtocol.UpdateType updateType) {
        final CmwLightMessage msg = new CmwLightMessage();
        msg.messageType = CmwLightProtocol.MessageType.CLIENT_REQ;
        msg.requestType = CmwLightProtocol.RequestType.UNSUBSCRIBE;
        msg.id = id;
        msg.sessionId = sessionId;
        msg.deviceName = device;
        msg.propertyName = property;
        msg.requestContext = requestContext;
        msg.updateType = updateType;
        return msg;
    }
    public static CmwLightMessage getRequest(String sessionId, long id, String device, String property, RequestContext requestContext) {
        final CmwLightMessage msg = new CmwLightMessage();
        msg.messageType = CmwLightProtocol.MessageType.CLIENT_REQ;
        msg.requestType = CmwLightProtocol.RequestType.GET;
        msg.id = id;
        msg.sessionId = sessionId;
        msg.deviceName = device;
        msg.propertyName = property;
        msg.requestContext = requestContext;
        msg.updateType = CmwLightProtocol.UpdateType.NORMAL;
        return msg;
    }

    protected CmwLightMessage() {
        // Constructor only accessible from within serialiser and factory methods to only allow valid messages
    }

    public CmwLightProtocol.MessageType messageType;

    // Connection Req/Ack
    public String version;

    // header data
    public CmwLightProtocol.RequestType requestType;
    public long id;
    public String deviceName;
    public CmwLightProtocol.UpdateType updateType;
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
    public Map<String, Object> sessionBody;

    public CmwLightMessage(final CmwLightProtocol.MessageType messageType) {
        this.messageType = messageType;
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
                if (messageType == CmwLightProtocol.MessageType.CLIENT_REQ) sb.append("server reply: ");
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

    public static class RequestContext {
        public String selector;
        public Map<String, Object> data;
        public Map<String, Object> filters;

        public RequestContext(final String selector, final Map<String, Object> filters, final Map<String,Object> data) {
            this.selector = selector;
            this.filters = filters;
            this.data = data;
        }

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

        public DataContext(final String cycleName, final long cycleStamp, final long acqStamp, final Map<String, Object> data) {
            this.cycleName = cycleName;
            this.cycleStamp = cycleStamp;
            this.acqStamp = acqStamp;
            this.data = data;
        }

        protected DataContext() {
            // allow only protocol serialiser to create empty object
        }

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

        public ExceptionMessage(final long contextAcqStamp, final long contextCycleStamp, final String message, final byte type) {
            this.contextAcqStamp = contextAcqStamp;
            this.contextCycleStamp = contextCycleStamp;
            this.message = message;
            this.type = type;
        }

        protected ExceptionMessage() {
            // allow only protocol serialiser to create empty object
        }

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

}


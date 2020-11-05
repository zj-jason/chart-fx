# CMW rda3 protocol

The CMW rda3 protocol consists of zeromq multi-frame ZMsg's.

The first ZFrame contains a single byte defining the type of the Message:

    SERVER_CONNECT_ACK = (byte) 0x01;
    SERVER_REP = (byte) 0x02;
    SERVER_HB = (byte) 0x03;
    CLIENT_CONNECT = (byte) 0x20;
    CLIENT_REQ = (byte) 0x21;
    CLIENT_HB = (byte) 0x22;

## Establishing the connection

The connection is established by the Client sending a ZMsg with the first frame containing
the message type CLIENT_CONNECT, followed a ZFrame containing the version string
"1.0.0" (the version string is not used).
The server acknowledges by sending SERVER_CONNECT_ACK, followed by the version
string ("1.0.0", not used).

## Heartbeats

The rda3 protocol uses heartbeats for connection management. Client as well as server
regularily send messages only consisting of a single one-byte frame containing SERVER_HB/
CLIENT_HB. If client or server do not receive a heartbeat or any other message for some time,
the connection is reset.

## Requests/Replies

The client can send requests and the server sends replies, indicated by the types CLIENT_REQ
and SERVER_REP.
The message type frame is followed by an arbitrary number of frames, where the last one is the
so called descriptor, which contains one byte for each previous frame, containing the type
of the frame contents.

    MT_HEADER = 0;
    MT_BODY = 1;
    MT_BODY_DATA_CONTEXT = 2;
    MT_BODY_REQUEST_CONTEXT = 3;
    MT_BODY_EXCEPTION = 4;

The first frame is always of type MT_HEADER and its field reqType defines the type of the
request/reply:

| message type          | byte | message | direction | comment |
|:---------------------:|:----:|:-------:|:---------:|:-------:|
|RT_GET                 | 0    | H, RC   | C->S      |         |
|RT_SET                 | 1    | H,B,RC  | C->S      |         |
|RT_CONNECT             | 2    | H       | C->S      |         |
|RT_REPLY               | 3    | H,B,DC  | S->C      |         |
|RT_EXCEPTION           | 4    | H,B     | S->C      |         |
|RT_SUBSCRIBE           | 5    | H, RC   | C->S      |         |
|RT_UNSUBSCRIBE         | 6    | H, RC   | C->S      |         |
|RT_NOTIFICATION_DATA   | 7    | H,B,DC  | S->C      |         |
|RT_NOTIFICATION_EXC    | 8    | H,B     | S->C      |         |
|RT_SUBSCRIBE_EXCEPTION | 9    | H,B     | S->C      |         |
|RT_EVENT               | 10   |         | C->S      | close   | 
|RT_SESSION_CONFIRM     | 11   | H       | S->C      |         |

### Header fields:
- REQ_TYPE_TAG = "2";
- ID_TAG = "0";
- DEVICE_NAME_TAG = "1"; empty for subscription notifications
- UPDATE_TYPE_TAG = "7";
- SESSION_ID_TAG = "d"; empty for subscription notifications
- PROPERTY_NAME_TAG = "f"; empty for subscription notifications
- OPTIONS_TAG = "3";
  - optional NOTIFICATION_ID_TAG = "a"; for notification data
  - optional SOURCE_ID_TAG = "b"; for subscription requests to propagate the id
  - optional SESSION_BODY_TAG = "e"; for session context/RBAC

### Request Context Fields
- SELECTOR_TAG = "8";
- FILTERS_TAG = "c";
  - cmw data fields with values of various types
- DATA_TAG = "x";
  - here be dragons, has to be a cmw Data object

### Data Context Fields
- CYCLE_NAME_TAG = "4";
- ACQ_STAMP_TAG = "5";
- CYCLE_STAMP_TAG = "6";
- DATA_TAG = "x"; // free defined data, gsi custom
  - "acqStamp"
  - "cycleStamp"
  - "cycleName"
  - "version"
  - "type"
  
### connect body
- CLIENT_INFO_TAG = "9";
  
### Exception body field
- EXCEPTION_MESSAGE_FIELD = "Message";
- EXCEPTION_TYPE_FIELD = "Type";
- EXCEPTION_BACKTRACE_FIELD = "Backtrace";
- EXCEPTION_CONTEXT_CYCLE_NAME_FIELD = "ContextCycleName";
- EXCEPTION_CONTEXT_CYCLE_STAMP_FIELD = "ContextCycleStamp";
- EXCEPTION_CONTEXT_ACQ_STAMP_FIELD = "ContextAcqStamp";
- EXCEPTION_CONTEXT_DATA_FIELD = "ContextData";

### currently unused field names
- MESSAGE_TAG = "message";
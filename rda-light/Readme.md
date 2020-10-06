# rda-light: A lightweight CMW-rda3 client implementation in pure java

CMW (the CERN comon middleware) is a middleware communication framework used to
connect different parts of the accelerator control system.
The main implementation supports legacy protocols and different ZeroMQ implementations.
This leads to complicated dependencies which in addition to the code not being
openly available make it harder to use it in open source development.

This implementation tries to implement the basic client (Connect/Get/Set/(Un)Subscribe)
functionality with only [JeroMQ](https://github.com/zeromq/jeromq) and 
[ChartFx's CmwLightSerialiser](https://github.com/GSI-CS-CO/chart-fx/blob/master/microservice/src/main/java/de/gsi/serializer/spi/CmwLightSerialiser.java)
as external dependencies.

Its purpose is to be used in the open source middle-tier service implementation compatible with
the existing control systems.

## Wire Protocol
Documentation of the low-level wire-protocol in terms of zeromq primitives for cmw-rda3.

### Socket configuration
The cmw client uses a zeromq DEALER socket to commuicate with cmw servers.
The following socket properties should be configured via Properties/Environent Variables:
- sndHWM: send High Water Mark, default 0
- rcvHWM: receive high water mark, default 0
- linger: default 0

Additionally the socket identity has to be set to `"<hostname>/<pid>/<conId>/<channelId>"`.
The values itself are not really important, but the general format has to be obeyed.

### General Message Format
In general rda3 uses multi frame zeroMQ messages. The first Frame is always one byte and determines the type of the Message.
Depending on the message type, other frames can follow. There are 6 different message types.

### Connecting
To connect, the client sends a message of type `CLIENT_CONNECT` (0x20) followed by a frame containing a zeroMQ string with
the version. For now the version is always "1.0.0" and it is never evaluated.

The server replies with a message of type `SERVER_CONNECT_ACK` (0x01), also followed by a version string.

### Heartbeat
To keep the connection open and responsive, cmw periodically sends heartbeat messages and closes the connection if
a configurable number of heartbeats is missed.

Heartbeat messages consist of a single frame with a single byte, for the server `SERVER_HB` (0x03) and for the client `CLIENT_HB`(0x22).

### Requests and Replies
To get or set data, the client can send requests `CLIENT_REQ` (0x21) upon which the server will send reply messages `SERVER_REP`(0x02).

The message format for requests and replies consists of several frames which are encoded using the cmw-data/CmwLightSerialiser serialisers.
The last frame is the so called descriptor which contains one byte for each data frame and determines its type:
- 0x00: Header
- 0x01: Body
- 0x02: Reply Context
- 0x03: Request Context
- 0x04: Exception

The header field REQ_TYPE (byte) specifies the type of request or replies:
- `RT_GET` = 0
- `RT_SET` = 1
- `RT_CONNECT` = 2 // ?
- `RT_REPLY` = 3
- `RT_EXCEPTION` = 4
- `RT_SUBSCRIBE` = 5
- `RT_UNSUBSCRIBE` = 6
- `RT_NOTIFICATION_DATA` = 7
- `RT_NOTIFICATION_EXC` = 8
- `RT_SUBSCRIBE_EXCEPTION` = 9
- `RT_EVENT` = 10 // Also used as close
- `RT_SESSION_CONFIRM` = 11

#### Get
A get request consists of the following parts:
- CLIENT_REQ (0x21)
- request_header: cmw data serialised structure containing e.g. the type of request
  - REQ_TYPE_TAG: "2"=5 (byte)
  - ID_TAG:       "0"=<free> (long)
  - DEVICE_NAME_TAG: "1"=<DeviceName> (String)
  - PROPERTY_NAME_TAG: "f"=<PropertyName> (String)
  - UPDATE_TYPE_TAG: "7"=0 (UpdateTypeNormal, byte)
  - SESSION_ID_TAG: "d"=<sessionId> (choose freely, String)
  - OPTIONS_TAG: "3" (Data)
    - "e" (Data, can be empty)
- request_context: cmw data serialised structure specifying the context to get a value for
  - SELECTOR_TAG: "8"=<context> (String, FAIR.SELECTOR...)
  - FILTERS_TAG: "c" (data, optional)
  - DATA_TAG: "x" (data, optional)
- descriptor: [ 0x00, 0x03 ]

The server can either reply with the requested data or with an exception. In the former case it sends:
- SERVER_REP 0x02
- reply_header
- reply_body
- reply_context
- descriptor: [ 0x00, 0x01, 0x02 ]

and in the exception case
- SERVER_REP 0x02
- reply_header
- exception
- descriptor: [ 0x00, 0x04 ]

#### Subscription
Subscriptions are actually very similar to get requests
- CLIENT_REQ (0x21)
- request_header
- request_context
- descriptor: [ 0x00, 0x03 ]

The server then replies either with an acknowledgement or an exception.

In case of acknowledgement, each notify on the server leads to a message in the same format as the reply to the GET request.

To close the subscription another request can be sent:
tbd

#### Set
tbd

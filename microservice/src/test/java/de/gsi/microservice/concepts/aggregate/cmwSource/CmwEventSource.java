package de.gsi.microservice.concepts.aggregate.cmwSource;

import de.gsi.dataset.utils.RingBuffer;
import de.gsi.microservice.concepts.aggregate.RingBufferEvent;
import de.gsi.microservice.concepts.cmwlight.CmwLightClient;
import de.gsi.microservice.concepts.cmwlight.CmwLightProtocol;
import de.gsi.microservice.concepts.cmwlight.DirectoryLightClient;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An Event source which publishes events for multiple CMW sources a disrupter ring buffer
 */
public class CmwEventSource implements Runnable {
    private static final String DIRECTORY_SERVER = "cmwpro00a.acc.gsi.de:5021";
    private final Map<String, CmwServer> servers = new ConcurrentHashMap<>(); // devicename -> server
    private final RingBuffer<RingBufferEvent> rb;
    private final DirectoryLightClient directoryLightClient = new DirectoryLightClient(DIRECTORY_SERVER);

    public CmwEventSource(final RingBuffer<RingBufferEvent> rb) throws DirectoryLightClient.DirectoryClientException {
        this.rb = rb;
    }

    public void subscribe(final String device, final String property, final String selector, final Map<String, Object> filters) {
        servers.computeIfAbsent(device, dev -> {
            final CmwServer server = new CmwServer();
            try {
                final List<DirectoryLightClient.Device> devInfos = directoryLightClient.getDeviceInfo(Collections.singletonList(dev));
                if (devInfos.size() != 1) {
                    return null;
                }
                final DirectoryLightClient.Device devInfo = devInfos.get(0);
                final Map<String, String> srvInfo = devInfo.servers.get(0);
                server.name = devInfo.name;
                server.address = srvInfo.get("Address: ");
                return server;
            } catch (DirectoryLightClient.DirectoryClientException e) {
                return null;
            }
        });

    }

    @Override
    public void run() {
        while (!Thread.interrupted()) {
            for (CmwServer server : servers.values()) {
                checkConnectionStatus(server);
                // poll messages
                while (!Thread.interrupted()) { // todo add timeout so one connection cannot block the others? or switch nesting?
                    try {
                        final CmwLightProtocol.Reply reply = server.cmwClient.receiveData();
                        // connection established, update status
                        // subscription established, update status
                        // data received -> put into buffer
                        // exception received -> put into buffer
                    } catch (CmwLightProtocol.RdaLightException e) {
                        continue;
                    }
                }
            }
        }
    }

    private void checkConnectionStatus(final CmwServer server) {
        if (server.conStatus.equals(ConnectionStatus.OFFLINE)) {
            server.cmwClient.connect();
            server.conStatus = ConnectionStatus.CONNECTING;
        } else {
            for (CmwDevice dev : server.devices.values()) {
                for (CmwSubscription sub : dev.subscriptions.values()) {
                    if (sub.conStatus.equals(ConnectionStatus.OFFLINE)) {
                        server.cmwClient.subscribe(sub.device, sub.property, sub.selector, sub.filters);
                        sub.conStatus = ConnectionStatus.CONNECTING;
                    }
                }
            }
        }
    }

    public static class CmwServer {
        String name;
        String address;
        CmwLightClient cmwClient;
        Map<String, CmwDevice> devices = new ConcurrentHashMap<>();
        ConnectionStatus conStatus = ConnectionStatus.OFFLINE;
    }

    public static class CmwDevice {
        String name;
        Map<String, CmwSubscription> subscriptions = new ConcurrentHashMap<>();
    }

    public static class CmwSubscription{
        String device;
        String property;
        String selector;
        Map<String, Object> filters = new HashMap<>();
        String sessionId;
        ConnectionStatus conStatus = ConnectionStatus.OFFLINE;
    }

    public enum ConnectionStatus {
        OFFLINE, CONNECTING, CONNECTED
    }
}

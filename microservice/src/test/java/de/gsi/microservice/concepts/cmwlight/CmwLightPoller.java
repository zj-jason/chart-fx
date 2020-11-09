package de.gsi.microservice.concepts.cmwlight;

import org.zeromq.ZMQ;

import java.util.HashMap;
import java.util.Map;

public class CmwLightPoller implements Runnable {
    private Map<String, CmwLightClient> clients = new HashMap<>(); // address -> client
    private ZMQ.Poller poller;
    private ZMQ.Context context;
    private boolean disconnectIdleClients = false;
    private long timeout = 1000;

    public CmwLightPoller() {
        context = ZMQ.context(1);
        poller = context.poller();
    }

    public void add(CmwLightClient client) {
        clients.put(client.getAddress(), client);
        poller.register(client.getSocket(), ZMQ.Poller.POLLIN);
    }

    /**
     * Polls a single message from one of the clients with the given timeout
     *
     * @param tout How long to wait if there are no messages waiting
     * @return an object with a reply or null if the poll timed out
     * @throws CmwLightProtocol.RdaLightException
     */
    public CmwLightProtocol.Reply poll(long tout) throws CmwLightProtocol.RdaLightException {
        // wait for data to become available
        if (poller.poll(tout) <= 0) {
            return null;
        }
        // get data from clients
        for (CmwLightClient client: clients.values()) {
            final CmwLightProtocol.Reply reply = client.receiveData();
            if (reply != null) {
                return reply; // this always asks the first client first... we should do round robin
            }
        }

        if (disconnectIdleClients) {
            // periodically check for clients without any subscriptions or recent activity
        }

        // no data was received
        return null;
    }

    /**
     * Simple event loop which calls a callback for received data
     */
    @Override
    public void run() {
        while (!Thread.interrupted()) {
            try {
                final CmwLightProtocol.Reply reply = poll(timeout);
                if (reply != null && reply.requestType == CmwLightProtocol.RequestType.NOTIFICATION_DATA) {
                    // subscriptionCallback.call(reply);
                    System.out.println(reply);
                }
            } catch (CmwLightProtocol.RdaLightException e) {
                e.printStackTrace();
            }
        }
    }

    // private <T> void subscribe(device, property, selector, filter, callback<T>, errorCallback) {
    // public <T> Future<T> get(device, property, selector) {
    // public <T> void get(device, property, selector, callback<T>, errorCallback) {
}

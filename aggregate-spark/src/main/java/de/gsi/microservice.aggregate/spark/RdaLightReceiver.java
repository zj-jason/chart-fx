package de.gsi.microservice.aggregate.spark;

import de.gsi.microservice.cmwlight.CmwLightClient;
import de.gsi.microservice.cmwlight.DirectoryLightClient;
import org.apache.spark.storage.StorageLevel;
import org.apache.spark.streaming.receiver.Receiver;

import java.util.Collections;

/**
 * A Spark Streaming receiver for CMW properties.
 * Subscribes to a specific device/property/selector and send each update as a stream.
 */
public class RdaLightReceiver extends Receiver<Object> {
    private static final String DIRECTORY_SERVER = "cmwpro00a.acc.gsi.de:5021";
    CmwLightClient client;
    DirectoryLightClient directoryClient;
    final String device;
    final String property;
    final String selector;

    public RdaLightReceiver(final String device, final String property, final String selector) {
        super(StorageLevel.MEMORY_AND_DISK_2());
        this.device = device;
        this.property = property;
        this.selector = selector;
    }

    @Override
    public void onStart() {
        System.out.println("starting rda light receiver");
        new Thread(this::receive).start();
    }

    private void receive() {
        try {
            directoryClient = new DirectoryLightClient(DIRECTORY_SERVER);
            // get first address for the device
            final String address = directoryClient.getDeviceInfo(Collections.singletonList(device)).get(0).servers.get(0).get("Address:");
            client = new CmwLightClient(address);
            client.connect();
            client.subscribe(device, property, selector);
            System.out.println("Subscription started");

            while (!isStopped()) {
                final CmwLightClient.Reply result = client.receiveData();
                System.out.println("Received data '" + result + "'");
                if (result instanceof CmwLightClient.DataReply) {
                    final byte[] body = ((CmwLightClient.DataReply) result).dataBody.getData();
                    store(body);
                }
                if (result == null) {
                    client.sendHeartBeat();
                }
            }
            client.unsubscribe(device, property, selector);

            // Restart in an attempt to connect again when server is active again
            restart("Trying to connect again");
        } catch (DirectoryLightClient.DirectoryClientException | CmwLightClient.RdaLightException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onStop() {
        // don't do anything
    }

}
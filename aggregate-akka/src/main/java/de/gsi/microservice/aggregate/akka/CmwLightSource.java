package de.gsi.microservice.aggregate.akka;

import akka.NotUsed;
import akka.stream.Attributes;
import akka.stream.Outlet;
import akka.stream.SourceShape;
import akka.stream.javadsl.Source;
import akka.stream.stage.AbstractOutHandler;
import akka.stream.stage.GraphStage;
import akka.stream.stage.GraphStageLogic;
import de.gsi.microservice.cmwlight.CmwLightClient;
import de.gsi.microservice.cmwlight.DirectoryLightClient;

import java.util.Collections;

/**
 * Subscribe to a CMW property and expose the data as an AKKA Streaming Source.
 */
public class CmwLightSource extends GraphStage<SourceShape<String>> {
    public final Outlet<String> out = Outlet.create("TrimSource.out");
    private final SourceShape<String> shape = SourceShape.of(out);
    private static final String DIRECTORY_SERVER = "cmwpro00a.acc.gsi.de:5021";
    final String device;
    final String property;
    final String selector;

    @Override
    public SourceShape<String> shape() {
        return shape;
    }

    public static Source<String, NotUsed> create(final String device, final String property, final String selector) {
        return Source.fromGraph(new CmwLightSource(device, property, selector));
    }

    public CmwLightSource (final String device, final String property, final String selector) {
        this.device = device;
        this.property = property;
        this.selector = selector;
    }

    @Override
    public GraphStageLogic createLogic(final Attributes inheritedAttributes) throws Exception {
        return new GraphStageLogic(shape()) {
            CmwLightClient client;
            DirectoryLightClient directoryClient;

            @Override
            public void preStart() {
                try {
                    directoryClient = new DirectoryLightClient(DIRECTORY_SERVER);
                    // get first address for the device
                    final String address = directoryClient.getDeviceInfo(Collections.singletonList(device)).get(0).servers.get(0).get("Address:");
                    client = new CmwLightClient(address);
                    client.connect();
                    client.subscribe(device, property, selector);
                    System.out.println("Subscription started");

                } catch (DirectoryLightClient.DirectoryClientException | CmwLightClient.RdaLightException e) {
                    e.printStackTrace();
                }
            }

            {
                setHandler(out,
                        new AbstractOutHandler() {
                            @Override
                            public void onPull () throws Exception {
                                while (true) {
                                    final CmwLightClient.Reply result = client.receiveData(); // todo add timeout
                                    System.out.println("Received data '" + result + "'");
                                    if (result instanceof CmwLightClient.DataReply) {
                                        final byte[] body = ((CmwLightClient.DataReply) result).dataBody.getData();
                                        push(out, body.toString());
                                        break;
                                    }
                                    if (result == null) {
                                        client.sendHeartBeat();
                                    }
                                }
                            }

                            @Override
                            public void onDownstreamFinish(final Throwable cause) throws Exception {
                                client.unsubscribe(device, property, selector);
                                super.onDownstreamFinish(cause);
                            }
                        });
            }
        };
    }
}

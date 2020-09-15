package de.gsi.rdalegacy;

import cern.cmw.data.Data;
import cern.cmw.data.DataFactory;
import cern.cmw.rda3.common.data.AcquiredData;
import cern.cmw.rda3.common.exception.RdaException;
import cern.cmw.rda3.common.exception.ServerException;
import cern.cmw.rda3.impl.common.data.AcquiredContextImpl;
import cern.cmw.rda3.impl.transport.zeromq.ZMQAdapter;
import cern.cmw.rda3.server.core.GetRequest;
import cern.cmw.rda3.server.core.Server;
import cern.cmw.rda3.testing.NameServiceMock;
import cern.cmw.rda3.testing.ServerMock;
import cern.cmw.util.config.ConfigurationBuilder;

public class MockServer {
    public static void main(String[] args) throws RdaException, InterruptedException {
        final NameServiceMock nsMock = new NameServiceMock();
        final ServerMock mockServer = new ServerMock("testdevice", serverInfo -> System.out.println("Server registered: " + serverInfo),
                ConfigurationBuilder.newInstance()
                        .setProperty("cmw.rda3.transport.server.port", "7777")
                        .setProperty("cmw.rda3.transport.zeromq.provider", ZMQAdapter.Provider.JEROMQ.name())
                        .build());
        mockServer.start();
        mockServer.onGet = req -> {
            System.out.println("Get request: " + req.toString());
            if (req.getPropertyName().equals("testproperty")) {
                final Data data = DataFactory.createData();
                data.append("payload", "Hello CERN!");
                AcquiredData acquiredData = new AcquiredData(data, new AcquiredContextImpl("FAIR.SELECTOR.C=1:T=300:S=3:P=2", 12234532,133713371337l, DataFactory.createData()));
                req.requestCompleted(acquiredData);
            } else {
                System.out.println("Get request for unknown property requested");
                req.requestFailed(new ServerException("rejecting GET request for unknown property"));
            }
        };
        mockServer.onSubscribe = req -> {
            if (req.getPropertyName().equals("testproperty")) {
                req.accept();
            } else {
                System.out.println("rejecting subscription: " + req.toString());
                req.reject(new ServerException("rejecting submission for unkonwn property"));
            }
        };
        while (true) {
            Thread.sleep(1000);
            final Data data = DataFactory.createData();
            data.append("time", System.currentTimeMillis());
            AcquiredData acquiredData = new AcquiredData(data, new AcquiredContextImpl("FAIR.SELECTOR.C=1:T=300:S=3:P=2", 1,2, DataFactory.createData()));
            mockServer.notify("testdevice", "testproperty", acquiredData);
            System.out.println("Sent new data: " + acquiredData.toString());
        }
    }
}

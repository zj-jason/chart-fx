package de.gsi.rdalegacy;

import cern.cmw.rda3.common.exception.RdaException;
import cern.cmw.rda3.impl.transport.zeromq.ZMQAdapter;
import cern.cmw.rda3.testing.NameServiceMock;
import cern.cmw.rda3.testing.ServerMock;
import cern.cmw.util.config.ConfigurationBuilder;

public class MockServer {
    public static void main(String[] args) throws RdaException, InterruptedException {
        final NameServiceMock nsMock = new NameServiceMock();
        final ServerMock mockServer = new ServerMock("testserver", nsMock,
                ConfigurationBuilder.newInstance()
                        .setProperty("cmw.rda3.transport.zeromq.provider", ZMQAdapter.Provider.JEROMQ.name())
                        .build());
        mockServer.start();
        while (true) {
            Thread.sleep(1000);
        }
    }
}

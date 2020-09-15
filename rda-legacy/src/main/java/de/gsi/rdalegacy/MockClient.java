package de.gsi.rdalegacy;

import cern.cmw.rda3.client.core.AccessPoint;
import cern.cmw.rda3.client.service.ClientService;
import cern.cmw.rda3.client.service.ClientServiceBuilder;
import cern.cmw.rda3.client.service.NameService;
import cern.cmw.rda3.client.subscription.NotificationListener;
import cern.cmw.rda3.client.subscription.Subscription;
import cern.cmw.rda3.common.data.AcquiredData;
import cern.cmw.rda3.common.data.UpdateType;
import cern.cmw.rda3.common.exception.NameServiceException;
import cern.cmw.rda3.common.exception.RdaException;
import cern.cmw.rda3.common.info.RemoteHostInfo;
import cern.cmw.rda3.impl.common.info.RemoteHostInfoImpl;
import cern.cmw.rda3.impl.transport.zeromq.ZMQAdapter;
import cern.cmw.rda3.transport.Address;
import cern.cmw.util.config.ConfigurationBuilder;
import cern.cmw.util.version.Version;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class MockClient {
    public static void main(String[] args) throws RdaException, InterruptedException {
        final ClientService client = ClientServiceBuilder.newInstance().setClientName("testclient")
                .setNameService(new NameService() {
                    @Override
                    public String getServerName(final String deviceName) throws NameServiceException {
                        return "localhost";
                    }

                    @Override
                    public Map<String, String> getServersNames(final List<String> devicesNames) throws NameServiceException {
                        return Collections.singletonMap(devicesNames.get(0), "localhost");
                    }

                    @Override
                    public RemoteHostInfo getServerInfo(final String serverName) throws NameServiceException {
                        return new RemoteHostInfoImpl("foo", Address.createFromString("tcp://SYSPC004:6273"),  new Version(Version.UNDEFINED_VERSION));
                    }
                })
                .setConfig(ConfigurationBuilder.newInstance()
                        .setProperty("cmw.rda3.transport.zeromq.provider", ZMQAdapter.Provider.JEROMQ.name())
                        .build())
                .build();
        final AccessPoint ap = client.getAccessPoint("testserver", "testproperty", "localhost");
        final AcquiredData data = ap.get();
        System.out.println("get() returned: " + data);

        final Subscription sub = ap.subscribe(new NotificationListener() {
            @Override
            public void dataReceived(final Subscription subscription, final AcquiredData value, final UpdateType updateType) {
                System.out.println("data received: " + value);
            }

            @Override
            public void errorReceived(final Subscription subscription, final RdaException error, final UpdateType updateType) {
                System.out.println("error received: " + error);
            }
        });
        Thread.sleep(10000);
        sub.unsubscribe();
    }
}

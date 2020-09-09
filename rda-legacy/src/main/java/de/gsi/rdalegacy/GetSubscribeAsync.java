package de.gsi.rdalegacy;

import cern.cmw.rda3.client.core.AccessPoint;
import cern.cmw.rda3.client.core.AsyncGetCallback;
import cern.cmw.rda3.client.core.RequestHandle;
import cern.cmw.rda3.client.service.ClientService;
import cern.cmw.rda3.client.service.ClientServiceBuilder;
import cern.cmw.rda3.client.subscription.NotificationListener;
import cern.cmw.rda3.client.subscription.Subscription;
import cern.cmw.rda3.common.data.AcquiredData;
import cern.cmw.rda3.common.data.RequestContextFactory;
import cern.cmw.rda3.common.data.UpdateType;
import cern.cmw.rda3.common.exception.RdaException;

/**
 * TODO
 * 
 * @author Ilia Yastrebov
 * @version $Revision$, $Date$, $Author$
 */
public class GetSubscribeAsync {

    public static void main(String[] args) {
        ClientService client = null;
        try {
            client = ClientServiceBuilder.newInstance().build();

            for (int i = 0; i < 20; ++i) {

                AccessPoint accessPoint = client.getAccessPoint("device", "property");

                accessPoint.getAsync(null, new AsyncGetCallback() {
                    @Override
                    public void requestFailed(RequestHandle request, RdaException exception) {
                        exception.printStackTrace();
                    }

                    @Override
                    public void requestCompleted(RequestHandle request, AcquiredData value) {
                        System.out.println(value.getData());
                    }
                });

                ++i;
                accessPoint = client.getAccessPoint("CMW.RDA3.TEST" + i, "Acquisition", "TestServerR");

                accessPoint.subscribe(RequestContextFactory.create("Cycle"), new NotificationListener() {
                    @Override
                    public void errorReceived(Subscription subscription, RdaException exception, UpdateType updateType) {
                        exception.printStackTrace();
                    }

                    @Override
                    public void dataReceived(Subscription subscription, AcquiredData value, UpdateType updateType) {
                        System.out.println(value.getData().getString("field"));
                    }
                });

            }

        } catch (RdaException ex) {
            ex.printStackTrace();
        }finally {
            if (client != null) {
                client.close();
            }
        }

    }
}

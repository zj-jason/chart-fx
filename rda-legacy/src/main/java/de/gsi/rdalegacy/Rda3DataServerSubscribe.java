package de.gsi.rdalegacy;

import cern.cmw.data.Data;
import cern.cmw.data.DataFactory;
import cern.cmw.directory.client.DirectoryProperties;
import cern.cmw.rda3.client.core.AccessPoint;
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
public class Rda3DataServerSubscribe {
    
    static {
        System.setProperty(DirectoryProperties.SYSTEM_PROPERTY_CMW_DIRECTORY_ENV, "TEST");
    }    

    private static int notificationCounter = 0;

    public static void main(String[] args) {
        try {
            // Choose the server you want do run the demo test
            String serverName = "Rda3TestServerCpp"; // default name (e.g. when run localy on the mashine)
            //String serverName = "Rda3TestServerJava"; // default name (e.g. when run localy on the mashine)
            // String serverName = "CmwTest.RDA3.cfc-ccr-ctb05"; // testbedserver

            ClientService client = ClientServiceBuilder.newInstance().build();

            AccessPoint newUIDAccessPoint = client.getAccessPoint("SERVER", "NEW_DEVICE_UID", serverName);
            AcquiredData newUIDData = newUIDAccessPoint.get(null);
            long deviceUID = newUIDData.getData().getLong("deviceUID");
            String currentTestDeviceName = "Device_" + deviceUID;

            AccessPoint accessPoint = client.getAccessPoint(currentTestDeviceName, "Property", serverName);

            Data errorData1 = DataFactory.createData();
            AccessPoint accessPointForError = client.getAccessPoint(currentTestDeviceName, "Property.EXCEPTION", serverName);
            accessPointForError.set(errorData1, RequestContextFactory.create("Cycle_1"));

            Data setData = DataFactory.createData();
            setData.append("int32Value", 42);
            setData.append("doubleValue", 3.1415);
            accessPoint.set(setData, RequestContextFactory.create("Cycle_1"));

            System.out.println("Creating subsription");
            Subscription sub = accessPoint.subscribe(RequestContextFactory.create("Cycle_1"), new NotificationListener() {
                @Override
                public void errorReceived(Subscription subscription, RdaException exception, UpdateType updateType) {
                    System.out.println("Exception received");
                    exception.printStackTrace();
                }

                @Override
                public void dataReceived(Subscription subscription, AcquiredData value, UpdateType updateType) {
                    if (value.getData() != null) {
                        System.out.println(value.getData().toString());
                        notificationCounter += 1;
                    } else {
                        System.out.println("data is null!!");
                    }
                }
            });

            Thread.sleep(10000);

            AccessPoint statAccessPoint = client.getAccessPoint(currentTestDeviceName, "Property.STAT", serverName);
            AcquiredData statisticsData = statAccessPoint.get(RequestContextFactory.create("Cycle_1"));
            int notifyCounter = statisticsData.getData().getInt("counterNotify");

            System.out.println("Notify Counter from Server is: " + notifyCounter
                    + " Amount of notifications from test is " + notificationCounter);

            Data errorData = DataFactory.createData();
            // AccessPoint accessPointForError = client.getAccessPoint("Device", "Property#EXCEPTION");
            errorData.append("errorType", "InvalidRequestException");
            accessPointForError.set(errorData, RequestContextFactory.create("Cycle_1"));

            Thread.sleep(5000);
            // Probably the subscription will throw an error here

            accessPoint.unsubscribe(sub);

            // Clear the server
            Data resetData = DataFactory.createData();
            AccessPoint resetAccessPoint = client.getAccessPoint("SERVER", "DEVICE_RESET", serverName);
            resetData.append("deviceName", currentTestDeviceName);
            resetAccessPoint.set(resetData, RequestContextFactory.create("Cycle_1"));

            System.exit(0);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

}

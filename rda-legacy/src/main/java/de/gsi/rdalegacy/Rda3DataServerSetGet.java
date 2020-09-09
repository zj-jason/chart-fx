/**
 * Copyright (c) 2013 European Organisation for Nuclear Research (CERN), All Rights Reserved.
 */

package de.gsi.rdalegacy;

import cern.cmw.data.Data;
import cern.cmw.data.DataFactory;
import cern.cmw.directory.client.DirectoryProperties;
import cern.cmw.rda3.client.core.AccessPoint;
import cern.cmw.rda3.client.service.ClientService;
import cern.cmw.rda3.client.service.ClientServiceBuilder;
import cern.cmw.rda3.common.data.AcquiredData;
import cern.cmw.rda3.common.data.RequestContext;
import cern.cmw.rda3.common.data.RequestContextFactory;
import cern.cmw.rda3.common.exception.RdaException;

import java.util.Random;

/**
 * A basic demo client, which shows how to work with the RDA3TestServer.
 * 
 * @author Vitaliy Rapp
 */
public class Rda3DataServerSetGet {

    static {
        System.setProperty(DirectoryProperties.SYSTEM_PROPERTY_CMW_DIRECTORY_ENV, "TEST");
    }    

    public static void main(final String[] args) {

        try {
            // Choose the server you want to run the demo test
            String serverName = "Rda3TestServerCpp"; // default name (e.g. when run localy)
            // String serverName = "CmwTest.RDA3.cfc-ccr-ctb05"; // testbed server

            ClientService client = ClientServiceBuilder.newInstance().build();

            AccessPoint newUIDAccessPoint = client.getAccessPoint("SERVER", "NEW_DEVICE_UID", serverName);
            AcquiredData newUIDData = newUIDAccessPoint.get(null);
            long deviceUID = newUIDData.getData().getLong("deviceUID");
            String currentTestDeviceName = "Device_" + deviceUID;

            Data setData = DataFactory.createData();
            Data contextData = DataFactory.createData();
            AccessPoint accessPoint = client.getAccessPoint(currentTestDeviceName, "TestSettingProperty", serverName);
            AccessPoint contextAP = client.getAccessPoint(currentTestDeviceName, "TestSettingProperty.REQUEST_CONTEXT",
                    serverName);

            RequestContext context = RequestContextFactory.create("Cycle32", contextData);

            Random rand = new Random();

            for (int j = 0; j < 20; j++) {
                for (int i = 0; i < 1000; i++) {
                    int expectedIntValue = rand.nextInt();
                    double expectedDoubleValue = (double) expectedIntValue / rand.nextInt();
                    setData.append("int32Value", expectedIntValue);
                    setData.append("doubleValue", expectedDoubleValue);

                    boolean expectedBooleanContextValue = rand.nextBoolean();
                    String expectedStringContextValue = "Superstring_" + rand.nextInt() + "_number";

                    contextData.append("booleanValue", expectedBooleanContextValue);
                    contextData.append("stringValue", expectedStringContextValue);

                    context = RequestContextFactory.create("Cycle32", contextData);

                    accessPoint.set(setData, context);

                    AcquiredData aquiredData = accessPoint.get(context);
                    int result = aquiredData.getData().getInt("int32Value");
                    if (expectedIntValue != result) {
                        System.out.println("ERROR: Integer Value was not the same. Expected " + expectedIntValue
                                + " but was " + result);
                    }

                    double dobuleResult = aquiredData.getData().getDouble("doubleValue");
                    if (Math.abs(dobuleResult - expectedDoubleValue) > 0.00000001) {
                        System.out.println("ERROR: Double value was not the same. Expected " + expectedDoubleValue
                                + " but was " + dobuleResult);
                    }

                    aquiredData = contextAP.get(context);
                    boolean bValue = aquiredData.getData().getData(".REQUEST_CONTEXT_FILTER").getBool("booleanValue");
                    if (bValue != expectedBooleanContextValue) {
                        System.out.println("ERROR: Boolean Value in the context was not the same. Expected "
                                + expectedBooleanContextValue + " but was " + bValue);
                    }

                    String sValue = aquiredData.getData().getData(".REQUEST_CONTEXT_FILTER").getString("stringValue");
                    if (!expectedStringContextValue.equals(sValue)) {
                        System.out.println("ERROR: String Value in the context was not the same. Expected "
                                + expectedStringContextValue + " but was " + sValue);
                    }

                    Thread.sleep(2);
                }
                System.out.println("Test Checkpoint - Simple Data Burst " + j + " finished OK");
                Thread.sleep(1000);
            }

            System.out.println("Test Checkpoint - Simple Data Test finished OK");

            AccessPoint statAccessPoint = client.getAccessPoint(currentTestDeviceName, "TestSettingProperty.STAT",
                    serverName);
            AcquiredData statisticsData = statAccessPoint.get(context);
            int getCounter = statisticsData.getData().getInt("counterGet");
            int setCounter = statisticsData.getData().getInt("counterSet");

            if (getCounter != 20000) {
                System.out.println("ERROR: Get counter does not match. Expected 20000 but was " + getCounter);
            }

            if (setCounter != 20000) {
                System.out.println("ERROR: Set counter does not match. Expected 20000 but was " + setCounter);
            }

            System.out.println("Test Checkpoint - Counters are OK");

            for (int j = 0; j < 20; j++) {
                for (int i = 0; i < 1000; i++) {
                    int expectedIntValue1 = rand.nextInt();
                    double expectedDoubleValue1 = (double) expectedIntValue1 / rand.nextInt();
                    int expectedIntValue2 = rand.nextInt();
                    double expectedDoubleValue2 = (double) expectedIntValue2 / rand.nextInt();
                    int expectedIntValue3 = rand.nextInt();
                    double expectedDoubleValue3 = (double) expectedIntValue3 / rand.nextInt();
                    int expectedIntValue4 = rand.nextInt();
                    double expectedDoubleValue4 = (double) expectedIntValue4 / rand.nextInt();
                    int expectedIntValue5 = rand.nextInt();
                    double expectedDoubleValue5 = (double) expectedIntValue5 / rand.nextInt();

                    long longArray[] = { expectedIntValue1, expectedIntValue2, expectedIntValue3, expectedIntValue4,
                            expectedIntValue5 };
                    double doubleArray[] = { expectedDoubleValue1, expectedDoubleValue2, expectedDoubleValue3,
                            expectedDoubleValue4, expectedDoubleValue5 };

                    setData.appendArray("longArray", longArray);
                    setData.appendArray("doubleArray", doubleArray);

                    accessPoint.set(setData, null);

                    AcquiredData aquiredData = accessPoint.get(null);

                    long longArrayResult[] = aquiredData.getData().getLongArray("longArray");

                    // TODO arraycheck in
                    for (int n = 0; n < 5; n++) {
                        if (longArray[n] != longArrayResult[n]) {
                            System.out.println("ERROR: Long Array Value[" + n + "] was not the same. Expected "
                                    + longArray[n] + " but was " + longArrayResult[n]);
                        }
                    }

                    double doubleArrayResult[] = aquiredData.getData().getDoubleArray("doubleArray");
                    for (int n = 0; n < 5; n++) {
                        if (Math.abs(doubleArray[n] - doubleArrayResult[n]) > 0.00000001) {
                            System.out.println("ERROR: Double Array Value[" + n + "] was not the same. Expected "
                                    + doubleArray[n] + " but was " + doubleArrayResult[n]);
                        }
                    }

                    Thread.sleep(2);
                }
                System.out.println("Test Checkpoint - Array Data Burst " + j + " finished OK");
                Thread.sleep(1000);
            }

            System.out.println("Test Checkpoint - Array Data Test finished OK");

            statisticsData = statAccessPoint.get(null);
            getCounter = statisticsData.getData().getInt("counterGet");
            setCounter = statisticsData.getData().getInt("counterSet");

            if (getCounter != 20000) {
                System.out.println("ERROR: Get counter does not match. Expected 20000 but was " + getCounter);
            }

            if (setCounter != 20000) {
                System.out.println("ERROR: Set counter does not match. Expected 20000 but was " + setCounter);
            }

            System.out.println("Test Checkpoint - Counters are OK");

            Data errorData = DataFactory.createData();
            AccessPoint accessPointForError = client.getAccessPoint(currentTestDeviceName,
                    "TestSettingProperty.EXCEPTION", serverName);
            errorData.append("errorType", "InvalidRequestException");
            accessPointForError.set(errorData, null);

            boolean exceptionOccured = false;
            try {
                accessPoint.get(null);
            } catch (Exception ex) {
                System.out.println("Expected error occured : " + ex.getMessage());
                exceptionOccured = true;
            }

            if (!exceptionOccured) {
                System.out.println("ERROR: Exception is not occured, altough the .ERROR was set");
            }

            accessPointForError = client.getAccessPoint(currentTestDeviceName, "TestSettingProperty.EXCEPTION",
                    serverName);
            errorData.append("errorType", "InvalidRequestException");
            errorData.append("errorMessage", "The answer to life the universe and everything");
            accessPointForError.set(errorData, null);

            exceptionOccured = false;
            try {
                accessPoint.get(null);
            } catch (Exception ex) {
                System.out.println("Expected error occured : " + ex.getMessage());
                exceptionOccured = true;
            }

            if (!exceptionOccured) {
                System.out.println("ERROR: Exception is not occured, altough the .ERROR was set");
            }

            /* Clean the data on the server */
            Data resetData = DataFactory.createData();
            AccessPoint resetAccessPoint = client.getAccessPoint("SERVER", "DEVICE_RESET", serverName);
            resetData.append("deviceName", currentTestDeviceName);
            resetAccessPoint.set(resetData, null);

            System.out.println("Test finished");

        } catch (RdaException ex) {
            ex.printStackTrace();
        } catch (InterruptedException ex) {
            ex.printStackTrace();
        }
        System.exit(0);
    }

}

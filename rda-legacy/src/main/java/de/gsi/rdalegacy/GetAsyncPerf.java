/**
 * Copyright (c) 2014 European Organisation for Nuclear Research (CERN), All Rights Reserved.
 */

package de.gsi.rdalegacy;

import cern.cmw.data.Data;
import cern.cmw.data.DataFactory;
import cern.cmw.directory.client.DirectoryProperties;
import cern.cmw.rda3.client.core.AccessPoint;
import cern.cmw.rda3.client.core.RdaGetFuture;
import cern.cmw.rda3.client.service.ClientService;
import cern.cmw.rda3.client.service.ClientServiceBuilder;
import cern.cmw.rda3.common.exception.RdaException;

import java.util.LinkedList;

public class GetAsyncPerf {

    static {
        System.setProperty(DirectoryProperties.SYSTEM_PROPERTY_CMW_DIRECTORY_ENV, "TEST");
    }

    public static void main(String[] args) throws RdaException {
        String serverName = "CmwTest.RDA3.cfv-ccr-ctb01";
        int deviceCount = 5000;
        int propertyCount = 10;
        int propertySize = 1;
        int nbIteration = 1;

        System.out.println("serverName: " + serverName);
        System.out.println("deviceCount: " + deviceCount);
        System.out.println("propertyCount: " + propertyCount);
        System.out.println("propertySize: " + propertySize + " bytes");
        System.out.println("parameterCount: " + (deviceCount * propertyCount));
        System.out.println("nbIteration: " + nbIteration);
        System.out.println("");

        ClientService client = ClientServiceBuilder.newInstance().build();

        // // initialize the access points
        long before = System.currentTimeMillis();

        Data data = DataFactory.createData();
        data.appendArray("value", new byte[propertySize]);
        for (int iDevice = 0; iDevice < deviceCount; ++iDevice) {
            for (int iProperty = 0; iProperty < propertyCount; ++iProperty) {
                AccessPoint ap = client.getAccessPoint("device" + iDevice, "property" + iProperty, serverName);
                ap.set(data);
            }
        }

        System.out.println("init time: " + (System.currentTimeMillis() - before) + "ms");
        System.out.println();

        for (int i = 0; i < nbIteration; ++i) {
            // perform sync get calls
            before = System.currentTimeMillis();

            int errorCount = 0;
            for (int iDevice = 0; iDevice < deviceCount; ++iDevice) {
                for (int iProperty = 0; iProperty < propertyCount; ++iProperty) {
                    AccessPoint ap = client.getAccessPoint("device" + iDevice, "property" + iProperty, serverName);
                    try {
                        ap.get();
                    } catch (RdaException ex) {
                        ++errorCount;
                    }
                }
            }
            System.out.println("sync get time: " + (System.currentTimeMillis() - before) + "ms (" + errorCount
                    + " errors)");
        }

        System.out.println();

        for (int i = 0; i < nbIteration; ++i) {
            // perform async get calls
            before = System.currentTimeMillis();
            LinkedList<RdaGetFuture> futures = new LinkedList<RdaGetFuture>();

            for (int iDevice = 0; iDevice < deviceCount; ++iDevice) {
                for (int iProperty = 0; iProperty < propertyCount; ++iProperty) {
                    AccessPoint ap = client.getAccessPoint("device" + iDevice, "property" + iProperty, serverName);
                    futures.add(ap.getAsync());
                }
            }

            int errorCount = 0;
            for (RdaGetFuture future : futures) {
                try {
                    future.get();
                } catch (RdaException ex) {
                    ++errorCount;
                }
            }
            System.out.println("async get time: " + (System.currentTimeMillis() - before) + "ms (" + errorCount
                    + " errors)");

            // give some time to recover...
            if (errorCount > 0) {
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    // do nothing
                }
            }
        }

        System.out.println();
        System.out.println("test finished");

        client.close();
    }
}

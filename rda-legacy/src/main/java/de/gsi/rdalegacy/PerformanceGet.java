package de.gsi.rdalegacy;

import cern.accsoft.commons.util.proc.ProcUtils;
import cern.cmw.rda3.client.core.AccessPoint;
import cern.cmw.rda3.client.service.ClientService;
import cern.cmw.rda3.client.service.ClientServiceBuilder;
import cern.cmw.rda3.client.service.NameService;
import cern.cmw.rda3.common.exception.NameServiceException;
import cern.cmw.rda3.common.exception.RdaException;
import cern.cmw.rda3.common.info.RemoteHostInfo;
import cern.cmw.rda3.impl.common.info.RemoteHostInfoImpl;
import cern.cmw.rda3.transport.Address;
import cern.cmw.util.config.Configuration;
import cern.cmw.util.config.ConfigurationBuilder;
import cern.cmw.util.version.Version;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * TODO
 * 
 * @author Ilia Yastrebov
 * @version $Revision$, $Date$, $Author$
 */
public class PerformanceGet {
    
    private static Version VERSION = new Version(PerformanceGet.class);
    
    public static void main(final String[] args) throws IOException {

        String hostName = InetAddress.getLocalHost().getHostName();
        int pid = ProcUtils.getPid();
        String clientId = hostName + ":" + pid;

        FileWriter fstream = new FileWriter(args[0] + clientId + ".txt");
        BufferedWriter out = new BufferedWriter(fstream);
        try {
            final String device = "Device" + new Random().nextInt(100);
            // System.out.println(device);
            out.write(device + "\n");

            ConfigurationBuilder configBuilder = ConfigurationBuilder.newInstance();
            configBuilder.setProperty("cmw.rda3.client.schedulerCoreThreads", "1");
            configBuilder.setProperty("cmw.rda3.client.schedulerMaxThreads", "1");
            Configuration config = configBuilder.build();

            ClientServiceBuilder builder = ClientServiceBuilder.newInstance();
            builder.setConfig(config);
            builder.setNameService(new NameService() {

                @Override
                public String getServerName(String deviceName) throws NameServiceException {
                    if (deviceName.equals(device)) {
                        return "Server.Address";
                    }
                    throw new NameServiceException("Not supported device");
                }

                @Override
                public RemoteHostInfo getServerInfo(String serverName) throws NameServiceException {
                    if (serverName.equals("Server.Address")) {
                        Address address = new Address("tcp", args[1], 2000);
                        return new RemoteHostInfoImpl("Remote server", address, VERSION);
                    }
                    throw new NameServiceException("Not supported server");
                }

                @Override
                public Map<String, String> getServersNames(List<String> devicesNames) throws NameServiceException {
                    Map<String, String>  result = new HashMap<String, String> ();
                    for (String deviceName : devicesNames) {
                        result.put(deviceName, getServerName(deviceName));
                    }
                    return result;
                }
            });
                        
            ClientService client = builder.build();

            // Creates Parameter-Device-Peer + Transport internally
            AccessPoint parameter = client.getAccessPoint(device, "Property");

            for (int i = 0; i < 1000; ++i) {
                long t1 = System.currentTimeMillis();
                for (int j = 0; j < 1000; ++j) {
                    try {
                        parameter.get(null);
                    } catch (RdaException ex) {
                        // System.out.println(ex.getMessage());
                        out.write(ex.getMessage() + "\n");
                    }
                }
                // System.out.println("Time for 1000 gets: " + (System.currentTimeMillis() - t1));
                out.write("Time for 1000 gets: " + (System.currentTimeMillis() - t1) + "\n");
                out.flush();
            }
        } catch (RdaException ex) {
            System.out.println(ex.getMessage());
            out.write(ex.getMessage() + "\n");
        }
        out.close();   
        System.exit(0);
    }

}

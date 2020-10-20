package de.gsi.mircoservice.aggregate.disruptor;

import de.gsi.microservice.aggregate.lsa.FairSelector;
import de.gsi.microservice.aggregate.lsa.LsaRestContextService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LsaSubscriptionSample {
    public static final Logger LOGGER = LoggerFactory.getLogger(LsaSubscriptionSample.class);
    /**
     * Main method to test LSA REST access
     * @param args command line arguments
     */
    public static void main(String[] args) throws InterruptedException {
        final String bpName = ".SIS18_RING.RING_INJECTION.1";
        final String fairSelector = new FairSelector(3, -1, -1, -1).toString();

        // start new context service. Retrieves all patterns and sets up a SSE subscription on changes
        LsaRestContextService lsaMiddleTierAdapter = new LsaRestContextService();
        lsaMiddleTierAdapter.setUpdateCallback(u -> {
           LOGGER.atInfo().addArgument(u).log("received update {}");
          });
        lsaMiddleTierAdapter.reconnect();
        Thread.sleep(2000); // wait for contexts to be available

        // test subscription, will print every change to the setting
        lsaMiddleTierAdapter.subscribeToSetting("SIS18BI/FREV_INJ", (oldVal, newVal) -> LOGGER.atInfo()
                        .addArgument(oldVal).addArgument(newVal).log("FRev Changed from {} to {}"), bpName);

        LOGGER.atInfo().addArgument(lsaMiddleTierAdapter.getAvailableContexts()).log("Available Contexts: \n {}");

        // directly read  double setting
        final double settingVal = lsaMiddleTierAdapter.getSettingDouble("SIS18BI/FREV_INJ", fairSelector, bpName);
        LOGGER.atInfo().addArgument(settingVal).log("Read injection frequency: {} Hz");
        // directly read  int setting
        final int q = lsaMiddleTierAdapter.getSettingInt("SIS18BEAM/Q", fairSelector, bpName);
        LOGGER.atInfo().addArgument(q).log("Read charge state: {} e");
        // read function setting
        final LsaRestContextService.LsaSettingFunction settingValFunction = lsaMiddleTierAdapter.getSettingFunction("SIS18BEAM/ETA", fairSelector, bpName);
        LOGGER.atInfo().addArgument(settingValFunction).log("Read ETA function: {}");
        // read function[] setting
        final LsaRestContextService.LsaSettingFunction[] settingValFunctionArray = lsaMiddleTierAdapter.getSettingFunctionArray("SIS18RF/URFRING", fairSelector, bpName);
        LOGGER.atInfo().addArgument(settingValFunctionArray).log("Read URFRING functions: {}");
        // read boolean[][] setting
        final int[][] settingValBooleanArray2D = lsaMiddleTierAdapter.getSettingIntArray2D("SIS18RF/CAVITY2HARMONIC", fairSelector, bpName);
        LOGGER.atInfo().addArgument(settingValBooleanArray2D).log("Read Cavity to harmonics array: {}");
        // read String[] setting
        final String[] settingValStringArray = lsaMiddleTierAdapter.getSettingStringArray("SIS18RF/CAVITY_NAMES", fairSelector, bpName);
        LOGGER.atInfo().addArgument(settingValStringArray).log("Read Cavity names: {}");
    }
}

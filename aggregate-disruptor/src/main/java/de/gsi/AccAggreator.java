package de.gsi;

import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.EventHandlerGroup;
import com.lmax.disruptor.util.DaemonThreadFactory;
import de.gsi.microservice.cmwlight.CmwLightClient;
import de.gsi.microservice.cmwlight.DirectoryLightClient;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Aggregates Measurements and configuration changes throughout the accelerator complex and allows to subscribe to
 * specific aggregates becoming available.
 * To be integrated with the majordomo openCMW framework for accessing all data and internal state.
 *
 * Uses LMAX disruptor as internal datastorage and processing model.
 * There is one big ring buffer, which accumulates all updates and multiple workers, which process all incoming events,
 * eventually republishing transformed or aggregated events.
 * These events may then be made available as openCMW topics that clients can get or subscribe.
 */
public class AccAggreator {
   private static final String CMW_NAMESERVER = "cmwpro.acc.gsi.de";
   private Map<String, CmwLightClient> cmwClients = new HashMap<>();
   private DirectoryLightClient dirClient = new DirectoryLightClient(CMW_NAMESERVER);
   private static final int N_BUFFER = 1024;
   private RingBuffer ringBuffer;
   final Disruptor<RingEvent> disruptor;

   public AccAggreator() throws DirectoryLightClient.DirectoryClientException {
      disruptor = new Disruptor<>(() -> new RingEvent(), N_BUFFER, DaemonThreadFactory.INSTANCE);
      final EventHandlerGroup<RingEvent> eventBarrier = disruptor.handleEventsWith(new ContextDispatcher());
   }

   public void start() {
      ringBuffer = disruptor.start();
   }

   public void addCmwLightSubscription(final String device, final String property, final String selector) throws CmwLightClient.RdaLightException {
       final CmwLightClient client = cmwClients.computeIfAbsent(device, devicename -> {
          try {
             final String address = dirClient.getDeviceInfo(Collections.singletonList(devicename)).get(0).servers.get(0).get("Address:");
             return new CmwLightClient(device);
          } catch (DirectoryLightClient.DirectoryClientException e) {
             e.printStackTrace();
             return null;
          }
       });
      client.connect();
      client.subscribe(device, property, selector);
   }

   public void addLsaSettingSubscription(final String setting) {

   }

   public void addPublisher(final String topic, final Predicate<Object> filter) {
      if (ringBuffer == null) { // disruptor not started yet
         disruptor.handleEventsWith( (ringEvent, id, last) -> {

         });

      } else { // disruptor running

      }
   }

   public static class RingEvent {

   }
}

package de.gsi.microservice.cmwlight;

import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.util.DaemonThreadFactory;

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
   private static final int N_BUFFER = 1024;
   private RingBuffer ringBuffer;
   final Disruptor<RingEvent> disruptor;

   public AccAggreator() {
      disruptor = new Disruptor<>(() -> new RingEvent(), N_BUFFER, DaemonThreadFactory.INSTANCE);
   }

   public void start() {
      ringBuffer = disruptor.start();
   }

   public void addCmwLightSubscription(final String device, final String property, final String selector) {
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

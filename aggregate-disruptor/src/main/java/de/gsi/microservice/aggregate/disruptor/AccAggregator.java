package de.gsi.microservice.aggregate.disruptor;

import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.util.DaemonThreadFactory;
import de.gsi.microservice.aggregate.lsa.LsaRestContextService;
import de.gsi.microservice.cmwlight.CmwLightClient;
import de.gsi.microservice.cmwlight.DirectoryLightClient;
import de.gsi.serializer.IoClassSerialiser;
import de.gsi.serializer.spi.CmwLightSerialiser;
import de.gsi.serializer.spi.FastByteBuffer;
import de.gsi.serializer.spi.WireDataFieldDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Aggregates Measurements and configuration changes throughout the accelerator complex and allows to subscribe to
 * specific aggregates becoming available.
 * To be integrated with the majordomo openCMW framework for accessing all data and internal state.
 *
 * Uses LMAX disruptor as internal datastorage and processing model.
 * There is one big ring buffer, which accumulates all updates and multiple workers, which process all incoming events,
 * eventually republishing transformed or aggregated events.
 * These events may then be made available as openCMW topics that clients can get or subscribe.
 *
 * Event Store + LSA Resident Context Cache
 */
public class AccAggregator {
   private static final Logger LOGGER = LoggerFactory.getLogger(AccAggregator.class);
   private static final String CMW_NAMESERVER = "cmwpro00a.acc.gsi.de:5021";
   private Map<String, CmwLightClient> cmwClients = new HashMap<>();
   private final DirectoryLightClient dirClient = new DirectoryLightClient(CMW_NAMESERVER);
   private LsaRestContextService lsaService;
   private static final int N_BUFFER = 1024;
   private RingBuffer<RingEvent> inputBuffer;
   private Map<String,RingBuffer<RingEvent>> contextBuffers = new HashMap<>();
   final Disruptor<RingEvent> disruptor;

   public AccAggregator() throws DirectoryLightClient.DirectoryClientException {
      disruptor = new Disruptor<>(() -> new RingEvent(), N_BUFFER, DaemonThreadFactory.INSTANCE);
      //final EventHandlerGroup<RingEvent> eventBarrier = disruptor.handleEventsWith(new ContextDispatcher());

   }

   public void start() {
      inputBuffer = disruptor.start();
   }

   public <T> void addCmwLightSubscription(final String device, final String property, final String selector, final Class<T> clazz) throws CmwLightClient.RdaLightException {
      addCmwLightSubscription(device, property, selector, null, clazz);
   }

   public <T> void addCmwLightSubscription(final String device, final String property, final String selector, final Map<String, Object> filters, final Class<T> clazz) throws CmwLightClient.RdaLightException {
      new Thread(() -> {
         final IoClassSerialiser classSerialiser = new IoClassSerialiser(FastByteBuffer.wrap(new byte[0]), CmwLightSerialiser.class);
         final CmwLightClient client;
         try {
            final String address = dirClient.getDeviceInfo(Collections.singletonList(device)).get(0).servers.get(0).get("Address:");
            client = new CmwLightClient(address);
            client.connect();
            client.subscribe(device, property, selector, filters);
         } catch (CmwLightClient.RdaLightException | DirectoryLightClient.DirectoryClientException e) {
            e.printStackTrace();
            return;
         }
         while (!Thread.interrupted()) {
            try {
               final CmwLightClient.Reply data = client.receiveData();
               if (data instanceof CmwLightClient.DataReply) {
                  inputBuffer.publishEvent((ev, seq, last) -> {
                     final CmwUpdate<T> update = new CmwUpdate<T>();
                     update.content = deserialiseProperty(classSerialiser, ((CmwLightClient.DataReply) data).dataBody.getData(), clazz);
                     update.selector = getSelector(classSerialiser, ((CmwLightClient.DataReply) data).dataContext.getData());
                     update.header = getHeader(classSerialiser, data.header.getData());
                     ev.data = update;
                  });
               } else {
                  client.sendHeartBeat();
               }
            } catch (CmwLightClient.RdaLightException e) {
               e.printStackTrace();
            }
         }
      }, "cmwSubscription" + device).start();
   }

   private String getHeader(final IoClassSerialiser classSerialiser, final byte[] data) {
      classSerialiser.setDataBuffer(FastByteBuffer.wrap(data));
      System.out.println(new String(classSerialiser.getDataBuffer().elements()));
      final WireDataFieldDescription description = classSerialiser.parseWireFormat();
      return description.toString();
   }

   private String getSelector(final IoClassSerialiser classSerialiser, final byte[] dataContext) {
      classSerialiser.setDataBuffer(FastByteBuffer.wrap(dataContext));
      final WireDataFieldDescription description = classSerialiser.parseWireFormat();
      return description.toString();
   }

   private <T> T deserialiseProperty(final IoClassSerialiser classSerialiser, final byte[] data, final Class<T> clazz) {
      classSerialiser.setDataBuffer(FastByteBuffer.wrap(data));
      return classSerialiser.deserialiseObject(clazz);
   }

   public void addLsaSettingSubscription(final String setting) {
   }

   public void startLsaUpdateSubscription() {
      lsaService = new LsaRestContextService();
      lsaService.setUpdateCallback(u -> {
         inputBuffer.publishEvent((ev, seq, last) -> {
            final LsaUpdate update = new LsaUpdate();
            update.trims = u.residentPatterns.entrySet().stream().map(Map.Entry::toString).collect(Collectors.joining(", ", "{", "}"));
            ev.data = update;
         });
      });
      lsaService.reconnect();
   }

   public void addPublisher(final String topic, final Predicate<Object> filter) {
      if (inputBuffer == null) { // disruptor not started yet
         disruptor.handleEventsWith( (ringEvent, id, last) -> {

         });

      } else { // disruptor running

      }
   }

   public void addProcessor(final EventHandler<RingEvent> handler) {
      disruptor.handleEventsWith(handler);
   }

   public static class RingEvent {
      public Object data;
       public int chain = -1;

      public void set(final RingEvent that) {
         this.data = that.data;
         this.chain = that.chain;
      }
   }

   public static class CmwUpdate<T> {
      public String header; // replace by important header fields?
      public String selector;
      public  T content;

      @Override
      public String toString() {
         return "CmwUpdate{" +
                 "header=" + header +
                 ", selector='" + selector + '\'' +
                 ", content=" + content +
                 '}';
      }
   }

   public static class CmwHeader {

   }

   public static class LsaUpdate {
      public String trims;
   }
}

package de.gsi.dataset.event.queue;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lmax.disruptor.AlertException;
import com.lmax.disruptor.BatchEventProcessor;
import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.Sequence;
import com.lmax.disruptor.SequenceBarrier;
import com.lmax.disruptor.SleepingWaitStrategy;
import com.lmax.disruptor.TimeoutException;
import com.lmax.disruptor.WaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.lmax.disruptor.util.DaemonThreadFactory;
import com.netflix.spectator.atlas.AtlasConfig;

import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Timer.Sample;
import io.micrometer.atlas.AtlasMeterRegistry;
import io.micrometer.core.instrument.Counter;
import de.gsi.dataset.event.AxisRangeChangeEvent;
import de.gsi.dataset.event.AxisRecomputationEvent;
import de.gsi.dataset.event.EventListener;
import de.gsi.dataset.event.EventSource;
import de.gsi.dataset.event.EventThreadHelper;
import de.gsi.dataset.event.UpdateEvent;

/**
 * Global Event Queue implemented as a circular buffer.
 * Accepts update events and allows to get the backlog of unprocessed events.
 *
 * @author Alexander Krimm
 */
public class EventQueue {
    private static final Logger LOGGER = LoggerFactory.getLogger(EventQueue.class);
    private static final int QUEUE_SIZE = 512; // must be power of 2
    private static EventQueue instance = null;
    Counter eventCount = Metrics.counter("chartfx.event.count", "event", "all"); // micrometer event counter

    private final RingBuffer<RingEvent> queue; // the ring buffer storing all events
    private List<EventQueueListener> listeners = Collections.synchronizedList(new ArrayList<>());

    public static EventQueue getInstance() {
        if (instance == null) {
            instance = new EventQueue(QUEUE_SIZE);
        }
        return instance;
    }

    public static EventQueue getInstance(int size) {
        if (instance == null) {
            instance = new EventQueue(size);
        }
        return instance;
    }

    /**
     * @param size Number of events to be saved in the event queue
     */
    private EventQueue(final int size) {
        Disruptor<RingEvent> disruptor = new Disruptor<>(RingEvent::new, // used to fill the buffer with blank events
                size, // size of the ring buffer
                DaemonThreadFactory.INSTANCE, // ThreadFactory
                ProducerType.MULTI, // Allow multiple threads to insert new events
                new BlockingWaitStrategy()
        //new SleepingWaitStrategy() // how the consumers will wait for new work
        );
        

        queue = disruptor.getRingBuffer();
        // TODO: use more than one processing thread?
        disruptor.handleEventsWith(new BatchEventProcessor<>(queue, queue.newBarrier(), this::handle));
        disruptor.start();
        waiter = new BatchEventProcessor<>(queue, queue.newBarrier(), (evt, id, lastOfBatch) -> {
            if (evt.isAck && evt.evt == waitEvent.get()) {
                waiterRef.get().halt();
            }
        });
    }

    public void submitEvent(final UpdateEvent event) {
        queue.publishEvent((evnt, id, updateEvent) -> {
            evnt.set(id, updateEvent, false);
            evnt.setSubmitTime();
        }, event);
        eventCount.increment();
    }

    /**
     * @param event the original event which to ack
     */
    public void submitEventAck(UpdateEvent event) {
        queue.publishEvent((evnt, id, updateEvent) -> {
            evnt.set(id, updateEvent, true);
            evnt.setSubmitTime();
        }, event);
    }

    public void handle(final RingEvent evt, final long evtId, final boolean endOfBatch) {
        if (evt.isAck) {
            return; // skip ACK events
        }
        final List<EventQueueListener> listenersLocal;
        synchronized (listeners) {
            listenersLocal = new ArrayList<>(listeners);
        }

        final List<Future<?>> jobs = new ArrayList<>(listenersLocal.size());
        for (final EventQueueListener listener : listenersLocal) {
            Future<?> future = EventThreadHelper.getExecutorService().submit(() -> listener.handle(evt, evtId, endOfBatch));
            jobs.add(future);
        }
        try {
            // wait for submitted tasks to complete
            for (final Future<?> future : jobs) {
                future.get();
            }
        } catch (final Exception e) { // NOPMD -- necessary since these are forwarded
        }
    }

    UpdateEvent getUpdate(long eventId) {
        return queue.get(eventId).getEvent();
    }

    /**
     * Adds a listener on the common listener thread pool.
     * 
     * @param listener event listener which gets called with the update
     */
    public void addListener(final EventQueueListener listener) {
        listeners.add(listener);
    }

    /**
     * @param source The event source this listeners events should be filtered for
     * @param listener the event listener to be called for all events
     * @param name Name for the listener
     */
    public void addListener(final EventSource source, final EventListener listener, final String name) {
        final EventQueueListener eql = new EventQueueListener(queue, listener, UpdateEvent.class, source, null, name);
        addListener(eql);
    }

    /**
     * @return the last event which was added to the queue
     */
    public long getLastEvent() {
        return queue.getCursor();
    }

    /**
     * @return The disruptor ring buffer
     */
    public RingBuffer<RingEvent> getQueue() {
        return queue;
    }

    

    final AtomicReference<UpdateEvent> waitEvent = new AtomicReference<>();
    final AtomicReference<BatchEventProcessor<RingEvent>> waiterRef = new AtomicReference<>();
    final BatchEventProcessor<RingEvent> waiter;
    
    /**
     * Submits the event and waits for its processing to be acknowledged
     * 
     * @param event the Event to be submitted to the event queue
     */
    public void submitEventAndWait(final UpdateEvent event) {
        waiterRef.set(waiter);
        waitEvent.set(event);
        waiter.getSequence().set(queue.getCursor());
        submitEvent(event);
        waiter.run();
    }

    /**
     * @param listener the listener to remove
     */
    public void removeListener(EventListener listener) {
        LOGGER.atWarn().addArgument(listener).log("Removing Listeners not implemented yet, cannot remove listener: {}");
    }

    /**
     * Event wrapper class containing event id and the original update event
     */
    public static class RingEvent {
        private long id; // id of the event
        UpdateEvent evt; // the actual event
        private Sample start; // micrometer start timestamp for the submission time of this event, set by ring buffer
        private boolean isAck;

        /**
         * @param id Sequence id of the event
         * @param evt actual update event
         */
        public RingEvent(final long id, final UpdateEvent evt) {
            this.id = id;
            this.evt = evt;
        }

        /**
         * Empty constructor
         */
        public RingEvent() {
            this.id = 0;
            this.evt = null;
            this.isAck = false;
        }

        /**
         * @return the wrapped UpdateEvent
         */
        public UpdateEvent getEvent() {
            return evt;
        }

        /**
         * @return the id of the event
         */
        public long getId() {
            return id;
        }

        /**
         * @param id Id of the event
         * @param event wrapped UpdateEvent
         * @param isAck whether the event is an actual event or the acknowledgement of an event handler
         * @return itself
         */
        public RingEvent set(long id, UpdateEvent event, boolean isAck) {
            this.id = id;
            this.evt = event;
            this.isAck = isAck;
            return this;
        }

        /**
         * Sets the event start timestamp to the current time
         */
        public void setSubmitTime() {
            start = Timer.start();
        }

        /**
         * @return the timestamp of the submission of the event
         */
        public Sample getSubmitTimestamp() {
            return start;
        }
    }

    /**
     * Publish some events to a test event source and have some dummy listeners print them to stderr
     * 
     * @param args CLI Arguments
     * @throws InterruptedException When the thread is interrupted
     */
    public static void main(String[] args) throws InterruptedException {
        // setup micrometer
        Metrics.addRegistry(new AtlasMeterRegistry(new AtlasConfig() {
            @Override
            public Duration step() {
                return Duration.ofSeconds(10);
            }

            @Override
            public String get(String k) {
                return null;
            }
        }));
        // setup test Queue
        EventQueue test = EventQueue.getInstance();
        // dummy event source for emitting events to the ring buffer
        EventSource source = new EventSource() {
            @Override
            public List<EventListener> updateEventListener() {
                return Collections.emptyList();
            }

            @Override
            public AtomicBoolean autoNotification() {
                return null;
            }
        };
        // add listener which listens to all events and publishes summaries about the encountered event types
        final EventQueueListener eql = new EventQueueListener( //
                test.queue, // ring buffer
                new MultipleEventListener() {
                    HashMap<Class<? extends UpdateEvent>, Integer> updates = new HashMap<>();

                    @Override
                    public void handle(UpdateEvent event) {
                        if (event != null) {
                            updates.put(event.getClass(), updates.getOrDefault(event.getClass(), 0) + 1);
                        }
                        if (!updates.isEmpty()) {
                            System.err.println(updates);
                            updates.clear();
                        }
                    }

                    @Override
                    public void aggregate(UpdateEvent event) {
                        updates.put(event.getClass(), updates.getOrDefault(event.getClass(), 0) + 1);
                    }
                }, UpdateEvent.class, // EventType
                null, // event source
                e -> true, // filter
                "EventPrintListener");
        test.addListener(eql);
        // add listener which listens to AxisRecomputationEvents and emits AxisRangeChangeEvents
        final EventQueueListener eql2 = new EventQueueListener( //
                test.queue, // ring buffer
                event -> {
                    System.err.println(event);
                    test.submitEvent(new AxisRangeChangeEvent(source, 3, event));
                }, // listener
                AxisRecomputationEvent.class, // EventType
                source, // event source
                e -> true, // filter
                "AxisRecomputationListener");
        test.addListener(eql2);

        // submit some test events
        while (true) {
            for (int i = 0; i < 2; i++) {
                test.submitEvent(new UpdateEvent(source));
            }
            // send an update and wait for its child to be published
            UpdateEvent toWaitForEvent = new AxisRecomputationEvent(source, 3);
            test.submitEventAndWait(toWaitForEvent);
            System.out.println("->" + test.getQueue().getMinimumGatingSequence() + " ... " + test.getQueue().getCursor());
            System.err.println("Wait event acknowledged");
            for (int i = 0; i < 10; i++) {
                test.submitEvent(new UpdateEvent(source));
            }
            Thread.sleep(200); // sleep to let other threads finish working their backlog
        }
    }
}

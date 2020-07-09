package de.gsi.dataset.event.queue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lmax.disruptor.BatchEventProcessor;
import com.lmax.disruptor.EventTranslatorOneArg;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.SleepingWaitStrategy;
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
                new SleepingWaitStrategy() // how the consumers will wait for new work
        );

        queue = disruptor.getRingBuffer();
        // TODO: use more than one processing thread?
        disruptor.handleEventsWith(new BatchEventProcessor<>(queue, queue.newBarrier(), this::handle));
        disruptor.start();
    }

    public long submitEvent(final UpdateEvent event) {
        return submitEvent(event, -1);
    }

    public long submitEvent(final UpdateEvent event, long parent) {
        final long current = publishEvent((evnt, id, updateEvent) -> {
            evnt.set(id, updateEvent, parent);
            evnt.setSubmitTime();
        }, event);
        eventCount.increment();
        return current;
    }

    public void handle(final RingEvent evt, final long evtId, final boolean endOfBatch) {
        List<EventQueueListener> listenersLocal;
        synchronized (listeners) {
            listenersLocal = new ArrayList<>(listeners);
        }
        for (final EventQueueListener listener : listenersLocal) {
            listener.handle(evt, evtId, endOfBatch);
        }
    }

    /**
     * @param translator translator which fills the event
     * @param argument additional argument
     */
    private <A> long publishEvent(EventTranslatorOneArg<RingEvent, A> translator, A argument) {
        final long sequence = queue.next();
        try {
            translator.translateTo(queue.get(sequence), sequence, argument);
        } finally {
            queue.publish(sequence);
        }
        return sequence;
    }

    UpdateEvent getUpdate(long eventId) {
        return queue.get(eventId).getEvent();
    }

    /**
     * Waits for a follow up event to be added to the event queue.
     * This is useful e.g. for events which trigger recomputations.
     * As this method blocks the current thread, it should be used with care and only sparingly.
     * 
     * @param parent event id which is supposed to trigger a new event.
     */
    public void waitForEvent(UpdateEvent parent) {
        final EventQueueListener eql = new EventQueueListener( //
                queue, // ring buffer
                null, // listener
                UpdateEvent.class, // EventType
                null, // event source
                e -> e.getEvent().getParent() == parent, "waitForProcessed"); // filter
        final AtomicBoolean blocked = new AtomicBoolean(true);
        eql.setListener(evt -> {
            listeners.remove(eql);
            blocked.set(false);
        });
        listeners.add(eql);
        while (!blocked.get()) {
            Thread.onSpinWait();
        }
    }

    /**
     * Waits for a follow up event to be added to the event queue.
     * This is useful e.g. for events which trigger recomputations.
     * Also specifies a timeout after which it will return with a false return value.
     * e.g.
     * 
     * <pre>
     * {@code
     * long evntId = eventQueue.submitEvent(new RecomputeLimitsEvent(dimIndex))
     * if (eventQueue.waitForEvent(evntId, 100) {
     *     min = getMin();
     *     max = getMax();
     *     rescaleAxis(dimIndex, min, max);
     * } else {
     *     LOGGER.atWarn().log("Could not recompute axes");
     * }
     * }
     * </pre>
     * 
     * @param parent event id which is supposed to trigger a new event.
     * @param timeout time in ms after which to return false when the event is not triggered
     * @return true if the event occured within timeout, false otherwise
     */
    public boolean waitForEvent(final UpdateEvent parent, final int timeout) {
        LOGGER.atWarn().log("Timeout not yet implemented, ignoring");
        waitForEvent(parent);
        return true;
    }

    /**
     * Adds a listener on the common listener thread pool.
     * 
     * @param listener event listener which gets called with the update
     */
    public void addListener(EventQueueListener listener) {
        listeners.add(listener);
    }

    /**
     * @return the last event which was added to the queue
     */
    public long getLastEvent() {
        return queue.getCursor();
    }

    /**
     * Event wrapper class containing event id and the original update event
     */
    public static class RingEvent {
        private long id; // id of the event
        UpdateEvent evt; // the actual event
        private Sample start; // micrometer start timestamp for the submission time of this event, set by ring buffer
        private long parent;

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
         * @return the id of the parent event
         */
        public long getParent() {
            return parent;
        }

        /**
         * @param id Id of the event
         * @param event wrapped UpdateEvent
         * @param parent parent of the event
         * @return itself
         */
        public RingEvent set(long id, UpdateEvent event, final long parent) {
            this.id = id;
            this.evt = event;
            this.parent = parent;
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
            long waitForId = test.submitEvent(toWaitForEvent);
            System.err.println("Wait event sent: " + waitForId);
            System.out.println("->" + test.getQueue().getMinimumGatingSequence() + " ... " + test.getQueue().getCursor());
            test.waitForEvent(toWaitForEvent);
            System.err.println("Wait event acknowledged");
            for (int i = 0; i < 10; i++) {
                test.submitEvent(new UpdateEvent(source));
            }
            Thread.sleep(200); // sleep to let other threads finish working their backlog
        }
    }

    /**
     * @return The disruptor ring buffer
     */
    public RingBuffer<RingEvent> getQueue() {
        return queue;
    }

    /**
     * Submits the event and waits for its processing to be acknowledged
     * @param event the Event to be submitted to the event queue
     * @return the event id of the published event
     */
    public long submitEventAndWait(final UpdateEvent event) {
        final EventQueueListener eql = new EventQueueListener( //
                queue, // ring buffer
                null, // listener
                UpdateEvent.class, // EventType
                null, // event source
                e -> e.getEvent().getParent() == event, "waitForProcessed"); // filter
        final AtomicBoolean blocked = new AtomicBoolean(true);
        eql.setListener(evt -> {
            listeners.remove(eql);
            blocked.set(false);
        });
        listeners.add(eql);
        long result = submitEvent(event);
        while (blocked.get()) {
            Thread.onSpinWait();
        }
        return result;
    }
}

package de.gsi.dataset.event.queue;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import de.gsi.dataset.event.queue.EventQueueListener.EventQueueListenerStrategy;

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
    Counter eventCount = Metrics.counter("chartfx.event.count", "event", "all"); // micronaut event counter

    private final RingBuffer<RingEvent> queue; // the ring buffer storing all events
    // maps caching the last event id of different kinds of updates to allow consumers to skip lots of irrelevant updates
    // TODO: check if this is necessary or if all listeners should better just iterate over all events
    // private final Map<Object, Long> sourceUpdateMap = Collections.synchronizedMap(new HashMap<>());
    // private final Map<Class<? extends UpdateEvent>, Long> eventClassUpdateMap = Collections.synchronizedMap(new HashMap<>());

    public static EventQueue getInstance() {
        if (instance == null) {
            instance = new EventQueue(QUEUE_SIZE);
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
        disruptor.start();
    }

    long submitEvent(final UpdateEvent event) {
        return submitEvent(event, -1);
    }

    long submitEvent(final UpdateEvent event, long parent) {
        final long current = publishEvent((evnt, id, updateEvent) -> {
            evnt.set(id, updateEvent, parent);
            evnt.setSubmitTime();
        }, event);
        // eventClassUpdateMap.put(event.getClass(), current);
        // sourceUpdateMap.put(event.getSource(), current);
        eventCount.increment();
        return current;
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
     * 
     * @param parent event id which is supposed to trigger a new event.
     */
    public void waitForEvent(long parent) {
        EventSource source = (EventSource) queue.get(parent).evt.getSource();
        final EventQueueListener eql = new EventQueueListener( //
                queue, // ring buffer
                (event, id) -> {}, // listener
                EventQueueListenerStrategy.M_EVERY, // strategy
                UpdateEvent.class, // EventType
                null, // event source
                e -> e.getParent() == parent); // filter
        eql.setListener((evt, id) -> eql.halt());
        eql.run(); // run listener on this thread and block it until the event occured
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
    public boolean waitForEvent(final long parent, final int timeout) {
        LOGGER.atWarn().log("Timeout not yet implemented, ignoring");
        waitForEvent(parent);
        return true;
    }

    /**
     * Adds a listener on the common listener thread pool.
     * 
     * @param filter Filter which updates to consider
     * @param listener event listener which gets called with the update
     */
    public void addListener(Predicate<RingEvent> filter, EventListener listener) {
        final EventQueueListener eql = new EventQueueListener( //
                queue, // ring buffer
                listener, // listener
                EventQueueListenerStrategy.M_LAST_DROP, // strategy
                UpdateEvent.class, // EventType
                null, // event source
                filter); // filter
        eql.execute();
    }

    /**
     * @return the last event which was added to the queue
     */
    public long getLastEvent() {
        return queue.getCursor();
    }

    //    /**
    //     * Return the last event which matches eventClass
    //     * 
    //     * @param eventClass the class of the event which matches.
    //     * @return the last matching event's event id
    //     */
    //    public long getLastEvent(Class<UpdateEvent> eventClass) {
    //        return eventClassUpdateMap.get(eventClass);
    //    }
    //
    //    /**
    //     * Return the last event which matches source
    //     * 
    //     * @param source the source event emitting the event
    //     * @return the last matching event's event id
    //     */
    //    public long getLastEvent(Object source) {
    //        return sourceUpdateMap.get(source);
    //    }

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
        // add listener
        final EventQueueListener eql = new EventQueueListener( //
                test.queue, // ring buffer
                (event, id) -> System.err.println(id + " - " + event), // listener
                EventQueueListenerStrategy.M_LAST_DROP, // strategy
                UpdateEvent.class, // EventType
                source, // event source
                e -> !(e.evt instanceof AxisRecomputationEvent)); // filter
        eql.execute();
        final EventQueueListener eql2 = new EventQueueListener( //
                test.queue, // ring buffer
                (event, id) -> {
                    System.err.println(id + " = " + event);
                    test.submitEvent(new AxisRangeChangeEvent(source, 3), id);
                }, // listener
                EventQueueListenerStrategy.M_EVERY, // strategy
                AxisRecomputationEvent.class, // EventType
                source, // event source
                e -> true); // filter
        eql2.execute();

        // submit some test events
        while (true) {
            for (int i = 0; i < 2; i++) {
                test.submitEvent(new UpdateEvent(source));
            }
            // send an update and wait for its child to be published
            UpdateEvent toWaitForEvent = new AxisRecomputationEvent(source, 3);
            long waitForId = test.submitEvent(toWaitForEvent);
            System.err.println("Wait event sent: " + waitForId);
            test.waitForEvent(waitForId);
            System.err.println("Wait event acknowledged");
            for (int i = 0; i < 30; i++) {
                test.submitEvent(new UpdateEvent(source));
            }
            Thread.sleep(2000); // sleep to let other threads finish working their backlog
        }
    }
}

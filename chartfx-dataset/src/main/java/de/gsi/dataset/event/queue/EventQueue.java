package de.gsi.dataset.event.queue;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lmax.disruptor.BatchEventProcessor;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.EventTranslatorOneArg;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.SequenceBarrier;
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
import de.gsi.dataset.event.AxisRecomputationEvent;
import de.gsi.dataset.event.EventListener;
import de.gsi.dataset.event.EventSource;
import de.gsi.dataset.event.EventThreadHelper;
import de.gsi.dataset.event.UpdateEvent;
import de.gsi.dataset.event.queue.EventQueueListener.EventQueueListenerStrategy;

/**
 * Global Event Queue implemented as a circular buffer.
 * Accepts update events and allows to get the backlog of unprocessed events.
 * TODO:
 * - add timing information to events and micrometer instrumentation
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
    private final Map<Object, Long> sourceUpdateMap = Collections.synchronizedMap(new HashMap<>());
    private final Map<Class<? extends UpdateEvent>, Long> eventClassUpdateMap = Collections.synchronizedMap(new HashMap<>());

    // the thread pool running all the listener queues
    // private ExecutorService executor = EventThreadHelper.getExecutorService();
    ScheduledExecutorService executor = Executors.newScheduledThreadPool(EventThreadHelper.getMaxThreads());
    private EventQueueListener[] listeners = new EventQueueListener[100];

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
        final long current = publishEvent((evnt, id, updateEvent) -> {
            evnt.set(id, updateEvent);
            evnt.setSubmitTime();
        }, event);
        eventClassUpdateMap.put(event.getClass(), current);
        sourceUpdateMap.put(event.getSource(), current);
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
        // TODO: implement
        throw new UnsupportedOperationException("Not implemented yet");
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
        // TODO: implement
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /**
     * Adds a listener on the common listener thread pool.
     * 
     * @param filter Filter which updates to consider
     * @param listener event listener which gets called with the update
     * @param updateRate how often to recheck for new events
     */
    public void addListener(Predicate<RingEvent> filter, EventListener listener, int updateRate) {
        // TODO: implement
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /**
     * Call a listener with all events which where emitted since the last invocation.
     * This allows the listener to aggregate the events himself, eg calculate a sum or apply each update but only trigger
     * new events on the last update.
     * 
     * @param filter Filter which updates to consider
     * @param listener event listener which gets called with the update
     * @param updateRate how often to recheck for new events
     */
    public void addListener(Predicate<RingEvent> filter, MultipleEventListener listener, int updateRate) {

    }

    /**
     * @return the last event which was added to the queue
     */
    public long getLastEvent() {
        return queue.getCursor();
    }

    /**
     * Return the last event which matches eventClass
     * 
     * @param eventClass the class of the event which matches.
     * @return the last matching event's event id
     */
    public long getLastEvent(Class<UpdateEvent> eventClass) {
        return eventClassUpdateMap.get(eventClass);
    }

    /**
     * Return the last event which matches source
     * 
     * @param source the source event emitting the event
     * @return the last matching event's event id
     */
    public long getLastEvent(Object source) {
        return sourceUpdateMap.get(source);
    }

    /**
     * Event wrapper class containing event id and the original update event
     */
    public static class RingEvent {
        private long id;
        UpdateEvent evt;
        public Sample start;

        /**
         * @param id Sequence id of the event
         * @param evt actual update event
         */
        public RingEvent(final long id, final UpdateEvent evt) {
            this.id = id;
            this.evt = evt;
        }

        /**
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

        public long getId() {
            return id;
        }

        /**
         * @param id Id of the event
         * @param event wrapped UpdateEvent
         * @return itself
         */
        public RingEvent set(long id, UpdateEvent event) {
            this.id = id;
            this.evt = event;
            return this;
        }

        /**
         * 
         */
        public void setSubmitTime() {
            start = Timer.start();
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
        final EventQueueListener eql = new EventQueueListener(test.queue, 
                event -> System.out.println(event),
                EventQueueListenerStrategy.M_LAST_DROP,
                AxisRecomputationEvent.class,
                source,
                e -> true);
        final EventQueueListener eql2 = new EventQueueListener(test.queue, 
                event -> System.out.println(event),
                EventQueueListenerStrategy.M_LAST_DROP,
                UpdateEvent.class,
                source,
                e -> !(e instanceof AxisRecomputationEvent));

        // submit some test events
        while (true) {
            for (int i = 1; i < 200; i++) {
                test.submitEvent(new UpdateEvent(source));
            }
            for (int i = 1; i < 10; i++) {
                test.submitEvent(new AxisRecomputationEvent(source, 3));
            }
            for (int i = 1; i < 300; i++) {
                test.submitEvent(new UpdateEvent(source));
            }
            
            Thread.sleep(2000); // sleep to let other threads finish working their backlog
        }
    }
}

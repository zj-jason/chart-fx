package de.gsi.dataset.event.queue;

import java.lang.ref.WeakReference;
import java.util.function.Predicate;

import com.lmax.disruptor.RingBuffer;

import de.gsi.dataset.event.EventListener;
import de.gsi.dataset.event.EventSource;
import de.gsi.dataset.event.UpdateEvent;
import de.gsi.dataset.event.queue.EventQueue.RingEvent;
import io.micrometer.core.instrument.Metrics;

/**
 * Event Queue Listener gets periodically called and retrieves the new events since its last invocation from the event
 * queue.
 * Depending on its strategy it calls its callback function for events.
 * There is a ThreadPool which cycles over all EventQueueListeners and executes them.
 *
 * @author Alexander Krimm
 */
public class EventQueueListener {
    //    public class EventQueueListener implements Runnable {
    private EventListener listener; // the listener to process the events
    private WeakReference<EventSource> filterSource; // the source to listen to
    private Class<? extends UpdateEvent> filterEventType; // the event type to listen to
    private Predicate<RingEvent> filterPredicate; // a filter for selecting matching events
    private final String listenerName;
    // private BatchEventProcessor<RingEvent> processor;

    public EventQueueListener(final RingBuffer<RingEvent> queue, final EventListener listener, Class<? extends UpdateEvent> eventType, EventSource source,
            Predicate<RingEvent> filter, final String listenerName) {
        this.listener = listener;
        this.filterSource = new WeakReference<>(source);
        this.filterEventType = eventType;
        this.filterPredicate = filter;
        this.listenerName = listenerName;

        // processor = new BatchEventProcessor<>(queue, queue.newBarrier(), this::handle);
        // processor.getSequence().set(Math.max(-1, queue.getCursor() - (queue.getBufferSize() >> 1))); // half of the buffer size as initial backlog
    }

    /**
     * @param evt The RingEvent which occurred
     * @param evtId The id of the event in the buffer
     * @param endOfBatch whether the event was the last in a series of processed events.
     */
    public void handle(final RingEvent evt, final long evtId, final boolean endOfBatch) {
        if (listener == null //
                || (filterSource.get() != null && filterSource.get() != evt.evt.getSource()) //
                || (filterEventType != null && !filterEventType.isAssignableFrom(evt.evt.getClass())) //
                || (filterPredicate != null && !filterPredicate.test(evt))) { //
            if (listener instanceof MultipleEventListener && endOfBatch) {
                this.listener.handle(null);
            }
            return;
        }
        // measure latency as time between adding event to the buffer and staring of the listener
        evt.getSubmitTimestamp().stop(Metrics.timer("chartfx.events.latency", "eventType", evt.evt.getClass().getSimpleName(), "listener", listenerName));
        if (this.listener instanceof MultipleEventListener && !endOfBatch) {
            ((MultipleEventListener) this.listener).aggregate(evt.evt);
        } else {
            this.listener.handle(evt.evt);
        }
        // record the time from event creation to listener executed
        evt.getSubmitTimestamp().stop(Metrics.timer("chartfx.events.latency.post", "eventType", evt.evt.getClass().getSimpleName(), "listener", listenerName));
    }

    /**
     * @param listener new listener to be executed on new events
     */
    public void setListener(EventListener listener) {
        this.listener = listener;
    }

    //    /**
    //     * Runs the Event Processor on the default executor service.
    //     */
    //    public void execute() {
    //        EventThreadHelper.getExecutorService().execute(this);
    //    }
    //
    //    /**
    //     * Runs the event processor on the current thread, blocking it until {@link #halt()} is called.
    //     */
    //    @Override
    //    public void run() {
    //        processor.run();
    //    }
    //
    //    /**
    //     * Stops execution of the event processor. If it was started on the executor service, it's thread exits, otherwise the
    //     * {@link #run()} call will return to the caller.
    //     */
    //    public void halt() {
    //        processor.halt();
    //    }
}

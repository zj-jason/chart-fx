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
    private EventListener listener; // the listener to process the events
    private WeakReference<EventSource> filterSourceRef; // the source to listen to
    private Class<? extends UpdateEvent> filterEventType; // the event type to listen to
    private Predicate<RingEvent> filterPredicate; // a filter for selecting matching events
    private final String listenerName;

    public EventQueueListener(final RingBuffer<RingEvent> queue, final EventListener listener, Class<? extends UpdateEvent> eventType, EventSource source,
            Predicate<RingEvent> filter, final String listenerName) {
        this.listener = listener;
        this.filterSourceRef = new WeakReference<>(source);
        this.filterEventType = eventType;
        this.filterPredicate = filter;
        this.listenerName = listenerName;
    }

    /**
     * @param evt The RingEvent which occurred
     * @param evtId The id of the event in the buffer
     * @param endOfBatch whether the event was the last in a series of processed events.
     */
    public void handle(final RingEvent evt, final long evtId, final boolean endOfBatch) {
        final UpdateEvent event = evt.evt;
        final Class<? extends UpdateEvent> eventClass = event.getClass();
        final EventSource filterSource = filterSourceRef.get();
        if (listener == null) {
            evt.getHandlerCount().countDown();
            return;
        }
        if (filterSource != event.getSource() // incompatible source
                || (filterEventType != null && !filterEventType.isAssignableFrom(eventClass)) // and/or wrong (optional) event type
                || (filterPredicate != null && !filterPredicate.test(evt))) { // and/or (optional) predicate did not match

            if (listener instanceof MultipleEventListener && endOfBatch) { // handling previously aggregated events
                final String evtClassName = eventClass.getSimpleName();
                //evt.getSubmitTimestamp().stop(Metrics.timer("chartfx.events.latency", "eventType", evtClassName, "listener", listenerName));

                this.listener.handle(null);

                // record the time from event creation to listener executed
                //evt.getSubmitTimestamp().stop(Metrics.timer("chartfx.events.latency.post", "eventType", evtClassName, "listener", listenerName));
            }

            evt.getHandlerCount().countDown();
            return; // early return
        }

        // measure latency as time between adding event to the buffer and staring of the listener

        if (this.listener instanceof MultipleEventListener && !endOfBatch) {
            ((MultipleEventListener) this.listener).aggregate(event);
        } else {
            //final String evtClassName = eventClass.getSimpleName();
            //evt.getSubmitTimestamp().stop(Metrics.timer("chartfx.events.latency", "eventType", evtClassName, "listener", listenerName));

            this.listener.handle(event);

            // record the time from event creation to listener executed
            // evt.getSubmitTimestamp().stop(Metrics.timer("chartfx.events.latency.post", "eventType", evtClassName, "listener", listenerName));
        }
        evt.getHandlerCount().countDown();
    }

    /**
     * @param listener new listener to be executed on new events
     */
    public void setListener(EventListener listener) {
        this.listener = listener;
    }
}

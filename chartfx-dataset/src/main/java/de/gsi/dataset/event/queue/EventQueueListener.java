package de.gsi.dataset.event.queue;

import java.lang.ref.WeakReference;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.gsi.dataset.event.EventListener;
import de.gsi.dataset.event.EventSource;
import de.gsi.dataset.event.UpdateEvent;

/**
 * Event Queue Listener gets periodically called and retrieves the new events since its last invocation from the event queue.
 * Depending on its strategy it calls its callback function for events.
 * 
 * There is a ThreadPool which cycles over all EventQueueListeners and executes them.
 *
 * @author Alexander Krimm
 */
public class EventQueueListener {
     private static final Logger LOGGER = LoggerFactory.getLogger(EventQueueListener.class);
     
     private EventListener listener;
     private MultipleEventListener multiListener; 
     private long position; // last position in the eventLog which was processed
     private long lastEvent; // last matching event which was processed by the listener
     private EventQueueListenerStrategy strategy; // strategy how listeners should be invoked for events
     private WeakReference<EventSource> filterSource; // the source to listen to
     private Class<? extends UpdateEvent> filterEventType; // the event type to listen to
     private Predicate<UpdateEvent> filterPredicate; // a filter for selecting matching events
     
     public enum EventQueueListenerStrategy {
         // multi-threaded strategies
         M_LAST_DROP, // only run the listener on the last matching event, dropping if the listener is still running
         M_LAST_INTERRUPT,  // like M_LAST_DROP, but cancel a currently running Listener
         M_LAST_RESCHEDULE, // like M_LAST_DROP, but ensure, that the last event gets rescheduled if no listener is running
         M_EVERY, // run every Event in parallel
         // single-threaded strategies
         S_ALL, // run every event on this thread
         S_LAST; // run the last event on this thread
     }
}


package de.gsi.dataset.event.queue;

import de.gsi.dataset.event.EventSource;
import de.gsi.dataset.event.UpdateEvent;

/**
 * 
 * @author rstein
 *
 */
public interface MultipleEventListener {

    /**
     * This method needs to be provided by an implementation of {@code UpdateListener}. It is called if an
     * {@link EventSource} has been modified/updated.
     * Compared to the simple EventListener, the MultipleEventListener gets an array of all Events since the last
     * invocation. Then the 
     * <p>
     * In general is is considered bad practice to modify the observed value in this method.
     *
     * @param events The {@code UpdateEvent}s issued by the modified {@code UpdateSource}
     */
    void handle(UpdateEvent[] events);
}

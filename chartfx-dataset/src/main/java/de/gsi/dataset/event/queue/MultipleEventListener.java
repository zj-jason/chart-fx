package de.gsi.dataset.event.queue;

import de.gsi.dataset.event.EventListener;
import de.gsi.dataset.event.EventSource;
import de.gsi.dataset.event.UpdateEvent;

/**
 * Extension of the event listener to aggregate multiple events into one.
 * 
 * 
 * @author akrimm
 *
 */
public interface MultipleEventListener extends EventListener {

    /**
     * This method needs to be provided by an implementation of {@code UpdateListener}. It is called if an
     * {@link EventSource} has been modified/updated for the last time in a batch.
     * It should then aggregate the new event and trigger its actions directly afterwards.
     * This interface is compatible with the regular EventListener, so that if unsupported the data is just not aggregated.
     * <p>
     * In general is is considered bad practice to modify the observed value in this method.
     *
     * @param event The {@code UpdateEvent} issued by the modified {@code UpdateSource}
     */
    @Override
    void handle(UpdateEvent event, long id);
    
    /**
     * This method is called for each event but the last one. The listener can then update its state, so that the final 
     * handle() call can then trigger actions based on all intermediate events.
     * @param event the event to be added to the aggregated events
     */
    void aggregate(UpdateEvent event);
}

package de.gsi.dataset.event;

/**
 * AddedDataEvent class that is passed along the notification performed by the {@code EventSource} class. The class is
 * intended to be further extended by named derivatives to allow for context-based event filters.
 * 
 * @see EventSource for details
 * 
 * @author rstein
 *
 */
public class AddedDataEvent extends UpdatedDataEvent {
    private static final long serialVersionUID = 1893124043479588248L;

    /**
     * generates new update event
     * 
     * @param source the class issuing the event
     * @param parent optional parent of the event
     */
    public AddedDataEvent(final EventSource source, final UpdateEvent... parent) {
        super(source, null, null, parent);
    }

    /**
     * generates new update event
     * 
     * @param source the class issuing the event
     * @param msg a customised message to be passed along (e.g. for debugging)
     * @param parent optional parent of the event
     */
    public AddedDataEvent(final EventSource source, final String msg, final UpdateEvent... parent) {
        super(source, msg, null, parent);
    }

    /**
     * generates new update event
     * 
     * @param source the class issuing the event
     * @param msg a customised message to be passed along (e.g. for debugging)
     * @param payload a customised user pay-load to be passed to the listener
     * @param parent optional parent of the event
     */
    public AddedDataEvent(final EventSource source, final String msg, final Object payload, final UpdateEvent... parent) {
        super(source, msg, payload, parent);
    }
}

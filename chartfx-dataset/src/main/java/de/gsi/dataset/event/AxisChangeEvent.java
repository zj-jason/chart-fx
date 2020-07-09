package de.gsi.dataset.event;

/**
 * UpdatedAxisDataEvent class that is passed along the notification performed by the {@code EventSource} class. Sent
 * when the axis name or unit is changed (usually via Dataset.getAxisDescription(int dim).set(...)).
 * 
 * @see EventSource Event Source for details about DataSet Event implementation
 * @see de.gsi.dataset.AxisDescription#set for details
 * 
 * @author akrimm
 *
 */
public class AxisChangeEvent extends UpdateEvent {
    private static final long serialVersionUID = 233444066954702782L;

    private int dim;

    /**
     * generates new update event
     * 
     * @param source the class issuing the event
     * @param parent parent optional parent of the event
     */
    public AxisChangeEvent(final EventSource source, final UpdateEvent... parent) {
        super(source, null, null, parent);
        this.dim = -1;
    }

    /**
     * generates new update event
     * 
     * @param source the class issuing the event
     * @param dim for which dimension the Axis data was changed
     * @param parent parent optional parent of the event
     */
    public AxisChangeEvent(final EventSource source, int dim, final UpdateEvent... parent) {
        super(source, null, null, parent);
        this.dim = dim;
    }

    /**
     * generates new update event
     * 
     * @param source the class issuing the event
     * @param msg a customised message to be passed along (e.g. for debugging)
     * @param dim for which dimension the Axis data was changed
     * @param parent parent optional parent of the event
     */
    public AxisChangeEvent(final EventSource source, final String msg, int dim, final UpdateEvent... parent) {
        super(source, msg, null, parent);
        this.dim = dim;
    }

    /**
     * generates new update event
     * 
     * @param source the class issuing the event
     * @param msg a customised message to be passed along (e.g. for debugging)
     * @param payload a customised user pay-load to be passed to the listener
     * @param dim for which dimension the Axis data was changed
     * @param parent parent optional parent of the event
     */
    public AxisChangeEvent(final EventSource source, final String msg, final Object payload, int dim, final UpdateEvent... parent) {
        super(source, msg, payload, parent);
        this.dim = dim;
    }

    public int getDimension() {
        return dim;
    }
}
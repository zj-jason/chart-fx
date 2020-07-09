package de.gsi.dataset.event;

/**
 * Event issued when only the axis range changed
 * 
 * @see de.gsi.dataset.AxisDescription
 * 
 * @author rstein
 */
public class AxisRangeChangeEvent extends AxisChangeEvent {
    private static final long serialVersionUID = -7285890268185312226L;

    public AxisRangeChangeEvent(EventSource evtSource, final UpdateEvent... parent) {
        super(evtSource, parent);
    }

    public AxisRangeChangeEvent(EventSource evtSource, int dimension, final UpdateEvent... parent) {
        super(evtSource, dimension, parent);
    }

    public AxisRangeChangeEvent(EventSource evtSource, String msg, int dimension, final UpdateEvent... parent) {
        super(evtSource, msg, dimension, parent);
    }

    public AxisRangeChangeEvent(EventSource evtSource, String msg, Object obj, int dimension, final UpdateEvent... parent) {
        super(evtSource, msg, obj, dimension, parent);
    }
}

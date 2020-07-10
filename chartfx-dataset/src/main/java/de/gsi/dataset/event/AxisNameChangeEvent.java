package de.gsi.dataset.event;

/**
 * Event issued when only the name/unit changed
 * 
 * @see de.gsi.dataset.AxisDescription
 * 
 * @author rstein
 */
public class AxisNameChangeEvent extends AxisChangeEvent {
    private static final long serialVersionUID = -425352346909656104L;

    public AxisNameChangeEvent(EventSource evtSource, final UpdateEvent... parent) {
        super(evtSource, parent);
    }

    public AxisNameChangeEvent(EventSource evtSource, int dimension, final UpdateEvent... parent) {
        super(evtSource, dimension, parent);
    }

    public AxisNameChangeEvent(EventSource evtSource, String msg, int dimension, final UpdateEvent... parent) {
        super(evtSource, msg, dimension, parent);
    }

    public AxisNameChangeEvent(EventSource evtSource, String msg, Object obj, int dimension, final UpdateEvent... parent) {
        super(evtSource, msg, obj, dimension, parent);
    }
}

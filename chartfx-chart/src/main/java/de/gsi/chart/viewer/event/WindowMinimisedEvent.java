package de.gsi.chart.viewer.event;

import de.gsi.dataset.event.EventSource;
import de.gsi.dataset.event.UpdateEvent;

/**
 * Event issued after DataViewWindow has been minimised
 *
 * @see de.gsi.chart.viewer.DataViewWindow
 * @see de.gsi.chart.viewer.event.WindowUpdateEvent
 * @author rstein
 */
public class WindowMinimisedEvent extends WindowUpdateEvent {
    private static final long serialVersionUID = 2846294413532027952L;

    public WindowMinimisedEvent(final EventSource evtSource, final UpdateEvent... parent) {
        super(evtSource, Type.WINDOW_MINIMISED, parent);
    }

    public WindowMinimisedEvent(final EventSource evtSource, final String msg, final UpdateEvent... parent) {
        super(evtSource, msg, Type.WINDOW_MINIMISED, parent);
    }

    public WindowMinimisedEvent(final EventSource evtSource, final String msg, final Object obj, final UpdateEvent... parent) {
        super(evtSource, msg, obj, Type.WINDOW_MINIMISED, parent);
    }
}

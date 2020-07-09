package de.gsi.dataset.event;

import java.util.EventObject;

/**
 * Event class that is passed along the notification performed by the {@code EventSource} class. The class is intended
 * to be further extended by named derivatives to allow for context-based event filters.
 * 
 * @see EventSource for details
 * @author rstein
 */
public class UpdateEvent extends EventObject {
    private static final long serialVersionUID = -3097725478448868303L;
    private static boolean DEBUG = false;
    private final String caller;
    private final String msg;
    private transient Object payload;
    private final UpdateEvent parent;

    /**
     * generates new update event
     * 
     * @param source the class issuing the event
     * @param parent optional parent of the event
     */
    public UpdateEvent(final EventSource source, final UpdateEvent... parent) {
        this(source, null, null, parent);
    }

    /**
     * generates new update event
     * 
     * @param source the class issuing the event
     * @param msg a customised message to be passed along (e.g. for debugging)
     * @param parent optional parent of the event
     */
    public UpdateEvent(final EventSource source, final String msg, final UpdateEvent... parent) {
        this(source, msg, null, parent);
    }

    /**
     * generates new update event
     * 
     * @param source the class issuing the event
     * @param msg a customised message to be passed along (e.g. for debugging)
     * @param payload a customised user pay-load to be passed to the listener
     * @param parent optional parent of the event
     */
    public UpdateEvent(final EventSource source, final String msg, final Object payload, final UpdateEvent... parent) {
        super(source);
        this.msg = msg;
        this.payload = payload;
        this.parent = parent.length == 0 ? null : parent[0];
        if (DEBUG) { // in debug mode add stack trace information and verify recursion depth
            StackTraceElement st = Thread.currentThread().getStackTrace()[1];
            String file = st.getFileName();
            int lineNo = st.getLineNumber();
            int rDepth = this.getRecursionDepth(30);
            caller = file + "L" + lineNo + " (" + rDepth + ")";

        } else {
            caller = null;
        }

    }

    /**
     * @return a customised message to be passed along (e.g. for debugging)
     */
    public String getMessage() {
        return msg;
    }

    @Override
    public EventSource getSource() {
        return (EventSource) super.getSource();
    }

    public UpdateEvent getParent() {
        return parent;
    }

    /**
     * @return a customised user pay-load to be passed to the listener
     */
    public Object getPayLoad() {
        return payload;
    }

    /**
     * Utility function to evaluate the recursion depth of an event cascade.
     * Can be used to debug performance problems due to event cascades and cycles.
     * 
     * @param nMax The maxium recursion depth. If it is reached, an {@linkplain IllegalStateException} is thrown.
     * @return The number of parents for this event.
     */
    public int getRecursionDepth(final int... nMax) {
        int result = 0;
        UpdateEvent p = parent;
        if (nMax.length == 0) {
            while (p != null) {
                p = p.parent;
                result++;
            }
        } else {
            while (p != null && result < nMax[0]) {
                p = p.parent;
                result++;
            }
            if (result == nMax[0]) {
                throw new IllegalStateException("iteration depth nMax=" + nMax[0] + " reached");
            }
        }
        return result;
    }

    public static void setDebug(final boolean debug) {
        DEBUG = debug;
    }
}

package de.gsi;

import com.lmax.disruptor.EventHandler;

import java.util.HashMap;
import java.util.Map;

public class ContextDispatcher implements EventHandler<AccAggreator.RingEvent> {
    private final Map<String, Long> activeContexts = new HashMap<>(); // all currently active contexts and their

    public ContextDispatcher() {

    }

    @Override
    public void onEvent(final AccAggreator.RingEvent event, final long sequence, final boolean endOfBatch) throws Exception {
        // scan for new contexts
        // schedule new Handlers for new Contexts
        // periodically check for expired contexts
    }
}


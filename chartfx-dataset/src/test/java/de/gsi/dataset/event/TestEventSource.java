package de.gsi.dataset.event;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Default event source for testing
 * @author rstein
 */
public class TestEventSource implements EventSource {
    protected final AtomicBoolean autoNotification = new AtomicBoolean(true);

    @Override
    public AtomicBoolean autoNotification() {
        return autoNotification;
    }
}

package de.gsi.microservice.aggregate.disruptor;

import com.lmax.disruptor.EventHandler;
import de.gsi.microservice.cmwlight.DirectoryLightClient;

import java.util.HashMap;
import java.util.Map;

public class ContextDispatcher implements EventHandler<AccAggregator.RingEvent> {
    private final Map<Integer, AccAggregator> activeContexts = new HashMap<>(); // all currently active contexts and their

    public ContextDispatcher() {

    }

    @Override
    public void onEvent(final AccAggregator.RingEvent event, final long sequence, final boolean endOfBatch) throws Exception {
        if (event.chain == -1) {
            return;
        }
        // scan for new contexts and schedule new Handlers for new Contexts
        final AccAggregator chainAggregator = activeContexts.computeIfAbsent(event.chain, c -> {
            try {
                final AccAggregator cAgg = new AccAggregator();
                cAgg.start();
                cAgg.addProcessor((event1, sequence1, endOfBatch1) -> System.out.println(event1));
                return cAgg;
            } catch (DirectoryLightClient.DirectoryClientException e) {
                e.printStackTrace();
                return null;
            }
        });

        chainAggregator.disruptor.publishEvent((event1, sequence1, arg0) -> event1.set(arg0), event);

        // periodically check for expired contexts
    }
}


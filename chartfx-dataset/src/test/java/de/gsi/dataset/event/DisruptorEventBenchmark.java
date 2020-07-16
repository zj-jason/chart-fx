package de.gsi.dataset.event;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.profile.StackProfiler;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.*;

import de.gsi.dataset.event.queue.EventQueue;
import de.gsi.dataset.event.queue.EventQueueListener;

/**
 * @author Alexander Krimm
 */
@State(Scope.Benchmark)
public class DisruptorEventBenchmark {
    // setup test Queue
    final EventQueue test = EventQueue.getInstance(256);
    // dummy event source for emitting events to the ring buffer
    final EventSource source = new EventSource() {
        @Override
        public List<EventListener> updateEventListener() {
            return Collections.emptyList();
        }

        @Override
        public AtomicBoolean autoNotification() {
            return null;
        }
    };

    @Setup()
    public void initialize() {
        // add listener which listens to AxisRecomputationEvents and emits AxisRangeChangeEvents
        final EventQueueListener eql2 = new EventQueueListener( //
                test.getQueue(), // ring buffer
                event -> {
                    //test.submitEvent(new AxisRangeChangeEvent(source, 3, event));
                }, // listener
                AxisRecomputationEvent.class, // EventType
                source, // event source
                e -> true,
                "AxisRecomputationListener"); // filter
        test.addListener(eql2);
    }

    @Benchmark
    @Warmup(iterations = 1)
    @Fork(value = 2, warmups = 1)
    public void submitAndWait(Blackhole blackhole) {
        test.submitEventAndWait(new AxisRecomputationEvent(source, 3));
    }

    @Benchmark
    @Warmup(iterations = 1)
    @Fork(value = 2, warmups = 1)
    public void submitOnly(Blackhole blackhole) {
        test.submitEvent(new AxisRecomputationEvent(source, 3));
    }

    //@Benchmark
    //@Warmup(iterations = 1)
    //@Fork(value = 2, warmups = 1)
    //public void baseline(Blackhole blackhole) {
    //}
}

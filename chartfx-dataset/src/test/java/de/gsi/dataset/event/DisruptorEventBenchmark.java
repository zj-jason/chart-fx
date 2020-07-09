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
    final EventQueue test = EventQueue.getInstance(16);
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
                    test.submitEvent(new AxisRangeChangeEvent(source, 3, event));
                }, // listener
                AxisRecomputationEvent.class, // EventType
                source, // event source
                e -> true,
                "AxisRecomputationListener"); // filter
        test.addListener(eql2);
    }

    @Benchmark
    @Warmup(iterations = 1)
    @Fork(value = 2, warmups = 2)
    public void oneToOne(Blackhole blackhole) {
        test.submitEventAndWait(new AxisRecomputationEvent(source, 3));
    }

    public static void debugBenchmark() {
        final DisruptorEventBenchmark cut = new DisruptorEventBenchmark();
        cut.initialize();
        for (int i = 0; i < 1000; i++) {
            System.out.println(i);
            cut.oneToOne(null);
        }
    }

    /**
     * Launch this benchmark. Note that to start this benchmark from eclipse you first have to run mvn test-compile to
     * run the annotations processor.
     * 
     * @param args cli arguments (unused)
     * @throws Exception in case of errors
     */
    public static void main(String[] args) throws Exception {
//        debugBenchmark();
        Options opt = new OptionsBuilder()
                // Specify which benchmarks to run. You can be more specific if you'd like to run only one benchmark per test.
                .include(DisruptorEventBenchmark.class.getName() + ".*")
                // Set the following options as needed
                .mode(Mode.Throughput)
                .timeUnit(TimeUnit.SECONDS)
                .warmupTime(TimeValue.seconds(10))
                .warmupIterations(1)
                .timeout(TimeValue.minutes(10))
                .measurementTime(TimeValue.seconds(10))
                .measurementIterations(5)
                .threads(1)
                .forks(2).warmupForks(2)
                .shouldFailOnError(true)
                .shouldDoGC(true)
                .addProfiler(StackProfiler.class)
                //.jvmArgs("-XX:+UnlockDiagnosticVMOptions", "-XX:+PrintInlining")
                .build();
        new Runner(opt).run();
    }
}

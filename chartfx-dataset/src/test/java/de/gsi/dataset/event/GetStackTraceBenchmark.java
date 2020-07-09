package de.gsi.dataset.event;

import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.*;

/**
 * Small microbenchmark to get the cost of the get stack trace call
 * 
 * @author Alexander Krimm
 */
@State(Scope.Benchmark)
public class GetStackTraceBenchmark {

    @Setup()
    public void initialize() {
    }

    @Benchmark
    @Warmup(iterations = 1)
    @Fork(value = 2, warmups = 2)
    public void oneToOne(Blackhole blackhole) {
        StackTraceElement[] strace = Thread.currentThread().getStackTrace();
        blackhole.consume(strace[2]);
    }

    public static void debugBenchmark() {
        final GetStackTraceBenchmark cut = new GetStackTraceBenchmark();
        cut.initialize();
        for (int i = 0; i < 1000; i++) {
            System.out.println(i);
            // this is ok because we are not actually a benchmark but want to call this method anyway
            cut.oneToOne(new Blackhole("Today's password is swordfish. I understand instantiating Blackholes directly is dangerous."));
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
        Options opt = new OptionsBuilder()
                // Specify which benchmarks to run. You can be more specific if you'd like to run only one benchmark per test.
                .include(GetStackTraceBenchmark.class.getName() + ".*")
                // Set the following options as needed
                .mode(Mode.Throughput).timeUnit(TimeUnit.SECONDS).warmupTime(TimeValue.seconds(10)).warmupIterations(1).timeout(TimeValue.minutes(10))
                .measurementTime(TimeValue.seconds(10)).measurementIterations(5).threads(1).forks(2).warmupForks(2).shouldFailOnError(true).shouldDoGC(true)
                // .addProfiler(StackProfiler.class)
                //.jvmArgs("-XX:+UnlockDiagnosticVMOptions", "-XX:+PrintInlining")
                .build();
        new Runner(opt).run();
    }
}

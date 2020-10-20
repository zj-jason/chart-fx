package de.gsi.mircoservice.aggregate.disruptor;

import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.util.DaemonThreadFactory;

public class TestEventSourceSample {
    private final RingBuffer<TestEventSource.IngestedEvent> rb;
    private final TestEventSource testEventSource;

    public TestEventSourceSample() {

        final Disruptor<TestEventSource.IngestedEvent> disruptor = new Disruptor<TestEventSource.IngestedEvent>(
                () -> new TestEventSource.IngestedEvent(), 256, DaemonThreadFactory.INSTANCE);
        final AggregatingProcessor aggProc = new AggregatingProcessor(disruptor.getRingBuffer());
        disruptor
                .handleEventsWith(((event, sequence, endOfBatch) -> System.out.println(sequence + "@" + event.ingestionTime + ": " +  event.payload)))
                .handleEventsWith(aggProc).then(aggProc.workers);

        rb = disruptor.start();
        testEventSource = new TestEventSource(TestEventSource.overlapping, true, rb);
    }

    public void start() {
        final Thread eventThread = new Thread(testEventSource, "testEventSource");
        eventThread.setDaemon(true);
        eventThread.run();
    }

    public static void main(String[] args) throws InterruptedException {
        new TestEventSourceSample().start();
        Thread.sleep(3000);
    }
}

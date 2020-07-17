package de.gsi.math.samples;

import com.lmax.disruptor.BatchEventProcessor;
import com.lmax.disruptor.EventProcessor;
import com.lmax.disruptor.RingBuffer;
import de.gsi.chart.XYChart;
import de.gsi.chart.axes.spi.CategoryAxis;
import de.gsi.chart.axes.spi.DefaultNumericAxis;
import de.gsi.chart.marker.DefaultMarker;
import de.gsi.chart.plugins.EditAxis;
import de.gsi.chart.plugins.Zoomer;
import de.gsi.chart.renderer.ErrorStyle;
import de.gsi.chart.renderer.LineStyle;
import de.gsi.chart.renderer.spi.ErrorDataSetRenderer;
import de.gsi.dataset.DataSet;
import de.gsi.dataset.event.EventSource;
import de.gsi.dataset.event.UpdateEvent;
import de.gsi.dataset.event.UpdatedDataEvent;
import de.gsi.dataset.event.queue.EventQueue;
import de.gsi.dataset.event.queue.EventQueueListener;
import de.gsi.dataset.spi.CircularDoubleErrorDataSet;
import de.gsi.dataset.spi.DataSetBuilder;
import de.gsi.dataset.spi.DimReductionDataSet;
import de.gsi.dataset.spi.MultiDimDoubleDataSet;
import de.gsi.dataset.utils.ProcessingProfiler;
import io.micrometer.core.instrument.Metrics;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Sample which plots all generated events from the chartfx event queue
 *
 * @author akrimm
 */
public class EventVisualisation extends Application {
    private static final Logger LOGGER = LoggerFactory.getLogger(EventVisualisation.class);
    private static int BUFFER_CAPACITY = 750; // 750 samples @ 25 Hz <-> 30 s
    private final CircularDoubleErrorDataSet eventDataset = new CircularDoubleErrorDataSet( "events submitted to queue", EventVisualisation.BUFFER_CAPACITY);
    private final ErrorDataSetRenderer eventRenderer = new ErrorDataSetRenderer();
    private final CategoryAxis xAxis1 = new CategoryAxis("Event Source and Name");
    private final DefaultNumericAxis yAxis1 = new DefaultNumericAxis("Time", "s");
    private final MultiDimDoubleDataSet ds = (MultiDimDoubleDataSet) new DataSetBuilder("MockDataSet").setValues(DataSet.DIM_Z, new double[][] {{1.0,2,3}, {8,7,5}, {9,7,4}}).build();
    private final DimReductionDataSet reducedDataSet = new DimReductionDataSet(ds, 1, DimReductionDataSet.Option.MEAN);
    private Thread eventThread;

    public Node initComponents(Scene scene) {
        eventRenderer.setErrorType(ErrorStyle.NONE);
        eventRenderer.setPointReduction(false);
        eventRenderer.setDrawMarker(true);
        eventRenderer.setMarker(DefaultMarker.CIRCLE);
        eventRenderer.setPolyLineStyle(LineStyle.NONE);

        xAxis1.setAutoRangeRounding(false);
        xAxis1.setTickLabelRotation(45);
        xAxis1.setMinorTickCount(30);
        xAxis1.invertAxis(false);
        xAxis1.setTimeAxis(true);

        final XYChart chart = new XYChart(xAxis1, yAxis1);
        chart.setAnimated(false);
        chart.getRenderers().setAll(eventRenderer);
        chart.getPlugins().add(new EditAxis());
        chart.getPlugins().add(new Zoomer());

        eventRenderer.getDatasets().add(eventDataset);

        return chart;
    }

    @Override
    public void start(final Stage primaryStage) {
        ProcessingProfiler.setVerboseOutputState(true);
        ProcessingProfiler.setLoggerOutputState(true);
        ProcessingProfiler.setDebugState(false);

        final BorderPane root = new BorderPane();
        final Scene scene = new Scene(root, 600, 1000);
        root.setCenter(initComponents(scene));

        final long startTime = ProcessingProfiler.getTimeStamp();
        primaryStage.setTitle(this.getClass().getSimpleName());
        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest(evt -> Platform.exit());
        setupEventUpdate();
        generateEventNoise();
        primaryStage.show();
    }

    private void setupEventUpdate() {
        final RingBuffer<EventQueue.RingEvent> queue = EventQueue.getInstance().getQueue();
        // Alternative 1: add additional listener
        EventQueue.getInstance().addListener(new EventQueueListener(queue, ev -> {
            LOGGER.atError().log("Processing event");
        }, null, null, null, "EventToPlot"));
        final List<String> categories = new ArrayList<>();
        // Alternative 2: separate event Processor thread
        final EventProcessor eventProcessor = new BatchEventProcessor<>(queue, queue.newBarrier(), (ev, id, lastOfBatch) -> {
            EventSource source = ev.getEvent().getSource();
            Class<? extends UpdateEvent> event = ev.getEvent().getClass();
            final String categoryLabel = source.getClass().getSimpleName() + "(" + source.hashCode() + ")";
            LOGGER.atError().addArgument(categoryLabel).log("Added Event: {}");
            int catNo = categories.indexOf(categoryLabel);
            if (catNo == -1) {
                catNo = categories.size();
                categories.add(categoryLabel);
                xAxis1.setCategories(categories);
            }
            eventDataset.add(catNo, ev.getSubmitTimestamp().stop(Metrics.timer("asdf")), 0 , 0, categoryLabel);
        });
        eventThread = new Thread("Event-processor") {
            @Override
            public void run() {
                LOGGER.atError().log("Add EventProcessor");
                eventProcessor.run();
            }
        };
        eventThread.setDaemon(true);
        eventProcessor.getSequence().set(queue.getCursor());
        eventThread.start();
    }

    private void generateEventNoise() {
        final ScheduledExecutorService exService = Executors.newScheduledThreadPool(1);
        ds.invokeListener(new UpdatedDataEvent(ds, "Updated data"));

        exService.scheduleAtFixedRate(() -> {
            LOGGER.atError().log("Updating Data");
            //ds.lock().writeLockGuard(() -> {
            //    final int index = 1;
            //    final double newVal = 1.4;
            //    ds.set(DataSet.DIM_Y, ds.get(DataSet.DIM_X, index), ds.get(DataSet.DIM_Y, index), newVal);
            //});
            ds.invokeListener(new UpdatedDataEvent(ds, "Updated data"));
            }, 0, 1000, TimeUnit.MILLISECONDS);
    }

    /**
     * @param args the command line arguments
     */
    public static void main(final String[] args) {
        Application.launch(args);
    }
}

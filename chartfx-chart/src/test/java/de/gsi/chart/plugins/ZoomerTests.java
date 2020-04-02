package de.gsi.chart.plugins;

import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.geometry.VerticalDirection;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import javafx.util.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxAssert;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.DebugUtils;

import de.gsi.chart.XYChart;
import de.gsi.chart.axes.AxisMode;
import de.gsi.chart.axes.spi.DefaultNumericAxis;
import de.gsi.chart.ui.utils.JavaFXInterceptorUtils.SelectiveJavaFxInterceptor;
import de.gsi.chart.ui.geometry.Side;
import de.gsi.chart.ui.utils.TestFx;

/*
 * Tests for the Zoomer Plugin
 * 
 * TODO: Additional things to test
 * - range slider
 * - more than 2 axes, zoom inhibition property
 * - Error Handling
 * - infinite zoom to numerical limit
 * - zoom/pan to negative log axis?
 *
 * @author akrimm
 */
@ExtendWith(ApplicationExtension.class)
@ExtendWith(SelectiveJavaFxInterceptor.class)
public class ZoomerTests {
    private static final double TOLERANCE = 1.0;
    private DefaultNumericAxis xAxis;
    private DefaultNumericAxis yAxis;
    private DefaultNumericAxis ommittedXAxis;
    private DefaultNumericAxis ommittedYAxis;
    private XYChart chart;
    private Zoomer zoomer;
    private Scene scene;
    //    private FxRobot fxRobot = new FxRobot();

    /**
     * Initialize the scene with an empty chart with 2 axes
     * 
     * @param stage
     */
    @Start
    public void start(Stage stage) {
        xAxis = new DefaultNumericAxis("x", -100, +100, 10);
        xAxis.setAnimated(false);
        yAxis = new DefaultNumericAxis("x", -100, +100, 10);
        yAxis.setAnimated(false);
        ommittedXAxis = new DefaultNumericAxis("x - nozoom", -42, +42, 5);
        ommittedXAxis.setAnimated(false);
        ommittedXAxis.setSide(Side.TOP);
        ommittedYAxis = new DefaultNumericAxis("y - nozoom", -23, +23, 5);
        ommittedYAxis.setAnimated(false);
        ommittedYAxis.setSide(Side.RIGHT);
        // TODO: inverting the y axis should not break the test
        // yAxis.invertAxis(true);
        // yAxis.set(-100, 100);
        chart = new XYChart(xAxis, yAxis);
        chart.getAxes().addAll(ommittedXAxis, ommittedYAxis);
        zoomer = new Zoomer();
        Zoomer.setOmitZoom(ommittedYAxis, true);
        zoomer.omitAxisZoomList().add(ommittedXAxis);
        zoomer.setAnimated(false);
        chart.getPlugins().add(zoomer);
        scene = new Scene(new Pane(chart), 200, 200);
        stage.setScene(scene);
        stage.show();
    }

    /**
     * XY box zoom
     * 
     * @param fxRobot
     */
    @Test
    public void testBoxZoom(FxRobot fxRobot) {
        // assert initial axis configuration
        assertAxesZoom(-100, -100, 100, 100, fxRobot);
        // perform box zoom
        fxRobot.drag(getPointFromChartCoordinates(-50, 20, fxRobot), MouseButton.PRIMARY)
                .dropTo(getPointFromChartCoordinates(50, -30, fxRobot));
        // assert new axis ranges
        assertAxesZoom(-50, -30, 50, 20, fxRobot, TOLERANCE);
        // perform box zoom X
        fxRobot.interact(() -> zoomer.setAxisMode(AxisMode.X));
        fxRobot.drag(getPointFromChartCoordinates(-45, 15, fxRobot), MouseButton.PRIMARY)
                .dropTo(getPointFromChartCoordinates(42, -20, fxRobot));
        // assert new axis ranges
        assertAxesZoom(-45, -30, 42, 20, fxRobot, TOLERANCE);
        // perform box zoom Y
        fxRobot.interact(() -> zoomer.setAxisMode(AxisMode.Y));
        fxRobot.drag(getPointFromChartCoordinates(-40, 15, fxRobot), MouseButton.PRIMARY)
                .dropTo(getPointFromChartCoordinates(30, -20, fxRobot));
        // assert new axis ranges
        assertAxesZoom(-45, -20, 42, 15, fxRobot, TOLERANCE);
        // reset zoom (right click)
        fxRobot.clickOn(chart, MouseButton.SECONDARY);
        assertAxesZoom(-45, -30, 42, 20, fxRobot, TOLERANCE);
        // completely reset to origin (strg + right click)
        fxRobot.press(KeyCode.CONTROL).clickOn(chart, MouseButton.SECONDARY).release(KeyCode.CONTROL);
        assertAxesZoom(-100, -100, 100, 100, fxRobot);
        // one more time to reach fast return
        fxRobot.press(KeyCode.CONTROL).clickOn(chart, MouseButton.SECONDARY).release(KeyCode.CONTROL);
        assertAxesZoom(-100, -100, 100, 100, fxRobot);
    }

    /**
     * XY box zoom with auto zoom enabled
     * 
     * @param fxRobot
     */
    @Test
    public void testBoxZoomAuto(FxRobot fxRobot) {
        fxRobot.interact(() -> {
            zoomer.setAnimated(true);
            zoomer.setZoomDuration(Duration.millis(70));
            zoomer.setAutoZoomEnabled(true);
            zoomer.setAutoZoomThreshold(10); ///TODO: seems to be ignored
        });
        // assert initial axis configuration
        assertAxesZoom(-100, -100, 100, 100, fxRobot);
        // perform box zoom
        fxRobot.drag(getPointFromChartCoordinates(-50, 20, fxRobot), MouseButton.PRIMARY)
                .dropTo(getPointFromChartCoordinates(50, -30, fxRobot));
        fxRobot.sleep(100);
        // assert new axis ranges
        assertAxesZoom(-50, -30, 50, 20, fxRobot, TOLERANCE);
        // perform box zoom X
        fxRobot.drag(getPointFromChartCoordinates(-45, -20, fxRobot).add(0,-9), MouseButton.PRIMARY)
                .dropTo(getPointFromChartCoordinates(42, -20, fxRobot));
        fxRobot.sleep(100);
        // assert new axis ranges
        assertAxesZoom(-45, -30, 42, 20, fxRobot, TOLERANCE);
        // perform box zoom Y
        fxRobot.drag(getPointFromChartCoordinates(3, 15, fxRobot).add(9, 0) , MouseButton.PRIMARY)
                .dropTo(getPointFromChartCoordinates(3, -22, fxRobot));
        fxRobot.sleep(100);
        // assert new axis ranges
        assertAxesZoom(-45, -22, 42, 15, fxRobot, TOLERANCE);
        // reset zoom (right click)
        fxRobot.clickOn(chart, MouseButton.SECONDARY);
        fxRobot.sleep(100);
        assertAxesZoom(-45, -30, 42, 20, fxRobot, TOLERANCE);
        // completely reset to origin (strg + right click)
        fxRobot.press(KeyCode.CONTROL).clickOn(chart, MouseButton.SECONDARY).release(KeyCode.CONTROL);
        fxRobot.sleep(100);
        assertAxesZoom(-100, -100, 100, 100, fxRobot);
        // one more time to reach fast return
        fxRobot.press(KeyCode.CONTROL).clickOn(chart, MouseButton.SECONDARY).release(KeyCode.CONTROL);
        fxRobot.sleep(100);
        assertAxesZoom(-100, -100, 100, 100, fxRobot);
    }

    /**
     * Zoom constrained to y direction
     * 
     * @param fxRobot
     */
    @Test
    public void testScrollZoom(FxRobot fxRobot) {
        fxRobot.interact(() -> zoomer.setAxisMode(AxisMode.XY));
        // assert initial axis configuration
        assertAxesZoom(-100, -100, 100, 100, fxRobot);
        // perform scroll zoom
        Point2D point = getPointFromChartCoordinates(50, -25, fxRobot);
        fxRobot.moveTo(point).scroll(1, VerticalDirection.UP);
        assertAxesZoom(-85, -92.5, 95, 87.5, fxRobot, TOLERANCE);
        // reset zoom (right click)
        fxRobot.clickOn(chart, MouseButton.SECONDARY);
        assertAxesZoom(-100, -100, 100, 100, fxRobot);
    }

    /**
     * Zoom constrained to y direction
     * 
     * @param fxRobot
     */
    @Test
    public void testScrollZoomX(FxRobot fxRobot) {
        fxRobot.interact(() -> zoomer.setAxisMode(AxisMode.X));
        // assert initial axis configuration
        assertAxesZoom(-100, -100, 100, 100, fxRobot);
        // perform box zoom
        Point2D point = getPointFromChartCoordinates(50, -25, fxRobot);
        fxRobot.moveTo(point).scroll(1, VerticalDirection.UP);
        assertAxesZoom(-85, -100, 95, 100, fxRobot, TOLERANCE);
        // reset zoom (right click)
        fxRobot.clickOn(chart, MouseButton.SECONDARY);
        assertAxesZoom(-100, -100, 100, 100, fxRobot);
    }

    /**
     * Zoom constrained to y direction
     * 
     * @param fxRobot
     */
    @Test
    public void testScrollZoomY(FxRobot fxRobot) {
        fxRobot.interact(() -> zoomer.setAxisMode(AxisMode.Y));
        // assert initial axis configuration
        assertAxesZoom(-100, -100, 100, 100, fxRobot);
        // perform box zoom
        Point2D point = getPointFromChartCoordinates(50, -25, fxRobot);
        fxRobot.moveTo(point).scroll(1, VerticalDirection.UP);
        assertAxesZoom(-100, -92.5, 100, 87.5, fxRobot, TOLERANCE);
        // reset zoom (right click)
        fxRobot.clickOn(chart, MouseButton.SECONDARY);
        assertAxesZoom(-100, -100, 100, 100, fxRobot);
    }

    /**
     * Tests panning the zoom and also test disabling the panning functionality
     * 
     * @param fxRobot
     */
    @Test
    public void testPan(FxRobot fxRobot) {
        // assert initial axis configuration
        assertAxesZoom(-100, -100, 100, 100, fxRobot);
        // perform pan
        fxRobot.drag(getPointFromChartCoordinates(-50, 20, fxRobot), MouseButton.MIDDLE)
                .dropTo(getPointFromChartCoordinates(50, -30, fxRobot));
        // assert new axis ranges
        assertAxesZoom(-200, -50, 0, 150, fxRobot, 10);
        // reset pan (right click)
        fxRobot.clickOn(chart, MouseButton.SECONDARY);
        assertAxesZoom(-100, -100, 100, 100, fxRobot);
        // disable panning
        fxRobot.interact(() -> zoomer.setPannerEnabled(false));
        fxRobot.drag(getPointFromChartCoordinates(-50, 20, fxRobot), MouseButton.MIDDLE)
                .dropTo(getPointFromChartCoordinates(50, -30, fxRobot));
        assertAxesZoom(-100, -100, 100, 100, fxRobot);
    }

    @TestFx
    public void testProperties() {
        // AddButtonsToToolBar
        zoomer.setAddButtonsToToolBar(false);
        assertEquals(false, zoomer.isAddButtonsToToolBar());
        zoomer.setAddButtonsToToolBar(true);
        assertEquals(true, zoomer.isAddButtonsToToolBar());
        // Animated
        zoomer.setAnimated(false);
        assertEquals(false, zoomer.isAnimated());
        zoomer.setAnimated(true);
        assertEquals(true, zoomer.isAnimated());
        // AutoZoomEnabled
        zoomer.setAutoZoomEnabled(false);
        assertEquals(false, zoomer.isAutoZoomEnabled());
        zoomer.setAutoZoomEnabled(true);
        assertEquals(true, zoomer.isAutoZoomEnabled());
        // AutoZoomThreshold
        zoomer.setAutoZoomThreshold(42);
        assertEquals(42, zoomer.getAutoZoomThreshold());
        // AxisMode
        zoomer.setAxisMode(AxisMode.X);
        assertEquals(AxisMode.X, zoomer.getAxisMode());
        // Chart
        zoomer.setChart(null);
        assertEquals(null, zoomer.getChart());
        zoomer.setChart(chart);
        assertEquals(chart, zoomer.getChart());
        // DragCursor
        zoomer.setDragCursor(Cursor.TEXT);
        assertEquals(Cursor.TEXT, zoomer.getDragCursor());
        // PannerEnabled
        zoomer.setPannerEnabled(false);
        assertEquals(false, zoomer.isPannerEnabled());
        zoomer.setPannerEnabled(true);
        assertEquals(true, zoomer.isPannerEnabled());
        // SliderVisible
        zoomer.setSliderVisible(false);
        assertEquals(false, zoomer.isSliderVisible());
        zoomer.setSliderVisible(true);
        assertEquals(true, zoomer.isSliderVisible());
        // UpdateTickUnit
        zoomer.setUpdateTickUnit(false);
        assertEquals(false, zoomer.isUpdateTickUnit());
        zoomer.setUpdateTickUnit(true);
        assertEquals(true, zoomer.isUpdateTickUnit());
        // ZoomCursor
        zoomer.setZoomCursor(Cursor.TEXT);
        assertEquals(Cursor.TEXT, zoomer.getZoomCursor());
        // ZoomDuration
        zoomer.setZoomDuration(Duration.ZERO);
        assertEquals(Duration.ZERO, zoomer.getZoomDuration());
        // ZoomInMouseFilter
        zoomer.setZoomInMouseFilter(null);
        assertEquals(null, zoomer.getZoomInMouseFilter());
        // ZoomOriginMouseFilter
        zoomer.setZoomOriginMouseFilter(null);
        assertEquals(null, zoomer.getZoomOriginMouseFilter());
        // ZoomOutMouseFilter
        zoomer.setZoomOutMouseFilter(null);
        assertEquals(null, zoomer.getZoomOutMouseFilter());
        // ZoomScrollFilter
        zoomer.setZoomScrollFilter(null);
        assertEquals(null, zoomer.getZoomScrollFilter());
        // static omit zoom method
        Zoomer.setOmitZoom(xAxis, true);
        assertTrue(Zoomer.isOmitZoom(xAxis));
        assertTrue(Zoomer.isOmitZoom(ommittedYAxis));
        Zoomer.setOmitZoom(ommittedYAxis, false);
        assertFalse(Zoomer.isOmitZoom(ommittedYAxis));
    }

    /**
     * Helper Function which asserts that the chart is zoomed exactly to a certain data range
     * 
     * @param xMin minimum of x axis range
     * @param yMin minimum of y axis range
     * @param xMax maximum of x axis range
     * @param yMax maximum of y axis range
     * @param fxRobot the fx robot to interact with the scene
     */
    private void assertAxesZoom(double xMin, double yMin, double xMax, double yMax, FxRobot fxRobot) {
        assertAxesZoom(xMin, yMin, xMax, yMax, fxRobot, 0.0);
    }

    /**
     * Helper Function which asserts that the chart is zoomed to a certain data range
     * 
     * @param xMin minimum of x axis range
     * @param yMin minimum of y axis range
     * @param xMax maximum of x axis range
     * @param yMax maximum of y axis range
     * @param fxRobot the fx robot to interact with the scene
     * @param tolerance tolerance for the actual data range to differ from the given values in data units
     */
    private void assertAxesZoom(double xMin, double yMin, double xMax, double yMax, FxRobot fxRobot, double tolerance) {
        FxAssert.verifyThat(xAxis, (DefaultNumericAxis ax) -> {
            assertEquals(xMin, ax.getMin(), tolerance);
            assertEquals(xMax, ax.getMax(), tolerance);
            return true;
        }, DebugUtils.informedErrorMessage(fxRobot));
        FxAssert.verifyThat(yAxis, (DefaultNumericAxis ax) -> {
            assertEquals(yMin, ax.getMin(), tolerance);
            assertEquals(yMax, ax.getMax(), tolerance);
            return true;
        }, DebugUtils.informedErrorMessage(fxRobot));
        // assert that ignored axes are untouched
        FxAssert.verifyThat(ommittedXAxis, (DefaultNumericAxis ax) -> {
            assertEquals(-42, ax.getMin());
            assertEquals(42, ax.getMax());
            return true;
        }, DebugUtils.informedErrorMessage(fxRobot));
        FxAssert.verifyThat(ommittedYAxis, (DefaultNumericAxis ax) -> {
            assertEquals(-23, ax.getMin());
            assertEquals(23, ax.getMax());
            return true;
        }, DebugUtils.informedErrorMessage(fxRobot));
    }

    /**
     * Helper function which returns the screen coordinates of a point on the chart for passing it to the fxRobot
     * 
     * @param x X coordinate in data coordinates
     * @param y Y coordinate in data coordinates
     * @param fxRobot the fx robot to interact with the scene
     * @return a Point2D with the screen coordinates of the data point
     */
    private Point2D getPointFromChartCoordinates(double x, double y, FxRobot fxRobot) {
        double xScreen = xAxis.getDisplayPosition(x);
        double yScreen = yAxis.getDisplayPosition(y);
        return fxRobot.offset(chart.getPlotArea(), Pos.TOP_LEFT, new Point2D(xScreen, yScreen)).query();
    }
}

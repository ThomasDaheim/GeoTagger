/*
 * Copyright (c) 2014ff Thomas Feuster
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. The name of the author may not be used to endorse or promote products
 *    derived from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 * THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package tf.geotagger.main;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeoutException;
import javafx.scene.Node;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.framework.junit5.Stop;
import org.testfx.util.WaitForAsyncUtils;
import tf.exiftool.ExifTool;
import tf.geo.LatLon;
import tf.geotagger.metadata.ExifToolTestSupport;
import tf.geotagger.metadata.TestFiles;
import tf.geotagger.model.Field;
import tf.geotagger.model.MediaFile;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UI tests of the map: markers, selection, moving markers.
 */
@Tag("UI")
@ExtendWith(ApplicationExtension.class)
class GeoTaggerMapUiTest {
    private static Path exifTool;

    @TempDir
    Path dir;

    private GeoTaggerManager manager;
    private GeoTagger controller;

    @BeforeAll
    static void requireExifTool() {
        // without ExifTool GeoTagger would show a modal "ExifTool not found" - skip instead
        exifTool = ExifToolTestSupport.require();
    }

    @Start
    void start(Stage stage) throws IOException {
        UiTestSupport.preparePreferences(exifTool);
        manager = UiTestSupport.start(stage);
        controller = manager.controller();
    }

    @Stop
    void stop() {
        manager.stop();
    }

    // a.jpg at 46.5 / 11.5, b.jpg at 46.6 / 11.6, c.jpg without coordinates
    private void loadImages(FxRobot robot) throws TimeoutException {
        final Path a = TestFiles.image(dir, "a.jpg");
        final Path b = TestFiles.image(dir, "b.jpg");
        TestFiles.image(dir, "c.jpg");
        try (ExifTool tool = new ExifTool(exifTool)) {
            tool.execute(List.of("-overwrite_original", "-GPS:GPSLatitude#=46.5", "-GPS:GPSLatitudeRef#=N",
                    "-GPS:GPSLongitude#=11.5", "-GPS:GPSLongitudeRef#=E", a.toString()));
            tool.execute(List.of("-overwrite_original", "-GPS:GPSLatitude#=46.6", "-GPS:GPSLatitudeRef#=N",
                    "-GPS:GPSLongitude#=11.6", "-GPS:GPSLongitudeRef#=E", b.toString()));
        }
        robot.interact(() -> controller.loadFolder(dir, false));
        UiTestSupport.waitUntilIdle(controller);
        UiTestSupport.waitUntilMapReady(controller);
    }

    // runs the script on the JavaFX thread and returns its result (robot.interact returns the robot, not the result)
    private Object js(FxRobot robot, String script) {
        return WaitForAsyncUtils.waitFor(WaitForAsyncUtils.asyncFx(
                () -> controller.mapPane().mapView().getWebView().getEngine().executeScript(script)));
    }

    private static int number(Object value) {
        return ((Number) value).intValue();
    }

    // id of a file's marker as JavaScript string literal
    private String jsId(String name) {
        return "'" + dir.resolve(name).toString().replace("\\", "\\\\") + "'";
    }

    private static TableView<MediaFile> table(FxRobot robot) {
        return robot.lookup("#mediaTable").queryTableView();
    }

    private static MediaFile fileNamed(TableView<MediaFile> table, String name) {
        return table.getItems().stream()
                .filter(file -> file.path().getFileName().toString().equals(name))
                .findFirst().orElseThrow();
    }

    private static MediaFile select(FxRobot robot, String name) {
        final TableView<MediaFile> table = table(robot);
        final MediaFile file = fileNamed(table, name);
        robot.interact(() -> table.getSelectionModel().clearAndSelect(table.getItems().indexOf(file)));
        return file;
    }

    // replaces the text of a details field and presses Enter, like a user
    private static void enter(FxRobot robot, Field field, String text) {
        final TextField input = robot.lookup("#details-" + field.name()).queryAs(TextField.class);
        robot.clickOn(input);
        robot.interact(input::selectAll);
        robot.write(text).type(KeyCode.ENTER);
        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    void markersShownForFilesWithCoordinates(FxRobot robot) throws TimeoutException {
        loadImages(robot);
        assertEquals(2, number(js(robot, "geoMarkerCount()")));
    }

    @Test
    void selectingAFileMarksItsMarker(FxRobot robot) throws TimeoutException {
        loadImages(robot);
        select(robot, "a.jpg");
        assertEquals(1, number(js(robot, "geoClusteredCount()")));
        assertEquals(Boolean.TRUE, js(robot, "geoMarkers[" + jsId("a.jpg") + "].options.draggable"));
    }

    @Test
    void selectingFileWithoutCoordinatesKeepsTheMap(FxRobot robot) throws TimeoutException {
        loadImages(robot);
        final String view = "myMap.getCenter().lat + ',' + myMap.getZoom()";
        final Object before = js(robot, view);
        select(robot, "c.jpg");
        assertEquals(before, js(robot, view));
        robot.interact(() -> table(robot).getSelectionModel().selectAll());
        assertEquals(before, js(robot, view));
        assertEquals(2, number(js(robot, "geoMarkerCount()")));
    }

    @Test
    void clickingAMarkerSelectsTheFile(FxRobot robot) throws TimeoutException {
        loadImages(robot);
        js(robot, "geoMarkers[" + jsId("b.jpg") + "].fire('click')");
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(List.of(fileNamed(table(robot), "b.jpg")), List.copyOf(table(robot).getSelectionModel().getSelectedItems()));
    }

    @Test
    void draggingAMarkerSetsThePositionWithUndo(FxRobot robot) throws TimeoutException {
        loadImages(robot);
        final MediaFile file = select(robot, "a.jpg");
        js(robot, "var m = geoMarkers[" + jsId("a.jpg") + "]; m.setLatLng([47.25, 11.25]); m.fire('dragend')");
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(47.25, file.latitude(), 1e-9);
        assertEquals(11.25, file.longitude(), 1e-9);
        robot.clickOn("#undoButton");
        assertEquals(46.5, file.latitude(), 1e-9);
    }

    @Test
    void contextMenuSetsThePositionOfTheSelection(FxRobot robot) throws TimeoutException {
        loadImages(robot);
        js(robot, "myMap.setView([46.0, 11.0], 16, {animate: false})");
        robot.interact(() -> table(robot).getSelectionModel().selectAll());
        final WebView web = controller.mapPane().mapView().getWebView();
        // the robot right-clicks the centre of the WebView
        final LatLon expected = WaitForAsyncUtils.waitFor(WaitForAsyncUtils.asyncFx(
                () -> controller.mapPane().mapView().latLonAt(web.getWidth() / 2, web.getHeight() / 2))).orElseThrow();

        robot.rightClickOn(web);
        robot.clickOn("#setPositionHere");

        for (MediaFile file : table(robot).getItems()) {
            assertEquals(expected.latitude(), file.latitude(), 1e-3);
            assertEquals(expected.longitude(), file.longitude(), 1e-3);
        }
        robot.clickOn("#undoButton");
        assertFalse(fileNamed(table(robot), "c.jpg").hasCoordinates());
    }

    @Test
    void draggingFilesOntoTheMapSetsTheirPosition(FxRobot robot) throws TimeoutException {
        loadImages(robot);
        js(robot, "myMap.setView([46.0, 11.0], 16, {animate: false})");
        final MediaFile file = select(robot, "c.jpg");
        final Node row = robot.lookup(".table-row-cell").match((Node node) -> ((TableRow<?>) node).getItem() == file).query();

        robot.drag(row).dropTo(controller.mapPane().mapView().getWebView());
        WaitForAsyncUtils.waitForFxEvents();

        assertTrue(file.hasCoordinates());
        assertEquals(46.0, file.latitude(), 0.05);
    }

    @Test
    void editingCoordinatesMovesTheMarker(FxRobot robot) throws TimeoutException {
        loadImages(robot);
        select(robot, "a.jpg");
        enter(robot, Field.LATITUDE, "45.5");
        assertEquals(45.5, ((Number) js(robot, "geoMarkers[" + jsId("a.jpg") + "].getLatLng().lat")).doubleValue(), 1e-9);
    }
}
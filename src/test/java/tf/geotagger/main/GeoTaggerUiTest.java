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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javafx.scene.control.Label;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.ImageView;
import javafx.scene.text.Text;
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
import tf.geotagger.metadata.ExifToolTestSupport;
import tf.geotagger.metadata.TestFiles;
import tf.geotagger.model.MediaFile;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UI tests of the main window: start, loading a folder, filters.
 */
@Tag("UI")
@ExtendWith(ApplicationExtension.class)
class GeoTaggerUiTest {
    private static Path exifTool;

    @TempDir
    Path dir;

    private Stage stage;
    private GeoTaggerManager manager;
    private GeoTagger controller;

    @BeforeAll
    static void requireExifTool() {
        // without ExifTool GeoTagger would show a modal "ExifTool not found" - skip instead
        exifTool = ExifToolTestSupport.require();
    }

    @Start
    void start(Stage stage) throws IOException {
        this.stage = stage;
        UiTestSupport.preparePreferences(exifTool);
        // column widths saved by plan 4 / 4b under the old key - the columns have changed since (see oldColumnSettingsAreIgnored)
        for (int column = 0; column < 6; column++) {
            GeoTaggerPreferences.INSTANCE.put("mediaTable-ColumnWidth-" + column, "45");
        }
        manager = UiTestSupport.start(stage);
        controller = manager.controller();
    }

    @Stop
    void stop() {
        manager.stop();
    }

    // a.jpg with GPS and city, b.jpg and c.png without
    private void loadThreeImages(FxRobot robot) throws TimeoutException {
        final Path withGps = TestFiles.image(dir, "a.jpg");
        TestFiles.image(dir, "b.jpg");
        TestFiles.image(dir, "c.png");
        try (ExifTool tool = new ExifTool(exifTool)) {
            tool.execute(List.of("-overwrite_original", "-GPS:GPSLatitude#=46.5", "-GPS:GPSLatitudeRef#=N",
                    "-GPS:GPSLongitude#=11.5", "-GPS:GPSLongitudeRef#=E", "-XMP-photoshop:City=Meran", withGps.toString()));
        }
        robot.interact(() -> controller.loadFolder(dir, false));
        UiTestSupport.waitUntilIdle(controller);
    }

    @Test
    void imageInfoTabKeepsTheMap(FxRobot robot) throws TimeoutException {
        loadThreeImages(robot);
        UiTestSupport.waitUntilMapReady(controller);
        final TableView<MediaFile> table = robot.lookup("#mediaTable").queryTableView();
        robot.interact(() -> table.getSelectionModel().clearAndSelect(table.getItems().indexOf(fileNamed(table, "a.jpg"))));
        final TabPane tabs = robot.lookup("#infoTabs").queryAs(TabPane.class);
        final Object markers = mapScript("geoMarkerCount()");

        robot.interact(() -> tabs.getSelectionModel().select(tabs.getTabs().stream()
                .filter(tab -> "imageInfoTab".equals(tab.getId())).findFirst().orElseThrow()));
        @SuppressWarnings("unchecked")
        final TableView<Map.Entry<String, String>> info = robot.lookup("#imageInfoPane .table-view").queryAs(TableView.class);
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> info.getItems().stream().anyMatch(row -> "Meran".equals(row.getValue())));

        robot.interact(() -> tabs.getSelectionModel().select(tabs.getTabs().stream()
                .filter(tab -> "mapTab".equals(tab.getId())).findFirst().orElseThrow()));
        // no waiting for the map to load again
        assertEquals(markers, mapScript("geoMarkerCount()"));
    }

    private Object mapScript(String script) {
        return WaitForAsyncUtils.waitFor(WaitForAsyncUtils.asyncFx(
                () -> controller.mapPane().mapView().getWebView().getEngine().executeScript(script)));
    }

    private static MediaFile fileNamed(TableView<MediaFile> table, String name) {
        return table.getItems().stream()
                .filter(file -> file.path().getFileName().toString().equals(name))
                .findFirst().orElseThrow();
    }

    @Test
    void startShowsAnEmptyList(FxRobot robot) {
        assertEquals("GeoTagger", stage.getTitle());
        assertEquals("All (0)", robot.lookup("#filter-ALL").queryAs(ToggleButton.class).getText());
        assertFalse(robot.lookup("#openFolderButton").query().isDisabled());
        assertTrue(robot.lookup("#mediaTable").queryTableView().getItems().isEmpty());
    }

    @Test
    void oldColumnSettingsAreIgnored(FxRobot robot) {
        // the old width of the 3rd column (45) would make the coordinates unreadable
        final TableView<MediaFile> table = robot.lookup("#mediaTable").queryTableView();
        final TableColumn<MediaFile, ?> coordinates = table.getColumns().stream()
                .filter(column -> "coordinatesColumn".equals(column.getId())).findFirst().orElseThrow();
        assertTrue(coordinates.getWidth() > 100, "width " + coordinates.getWidth());
    }

    @Test
    void valueColumnsShowTheirHeaders(FxRobot robot) {
        final String[][] headers = {{"coordinatesColumn", "Coordinates"}, {"altitudeColumn", "Altitude"},
            {"cityColumn", "City"}, {"timeOffsetColumn", "Time zone"}};
        for (String[] header : headers) {
            // the text actually drawn - a column too narrow shows "..." or nothing
            final Text shown = robot.lookup("#" + header[0] + " .label .text").queryAs(Text.class);
            assertEquals(header[1], shown.getText());
        }
    }

    @Test
    void loadingFolderFillsListAndCounts(FxRobot robot) throws TimeoutException {
        loadThreeImages(robot);

        final TableView<MediaFile> table = robot.lookup("#mediaTable").queryTableView();
        assertEquals(3, table.getItems().size());
        assertEquals("All (3)", robot.lookup("#filter-ALL").queryAs(ToggleButton.class).getText());
        assertEquals("Without coordinates (2)", robot.lookup("#filter-WITHOUT_COORDINATES").queryAs(ToggleButton.class).getText());
        assertEquals("Without place (2)", robot.lookup("#filter-WITHOUT_PLACE").queryAs(ToggleButton.class).getText());
        assertEquals("3 files (1 with coordinates) - 1 selected", robot.lookup("#statusLabel").queryAs(Label.class).getText());
        // columns aren't nodes - found via the table, the cell data is the shown text
        assertEquals("N46°30'0.00\"; E11°30'0.00\"", cellData(table, "coordinatesColumn", "a.jpg"));
        assertEquals("", cellData(table, "coordinatesColumn", "b.jpg"));
        assertEquals("Meran", cellData(table, "cityColumn", "a.jpg"));
    }

    private static Object cellData(TableView<MediaFile> table, String columnId, String fileName) {
        final TableColumn<MediaFile, ?> column = table.getColumns().stream()
                .filter(candidate -> columnId.equals(candidate.getId())).findFirst().orElseThrow();
        return column.getCellData(fileNamed(table, fileName));
    }

    @Test
    void loadingTwiceKeepsOnlyTheSecondFolder(FxRobot robot) throws IOException, TimeoutException, InterruptedException {
        final Path first = Files.createDirectory(dir.resolve("first"));
        final Path second = Files.createDirectory(dir.resolve("second"));
        for (int i = 0; i < 120; i++) {
            TestFiles.image(first, String.format("f%03d.jpg", i));
        }
        TestFiles.image(second, "s.jpg");
        final TableView<MediaFile> table = robot.lookup("#mediaTable").queryTableView();

        robot.interact(() -> {
            controller.loadFolder(first, false);
            controller.loadFolder(second, false);
        });
        WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS, () -> !controller.isBusy()
                && table.getItems().stream().anyMatch(file -> file.path().getFileName().toString().equals("s.jpg")));
        // late chunks of the first folder would arrive now
        Thread.sleep(500);
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals(List.of("s.jpg"), table.getItems().stream().map(file -> file.path().getFileName().toString()).toList());
    }

    @Test
    void smallPreviewsAreNotEnlarged(FxRobot robot) throws TimeoutException {
        loadThreeImages(robot);
        final TableView<MediaFile> table = robot.lookup("#mediaTable").queryTableView();
        final MediaFile file = fileNamed(table, "b.jpg");
        robot.interact(() -> table.getSelectionModel().clearAndSelect(table.getItems().indexOf(file)));

        // e.g. the 160 x 120 thumbnail of a video - scaled up it only gets blurred
        final ImageView preview = robot.lookup("#previewPane .image-view").queryAs(ImageView.class);
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS,
                () -> preview.getImage() != null && preview.getImage().getProgress() >= 1.0);
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(16.0, preview.getLayoutBounds().getWidth(), 0.5);
    }

    @Test
    void filterShowsOnlyMatchingFiles(FxRobot robot) throws TimeoutException {
        loadThreeImages(robot);

        robot.clickOn("#filter-WITHOUT_COORDINATES");
        assertEquals(2, robot.lookup("#mediaTable").queryTableView().getItems().size());

        robot.clickOn("#filter-ALL");
        assertEquals(3, robot.lookup("#mediaTable").queryTableView().getItems().size());
    }
}

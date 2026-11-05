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
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.ToggleButton;
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
    void statusColumnsShowTheirHeaders(FxRobot robot) {
        final String[][] headers = {{"coordinatesStatusColumn", "GPS"}, {"placeStatusColumn", "Loc."},
            {"altitudeStatusColumn", "Alt."}};
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
        assertEquals("3 files in " + dir, robot.lookup("#statusLabel").queryAs(Label.class).getText());
        // columns aren't nodes - found via the table, the cell data is the shown text
        final TableColumn<MediaFile, ?> gpsColumn = table.getColumns().stream()
                .filter(column -> "coordinatesStatusColumn".equals(column.getId())).findFirst().orElseThrow();
        assertEquals("✓", gpsColumn.getCellData(fileNamed(table, "a.jpg")));
        assertEquals("–", gpsColumn.getCellData(fileNamed(table, "b.jpg")));
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

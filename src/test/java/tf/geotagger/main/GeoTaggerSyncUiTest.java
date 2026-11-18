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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javafx.scene.Node;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
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
import tf.geotagger.model.Field;
import tf.geotagger.model.MediaFile;
import tf.geotagger.sync.SyncResult;
import tf.geotagger.track.GpxTrack;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UI tests of the track sync dialog - see plan 6.
 */
@Tag("UI")
@ExtendWith(ApplicationExtension.class)
class GeoTaggerSyncUiTest {
    private static final double DELTA = 1e-6;
    // 09:00Z at 46.0 / 11.0 (1000 m), 09:10Z at 46.1 / 11.2 (1100 m)
    static final String TOUR = """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1" creator="test" xmlns="http://www.topografix.com/GPX/1/1">
              <trk><trkseg>
                <trkpt lat="46.0" lon="11.0"><ele>1000</ele><time>2026-09-02T09:00:00Z</time></trkpt>
                <trkpt lat="46.1" lon="11.2"><ele>1100</ele><time>2026-09-02T09:10:00Z</time></trkpt>
              </trkseg></trk>
            </gpx>
            """;

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

    // tour.gpx; a.jpg 11:05 (Berlin = 09:05Z, between the points), b.jpg 18:00 (no point near), c.jpg 11:05 with
    // coordinates in Rome
    private void loadFolder(FxRobot robot) throws IOException, TimeoutException {
        Files.writeString(dir.resolve("tour.gpx"), TOUR, StandardCharsets.UTF_8);
        final Path a = TestFiles.image(dir, "a.jpg");
        final Path b = TestFiles.image(dir, "b.jpg");
        final Path c = TestFiles.image(dir, "c.jpg");
        try (ExifTool tool = new ExifTool(exifTool)) {
            tool.execute(List.of("-overwrite_original", "-ExifIFD:DateTimeOriginal=2026:09:02 11:05:00", a.toString()));
            tool.execute(List.of("-overwrite_original", "-ExifIFD:DateTimeOriginal=2026:09:02 18:00:00", b.toString()));
            tool.execute(List.of("-overwrite_original", "-ExifIFD:DateTimeOriginal=2026:09:02 11:05:00",
                    "-GPS:GPSLatitude#=41.9", "-GPS:GPSLatitudeRef#=N", "-GPS:GPSLongitude#=12.5", "-GPS:GPSLongitudeRef#=E",
                    c.toString()));
        }
        robot.interact(() -> controller.loadFolder(dir, false));
        UiTestSupport.waitUntilIdle(controller);
    }

    // checks tour.gpx in the track list and waits until it is loaded
    static void loadTour(FxRobot robot) throws TimeoutException {
        final ListView<GpxTrack> list = robot.lookup("#trackList").queryListView();
        final GpxTrack tour = list.getItems().stream()
                .filter(track -> track.file().getFileName().toString().equals("tour.gpx"))
                .findFirst().orElseThrow();
        robot.interact(() -> tour.shownProperty().set(true));
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> tour.state() == GpxTrack.State.LOADED);
        WaitForAsyncUtils.waitForFxEvents();
    }

    private static TableView<MediaFile> table(FxRobot robot) {
        return robot.lookup("#mediaTable").queryTableView();
    }

    static MediaFile fileNamed(FxRobot robot, String name) {
        return table(robot).getItems().stream()
                .filter(file -> file.path().getFileName().toString().equals(name))
                .findFirst().orElseThrow();
    }

    private static void selectOnly(FxRobot robot, String... names) {
        final TableView<MediaFile> table = table(robot);
        robot.interact(() -> {
            table.getSelectionModel().clearSelection();
            for (String name : names) {
                table.getSelectionModel().select(fileNamed(robot, name));
            }
        });
    }

    // opens the dialog and sets the camera zone to Berlin - the suggestion would be the zone of the test machine
    static void openSync(FxRobot robot) throws TimeoutException {
        robot.clickOn("#trackSyncButton");
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS,
                () -> robot.lookup("#syncApply").tryQuery().map(Node::isVisible).orElse(false));
        setZone(robot, "Europe/Berlin");
    }

    static void setZone(FxRobot robot, String zone) {
        final ComboBox<?> box = robot.lookup("#syncZone").queryComboBox();
        robot.interact(() -> box.getEditor().setText(zone));
        WaitForAsyncUtils.waitForFxEvents();
    }

    static void setText(FxRobot robot, String id, String text) {
        final TextField field = robot.lookup(id).queryAs(TextField.class);
        robot.interact(() -> field.setText(text));
        WaitForAsyncUtils.waitForFxEvents();
    }

    static void setSign(FxRobot robot, String sign) {
        final ChoiceBox<?> box = robot.lookup("#syncOffsetSign").queryAs(ChoiceBox.class);
        robot.interact(() -> box.getSelectionModel().select(sign.equals("-") ? 1 : 0));
        WaitForAsyncUtils.waitForFxEvents();
    }

    static void click(FxRobot robot, String id) {
        final Node node = robot.lookup(id).query();
        robot.interact(() -> {
            if (node instanceof ToggleButton toggle) {
                toggle.fire();
            } else if (node instanceof javafx.scene.control.ButtonBase button) {
                button.fire();
            }
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    static List<SyncResult> preview(FxRobot robot) {
        final TableView<SyncResult> preview = robot.lookup("#syncPreview").queryTableView();
        return List.copyOf(preview.getItems());
    }

    static SyncResult row(FxRobot robot, String name) {
        return preview(robot).stream()
                .filter(result -> result.file().path().getFileName().toString().equals(name))
                .findFirst().orElseThrow();
    }

    static String summary(FxRobot robot) {
        return robot.lookup("#syncSummary").queryAs(Label.class).getText();
    }

    static boolean applyDisabled(FxRobot robot) {
        return robot.lookup("#syncApply").queryButton().isDisabled();
    }

    static void apply(FxRobot robot) {
        click(robot, "#syncApply");
        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    void syncAssignsPositionsFromLoadedTrack(FxRobot robot) throws IOException, TimeoutException {
        loadFolder(robot);
        loadTour(robot);
        selectOnly(robot);

        openSync(robot);

        final SyncResult a = row(robot, "a.jpg");
        assertEquals(SyncResult.Status.MATCHED, a.status());
        assertEquals(46.05, a.latitude(), DELTA);
        assertEquals(11.1, a.longitude(), DELTA);
        assertEquals(SyncResult.Status.NO_MATCH, row(robot, "b.jpg").status());
        // c.jpg has coordinates - not part of "Without coordinates"
        assertEquals(2, preview(robot).size());
        assertEquals("1 matched, 1 without match", summary(robot));
        assertEquals(1, robot.lookup(".sync-matched").queryAll().size());
        assertEquals(1, robot.lookup(".sync-no-match").queryAll().size());

        apply(robot);

        final MediaFile file = fileNamed(robot, "a.jpg");
        assertEquals(46.05, file.latitude(), DELTA);
        assertEquals(11.1, file.longitude(), DELTA);
        assertEquals(1050.0, file.altitude(), DELTA);
        assertFalse(fileNamed(robot, "b.jpg").isChanged());
        assertEquals("Track sync: positions for 1 file", robot.lookup("#statusLabel").queryAs(Label.class).getText());
    }

    @Test
    void applyIsOneUndoStep(FxRobot robot) throws IOException, TimeoutException {
        loadFolder(robot);
        loadTour(robot);
        selectOnly(robot);
        openSync(robot);
        click(robot, "#syncWriteZone");

        apply(robot);

        final MediaFile a = fileNamed(robot, "a.jpg");
        assertEquals(ZoneOffset.ofHours(2), a.timeOffset());
        robot.clickOn("#undoButton");
        assertFalse(a.hasCoordinates());
        assertNull(a.altitude());
        assertNull(a.timeOffset());
        assertTrue(robot.lookup("#undoButton").queryButton().isDisabled());
    }

    @Test
    void offsetChangesThePreviewLive(FxRobot robot) throws IOException, TimeoutException {
        loadFolder(robot);
        loadTour(robot);
        selectOnly(robot);
        openSync(robot);

        // camera 7 h ahead: a.jpg was taken at 02:05Z - no track point near; b.jpg (18:00 Berlin) at 09:00Z - on the
        // first point
        setText(robot, "#syncOffsetHours", "7");
        assertEquals(SyncResult.Status.NO_MATCH, row(robot, "a.jpg").status());
        assertEquals(SyncResult.Status.MATCHED, row(robot, "b.jpg").status());

        // camera 7 h behind: nothing near at all
        setSign(robot, "-");
        assertEquals(SyncResult.Status.NO_MATCH, row(robot, "b.jpg").status());
        assertTrue(applyDisabled(robot));

        setText(robot, "#syncOffsetHours", "0");
        assertEquals(SyncResult.Status.MATCHED, row(robot, "a.jpg").status());
        assertFalse(applyDisabled(robot));
    }

    @Test
    void selectionWithoutOverwriteKeepsCoordinates(FxRobot robot) throws IOException, TimeoutException {
        loadFolder(robot);
        loadTour(robot);
        selectOnly(robot, "a.jpg", "c.jpg");
        openSync(robot);

        // own selection: "Selection" is preselected (plan 6)
        assertTrue(robot.lookup("#syncFilesSelection").queryAs(RadioButton.class).isSelected());
        assertEquals(List.of("a.jpg"), preview(robot).stream().map(result -> result.file().path().getFileName().toString()).toList());
        assertEquals("1 matched - 1 skipped (they have coordinates)", summary(robot));

        click(robot, "#syncOverwrite");
        assertEquals(2, preview(robot).size());
        apply(robot);

        final MediaFile c = fileNamed(robot, "c.jpg");
        assertEquals(46.05, c.latitude(), DELTA);
        assertEquals(11.1, c.longitude(), DELTA);
    }

    @Test
    void invalidZoneBlocksApply(FxRobot robot) throws IOException, TimeoutException {
        loadFolder(robot);
        loadTour(robot);
        selectOnly(robot);
        openSync(robot);
        assertFalse(applyDisabled(robot));

        setZone(robot, "Europe/Berlinn");

        assertTrue(applyDisabled(robot));
        assertTrue(robot.lookup("#syncZone").query().getStyleClass().contains("invalid"));

        setText(robot, "#syncOffsetMinutes", "x");
        setZone(robot, "Europe/Berlin");
        assertTrue(applyDisabled(robot));
        assertTrue(robot.lookup("#syncOffsetMinutes").query().getStyleClass().contains("invalid"));
    }

    @Test
    void cancelChangesNothing(FxRobot robot) throws IOException, TimeoutException {
        loadFolder(robot);
        loadTour(robot);
        selectOnly(robot);
        openSync(robot);

        click(robot, "#syncCancel");

        assertFalse(fileNamed(robot, "a.jpg").isChanged());
        assertTrue(robot.lookup("#undoButton").queryButton().isDisabled());
    }

    @Test
    void trackSyncIsDisabledWithoutFolder(FxRobot robot) {
        assertTrue(robot.lookup("#trackSyncButton").queryButton().isDisabled());
    }
}

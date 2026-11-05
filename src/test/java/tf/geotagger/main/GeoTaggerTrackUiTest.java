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
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javafx.scene.control.ListView;
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
import org.testfx.matcher.control.LabeledMatchers;
import org.testfx.util.WaitForAsyncUtils;
import tf.geotagger.metadata.ExifToolTestSupport;
import tf.geotagger.metadata.TestFiles;
import tf.geotagger.track.GpxTrack;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UI tests of the track list: GPX files of the folder, showing them on the map, unreadable files.
 */
@Tag("UI")
@ExtendWith(ApplicationExtension.class)
class GeoTaggerTrackUiTest {
    private static final String TOUR = """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1" creator="test" xmlns="http://www.topografix.com/GPX/1/1">
              <trk><trkseg>
                <trkpt lat="46.50" lon="11.50"><time>2026-11-04T10:00:00Z</time></trkpt>
                <trkpt lat="46.51" lon="11.51"><time>2026-11-04T10:01:00Z</time></trkpt>
                <trkpt lat="46.52" lon="11.52"><time>2026-11-04T10:02:00Z</time></trkpt>
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

    // a.jpg, tour.gpx with three points, broken.gpx that isn't XML
    private void loadFolder(FxRobot robot) throws IOException, TimeoutException {
        TestFiles.image(dir, "a.jpg");
        Files.writeString(dir.resolve("tour.gpx"), TOUR, StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("broken.gpx"), "no xml", StandardCharsets.UTF_8);
        robot.interact(() -> controller.loadFolder(dir, false));
        UiTestSupport.waitUntilIdle(controller);
        UiTestSupport.waitUntilMapReady(controller);
    }

    private Object js(String script) {
        return WaitForAsyncUtils.waitFor(WaitForAsyncUtils.asyncFx(
                () -> controller.mapPane().mapView().getWebView().getEngine().executeScript(script)));
    }

    private static int number(Object value) {
        return ((Number) value).intValue();
    }

    private static ListView<GpxTrack> trackList(FxRobot robot) {
        return robot.lookup("#trackList").queryListView();
    }

    private static GpxTrack track(FxRobot robot, String name) {
        return trackList(robot).getItems().stream()
                .filter(track -> track.file().getFileName().toString().equals(name))
                .findFirst().orElseThrow();
    }

    @Test
    void gpxFilesOfTheFolderAreListed(FxRobot robot) throws IOException, TimeoutException {
        loadFolder(robot);
        assertEquals(List.of("broken.gpx", "tour.gpx"), trackList(robot).getItems().stream()
                .map(track -> track.file().getFileName().toString()).sorted().toList());
        assertTrue(trackList(robot).getItems().stream().noneMatch(track -> track.shownProperty().get()));
    }

    @Test
    void checkingATrackShowsItOnTheMap(FxRobot robot) throws IOException, TimeoutException {
        loadFolder(robot);
        final GpxTrack tour = track(robot, "tour.gpx");
        robot.interact(() -> tour.shownProperty().set(true));
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> tour.state() == GpxTrack.State.LOADED);
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(1, number(js("geoVisibleTrackCount()")));

        robot.interact(() -> tour.shownProperty().set(false));
        assertEquals(0, number(js("geoVisibleTrackCount()")));
        assertEquals(1, number(js("geoTrackCount()")));
    }

    @Test
    void unreadableGpxIsMarked(FxRobot robot) throws IOException, TimeoutException {
        loadFolder(robot);
        final GpxTrack broken = track(robot, "broken.gpx");
        robot.interact(() -> broken.shownProperty().set(true));
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> broken.state() == GpxTrack.State.FAILED);
        WaitForAsyncUtils.waitForFxEvents();
        assertFalse(broken.shownProperty().get());
        assertEquals(0, number(js("geoTrackCount()")));
        assertTrue(robot.lookup(".list-cell").match(LabeledMatchers.hasText("broken.gpx (unreadable)")).tryQuery().isPresent());
    }
}
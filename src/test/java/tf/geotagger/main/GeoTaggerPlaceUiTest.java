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
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
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
import tf.geo.service.Place;
import tf.geo.service.ReverseGeocoder;
import tf.geotagger.enrich.FakeGeocoder;
import tf.geotagger.metadata.ExifToolTestSupport;
import tf.geotagger.metadata.TestFiles;
import tf.geotagger.model.Field;
import tf.geotagger.model.MediaFile;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UI tests of "Find place" - with a fake service, no network.
 */
@Tag("UI")
@ExtendWith(ApplicationExtension.class)
class GeoTaggerPlaceUiTest {
    private static final Place MERAN = new Place("IT", "Italien", "Trentino-Südtirol", "Meran", null);

    private static Path exifTool;

    @TempDir
    Path dir;

    private GeoTaggerManager manager;
    private GeoTagger controller;
    private final FakeGeocoder geocoder = new FakeGeocoder(Duration.ZERO, MERAN);

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
        controller.setPlaceService(() -> geocoder);
    }

    @Stop
    void stop() {
        manager.stop();
    }

    // a.jpg with coordinates and without place, b.jpg with coordinates and place (Bozen / Altstadt), c.jpg without coordinates
    private void loadImages(FxRobot robot) throws TimeoutException {
        final Path a = TestFiles.image(dir, "a.jpg");
        final Path b = TestFiles.image(dir, "b.jpg");
        TestFiles.image(dir, "c.jpg");
        try (ExifTool tool = new ExifTool(exifTool)) {
            tool.execute(List.of("-overwrite_original", "-GPS:GPSLatitude#=46.5", "-GPS:GPSLatitudeRef#=N",
                    "-GPS:GPSLongitude#=11.5", "-GPS:GPSLongitudeRef#=E", a.toString()));
            tool.execute(List.of("-overwrite_original", "-GPS:GPSLatitude#=46.6", "-GPS:GPSLatitudeRef#=N",
                    "-GPS:GPSLongitude#=11.6", "-GPS:GPSLongitudeRef#=E", "-XMP-photoshop:City=Bozen",
                    "-XMP-iptcCore:Location=Altstadt", b.toString()));
        }
        robot.interact(() -> controller.loadFolder(dir, false));
        UiTestSupport.waitUntilIdle(controller);
    }

    private static TableView<MediaFile> table(FxRobot robot) {
        return robot.lookup("#mediaTable").queryTableView();
    }

    private static MediaFile fileNamed(FxRobot robot, String name) {
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

    private void findPlaces(FxRobot robot) throws TimeoutException {
        robot.clickOn("#findPlaceButton");
        UiTestSupport.waitUntilIdle(controller);
    }

    private static String status(FxRobot robot) {
        return robot.lookup("#statusLabel").queryAs(Label.class).getText();
    }

    @Test
    void findPlaceIsDisabledWithoutFolder(FxRobot robot) {
        assertTrue(robot.lookup("#findPlaceButton").queryButton().isDisabled());
    }

    @Test
    void findPlaceFillsListedFilesWithoutPlace(FxRobot robot) throws TimeoutException {
        loadImages(robot);
        selectOnly(robot);

        findPlaces(robot);

        final MediaFile a = fileNamed(robot, "a.jpg");
        assertEquals("IT", a.value(Field.COUNTRY_CODE));
        assertEquals("Italien", a.value(Field.COUNTRY));
        assertEquals("Trentino-Südtirol", a.value(Field.STATE));
        assertEquals("Meran", a.value(Field.CITY));
        assertNull(a.value(Field.SUBLOCATION));
        assertEquals("Bozen", fileNamed(robot, "b.jpg").value(Field.CITY));
        assertFalse(fileNamed(robot, "c.jpg").isChanged());
        assertEquals(1, geocoder.requests().size());
        assertEquals("Places found for 1 file", status(robot));

        // one undo step for the whole run
        robot.clickOn("#undoButton");
        assertFalse(a.hasPlace());
        assertTrue(robot.lookup("#undoButton").queryButton().isDisabled());
    }

    @Test
    void automaticSelectionAfterLoadingDoesNotCount(FxRobot robot) throws TimeoutException {
        // user decision 15.11.2026: the first file selected by GeoTagger after loading is no selection for "Find place"
        final Path a = TestFiles.image(dir, "a.jpg");
        final Path b = TestFiles.image(dir, "b.jpg");
        try (ExifTool tool = new ExifTool(exifTool)) {
            tool.execute(List.of("-overwrite_original", "-GPS:GPSLatitude#=46.6", "-GPS:GPSLatitudeRef#=N",
                    "-GPS:GPSLongitude#=11.6", "-GPS:GPSLongitudeRef#=E", "-XMP-photoshop:City=Bozen", a.toString()));
            tool.execute(List.of("-overwrite_original", "-GPS:GPSLatitude#=46.5", "-GPS:GPSLatitudeRef#=N",
                    "-GPS:GPSLongitude#=11.5", "-GPS:GPSLongitudeRef#=E", b.toString()));
        }
        robot.interact(() -> controller.loadFolder(dir, false));
        UiTestSupport.waitUntilIdle(controller);
        assertEquals(List.of(fileNamed(robot, "a.jpg")), List.copyOf(table(robot).getSelectionModel().getSelectedItems()));

        findPlaces(robot);

        assertEquals("Bozen", fileNamed(robot, "a.jpg").value(Field.CITY));
        assertEquals("Meran", fileNamed(robot, "b.jpg").value(Field.CITY));
    }

    @Test
    void findPlaceOverwritesSelectedFiles(FxRobot robot) throws TimeoutException {
        loadImages(robot);
        selectOnly(robot, "b.jpg");

        findPlaces(robot);

        final MediaFile b = fileNamed(robot, "b.jpg");
        assertEquals("Meran", b.value(Field.CITY));
        assertEquals("Italien", b.value(Field.COUNTRY));
        // the old sublocation doesn't belong to the new place
        assertNull(b.value(Field.SUBLOCATION));
        assertFalse(fileNamed(robot, "a.jpg").hasPlace());
    }

    @Test
    void mapEditsWaitWhileFindingPlaces(FxRobot robot) throws TimeoutException {
        // the place found for the old position must not land on a file moved meanwhile (final review of plan 5)
        final CountDownLatch release = new CountDownLatch(1);
        final ReverseGeocoder blocking = new ReverseGeocoder() {
            @Override
            public Optional<Place> reverse(LatLon position) {
                try {
                    release.await(20, TimeUnit.SECONDS);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
                return Optional.of(MERAN);
            }

            @Override
            public Duration minRequestInterval() {
                return Duration.ZERO;
            }

            @Override
            public String name() {
                return "Blocking";
            }
        };
        loadImages(robot);
        UiTestSupport.waitUntilMapReady(controller);
        selectOnly(robot);
        robot.interact(() -> controller.setPlaceService(() -> blocking));
        robot.clickOn("#findPlaceButton");
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, controller::isBusy);

        final String marker = "geoMarkers['" + dir.resolve("a.jpg").toString().replace("\\", "\\\\") + "']";
        js(marker + ".setLatLng([41.9, 12.5]); " + marker + ".fire('dragend')");
        WaitForAsyncUtils.waitForFxEvents();

        final MediaFile a = fileNamed(robot, "a.jpg");
        assertEquals(46.5, a.latitude(), 1e-9);
        // the marker is back at the position of the file
        assertEquals(46.5, ((Number) js(marker + ".getLatLng().lat")).doubleValue(), 1e-9);

        release.countDown();
        UiTestSupport.waitUntilIdle(controller);
        assertEquals("Meran", a.value(Field.CITY));
    }

    // runs the script on the JavaFX thread and returns its result
    private Object js(String script) {
        return WaitForAsyncUtils.waitFor(WaitForAsyncUtils.asyncFx(
                () -> controller.mapPane().mapView().getWebView().getEngine().executeScript(script)));
    }

    @Test
    void findPlaceIsDisabledWithNothingToLookUp(FxRobot robot) throws TimeoutException {
        // user feedback 16.11.2026: a file without coordinates selected - the button shouldn't be usable
        loadImages(robot);
        selectOnly(robot, "c.jpg");
        assertTrue(robot.lookup("#findPlaceButton").queryButton().isDisabled());

        selectOnly(robot, "a.jpg");
        assertFalse(robot.lookup("#findPlaceButton").queryButton().isDisabled());
        assertTrue(geocoder.requests().isEmpty());
    }
}

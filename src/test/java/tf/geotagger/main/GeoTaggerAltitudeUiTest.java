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
import java.io.OutputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.concurrent.TimeoutException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
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
import tf.exiftool.ExifTool;
import tf.geo.service.SrtmDownloader;
import tf.geo.service.SrtmElevationProvider;
import tf.geo.service.SrtmInterpolation;
import tf.geotagger.enrich.AltitudeEnricher;
import tf.geotagger.enrich.AltitudeSource;
import tf.geotagger.enrich.FakeElevationProvider;
import tf.geotagger.metadata.ExifToolTestSupport;
import tf.geotagger.metadata.TestFiles;
import tf.geotagger.model.MediaFile;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UI tests of "Find altitude" - SRTM tile made up in a temporary folder, fakes for openrouteservice and the download,
 * no network.
 */
@Tag("UI")
@ExtendWith(ApplicationExtension.class)
class GeoTaggerAltitudeUiTest {
    private static final String TILE = "N46E011";
    // the made up tile has this altitude everywhere
    private static final double TILE_ALTITUDE = 1000.0;
    private static final double ORS_ALTITUDE = 262.0;

    private static Path exifTool;

    @TempDir
    Path dir;
    @TempDir
    Path srtmFolder;

    private GeoTaggerManager manager;
    private GeoTagger controller;
    private final FakeElevationProvider ors = new FakeElevationProvider("ORS", position -> OptionalDouble.of(ORS_ALTITUDE));
    private final List<List<String>> questions = new ArrayList<>();
    private final List<URI> downloads = new ArrayList<>();
    private boolean answer = true;

    @BeforeAll
    static void requireExifTool() {
        // without ExifTool GeoTagger would show a modal "ExifTool not found" - skip instead
        exifTool = ExifToolTestSupport.require();
    }

    @Start
    void start(Stage stage) throws IOException {
        UiTestSupport.preparePreferences(exifTool);
        GeoTaggerPreferences.SRTM_FOLDER.put(srtmFolder.toString());
        GeoTaggerPreferences.SRTM_INTERPOLATION.put(SrtmInterpolation.NEAREST);
        manager = UiTestSupport.start(stage);
        controller = manager.controller();
        controller.setAltitudeServices(
                () -> new AltitudeEnricher(new SrtmElevationProvider(srtmFolder, SrtmInterpolation.NEAREST), ors,
                        AltitudeSource.SRTM_FIRST, duration -> { }, Duration.ZERO),
                tiles -> {
                    questions.add(tiles);
                    return answer;
                },
                new SrtmDownloader((source, target) -> {
                    downloads.add(source);
                    try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(target))) {
                        zip.putNextEntry(new ZipEntry("L32/" + TILE + ".hgt"));
                        writeTile(zip);
                        zip.closeEntry();
                    }
                }));
    }

    @Stop
    void stop() {
        manager.stop();
    }

    // SRTM3 tile (1201 x 1201 big endian values) with the same altitude everywhere
    private static void writeTile(OutputStream out) throws IOException {
        final ByteBuffer buffer = ByteBuffer.allocate(1201 * 1201 * 2);
        while (buffer.hasRemaining()) {
            buffer.putShort((short) TILE_ALTITUDE);
        }
        out.write(buffer.array());
    }

    private void tileInFolder() throws IOException {
        try (OutputStream out = Files.newOutputStream(srtmFolder.resolve(TILE + ".hgt"))) {
            writeTile(out);
        }
    }

    // a.jpg with coordinates and without altitude, b.jpg with coordinates and altitude 100 m, c.jpg without coordinates
    private void loadImages(FxRobot robot) throws TimeoutException {
        final Path a = TestFiles.image(dir, "a.jpg");
        final Path b = TestFiles.image(dir, "b.jpg");
        TestFiles.image(dir, "c.jpg");
        try (ExifTool tool = new ExifTool(exifTool)) {
            tool.execute(List.of("-overwrite_original", "-GPS:GPSLatitude#=46.5", "-GPS:GPSLatitudeRef#=N",
                    "-GPS:GPSLongitude#=11.5", "-GPS:GPSLongitudeRef#=E", a.toString()));
            tool.execute(List.of("-overwrite_original", "-GPS:GPSLatitude#=46.6", "-GPS:GPSLatitudeRef#=N",
                    "-GPS:GPSLongitude#=11.6", "-GPS:GPSLongitudeRef#=E", "-GPS:GPSAltitude#=100", "-GPS:GPSAltitudeRef#=0",
                    b.toString()));
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

    private void findAltitudes(FxRobot robot) throws TimeoutException {
        robot.clickOn("#findAltitudeButton");
        UiTestSupport.waitUntilIdle(controller);
    }

    private static String status(FxRobot robot) {
        return robot.lookup("#statusLabel").queryAs(Label.class).getText();
    }

    @Test
    void findAltitudeFillsListedFilesWithoutAltitude(FxRobot robot) throws TimeoutException, IOException {
        tileInFolder();
        loadImages(robot);
        selectOnly(robot);

        findAltitudes(robot);

        final MediaFile a = fileNamed(robot, "a.jpg");
        assertEquals(TILE_ALTITUDE, a.altitude());
        assertEquals(100.0, fileNamed(robot, "b.jpg").altitude());
        assertFalse(fileNamed(robot, "c.jpg").isChanged());
        assertTrue(questions.isEmpty());
        assertTrue(ors.calls().isEmpty());
        assertEquals("Altitude found for 1 file", status(robot));

        // one undo step for the whole run
        robot.clickOn("#undoButton");
        assertNull(a.altitude());
        assertTrue(robot.lookup("#undoButton").queryButton().isDisabled());
    }

    @Test
    void findAltitudeOverwritesSelectedFiles(FxRobot robot) throws TimeoutException, IOException {
        tileInFolder();
        loadImages(robot);
        selectOnly(robot, "b.jpg");

        findAltitudes(robot);

        assertEquals(TILE_ALTITUDE, fileNamed(robot, "b.jpg").altitude());
        assertNull(fileNamed(robot, "a.jpg").altitude());
    }

    @Test
    void findAltitudeIsDisabledWithNothingToLookUp(FxRobot robot) throws TimeoutException {
        assertTrue(robot.lookup("#findAltitudeButton").queryButton().isDisabled());
        loadImages(robot);
        selectOnly(robot, "c.jpg");
        assertTrue(robot.lookup("#findAltitudeButton").queryButton().isDisabled());

        selectOnly(robot, "a.jpg");
        assertFalse(robot.lookup("#findAltitudeButton").queryButton().isDisabled());
    }

    @Test
    void missingTilesAreDownloadedAfterConfirmation(FxRobot robot) throws TimeoutException {
        loadImages(robot);
        selectOnly(robot);

        findAltitudes(robot);

        assertEquals(List.of(List.of(TILE)), questions);
        assertEquals(List.of(URI.create("https://viewfinderpanoramas.org/dem3/L32.zip")), downloads);
        assertTrue(Files.isRegularFile(srtmFolder.resolve(TILE + ".hgt")));
        assertEquals(TILE_ALTITUDE, fileNamed(robot, "a.jpg").altitude());
        assertTrue(ors.calls().isEmpty());
    }

    @Test
    void declinedDownloadContinuesWithout(FxRobot robot) throws TimeoutException {
        answer = false;
        loadImages(robot);
        selectOnly(robot);

        findAltitudes(robot);

        assertEquals(List.of(List.of(TILE)), questions);
        assertTrue(downloads.isEmpty());
        assertEquals(ORS_ALTITUDE, fileNamed(robot, "a.jpg").altitude());
    }
}

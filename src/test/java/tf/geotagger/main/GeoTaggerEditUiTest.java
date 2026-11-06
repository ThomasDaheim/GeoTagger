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
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UI tests of editing: details, undo / redo, multi-selection, saving.
 */
@Tag("UI")
@ExtendWith(ApplicationExtension.class)
class GeoTaggerEditUiTest {
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

    // a.jpg with city "Meran", b.jpg without - all files are copies in @TempDir
    private void loadTwoImages(FxRobot robot) throws TimeoutException {
        final Path withCity = TestFiles.image(dir, "a.jpg");
        TestFiles.image(dir, "b.jpg");
        try (ExifTool tool = new ExifTool(exifTool)) {
            tool.execute(List.of("-overwrite_original", "-XMP-photoshop:City=Meran", withCity.toString()));
        }
        robot.interact(() -> controller.loadFolder(dir, false));
        UiTestSupport.waitUntilIdle(controller);
    }

    private static TableView<MediaFile> table(FxRobot robot) {
        return robot.lookup("#mediaTable").queryTableView();
    }

    private static MediaFile fileNamed(TableView<MediaFile> table, String name) {
        return table.getItems().stream()
                .filter(file -> file.path().getFileName().toString().equals(name))
                .findFirst().orElseThrow();
    }

    // columns aren't nodes - found via the table, the cell data is the shown text
    private static Object placeStatus(TableView<MediaFile> table, MediaFile file) {
        final TableColumn<MediaFile, ?> column = table.getColumns().stream()
                .filter(candidate -> "placeStatusColumn".equals(candidate.getId())).findFirst().orElseThrow();
        return column.getCellData(file);
    }

    // selects the file in the list - the details show it afterwards
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

    // replaces the text of a details field and leaves it with Tab, like a user
    private static void enterAndTab(FxRobot robot, Field field, String text) {
        final TextField input = robot.lookup("#details-" + field.name()).queryAs(TextField.class);
        robot.clickOn(input);
        robot.interact(input::selectAll);
        robot.write(text).type(KeyCode.TAB);
        WaitForAsyncUtils.waitForFxEvents();
    }

    private static TextField detailsField(FxRobot robot, Field field) {
        return robot.lookup("#details-" + field.name()).queryAs(TextField.class);
    }

    @Test
    void undoAndRedoShowIcons(FxRobot robot) {
        for (String id : List.of("#undoButton", "#redoButton")) {
            final Button button = robot.lookup(id).queryButton();
            assertTrue(button.getText() == null || button.getText().isEmpty(), id);
            assertTrue(button.getGraphic() instanceof ImageView view && view.getImage() != null && !view.getImage().isError(), id);
            assertTrue(button.getTooltip() != null
                    && button.getTooltip().getText().startsWith("#undoButton".equals(id) ? "Undo" : "Redo"), id);
        }
    }

    @Test
    void invalidInputIsShownRightAwayAtTheLeft(FxRobot robot) throws TimeoutException {
        loadTwoImages(robot);
        select(robot, "b.jpg");

        enter(robot, Field.LATITUDE, "91");

        final TextField latitude = detailsField(robot, Field.LATITUDE);
        final Tooltip error = latitude.getTooltip();
        assertTrue(error != null && error.isShowing());
        final double fieldLeft = latitude.localToScreen(latitude.getBoundsInLocal()).getMinX();
        assertEquals(fieldLeft, error.getAnchorX(), 2.0);
    }

    @Test
    void invalidInputStaysWhenAnotherFieldIsEdited(FxRobot robot) throws TimeoutException {
        loadTwoImages(robot);
        final MediaFile file = select(robot, "b.jpg");

        enterAndTab(robot, Field.LATITUDE, "100");
        enterAndTab(robot, Field.LONGITUDE, "100");

        assertEquals(100.0, file.value(Field.LONGITUDE));
        final TextField latitude = detailsField(robot, Field.LATITUDE);
        assertEquals("100", latitude.getText());
        assertTrue(latitude.getStyle().contains("red"));
    }

    @Test
    void undoAndRedoWithKeysOutsideTextFields(FxRobot robot) throws TimeoutException {
        loadTwoImages(robot);
        final MediaFile file = select(robot, "a.jpg");
        enter(robot, Field.CITY, "Bozen");
        robot.clickOn(robot.lookup(".table-row-cell").nth(0).queryAs(javafx.scene.Node.class));
        select(robot, "a.jpg");

        robot.push(KeyCode.CONTROL, KeyCode.Z);
        assertEquals("Meran", file.value(Field.CITY));

        robot.push(KeyCode.CONTROL, KeyCode.Y);
        assertEquals("Bozen", file.value(Field.CITY));
    }

    @Test
    void ctrlSSaves(FxRobot robot) throws TimeoutException {
        loadTwoImages(robot);
        final MediaFile file = select(robot, "a.jpg");
        enter(robot, Field.CITY, "Bozen");

        robot.push(KeyCode.CONTROL, KeyCode.S);
        UiTestSupport.waitUntilIdle(controller);

        assertTrue(Files.exists(dir.resolve("a.jpg_original")));
        assertFalse(file.isChanged());
    }

    @Test
    void fieldsAreOnlyAsWideAsNeeded(FxRobot robot) throws TimeoutException {
        loadTwoImages(robot);
        select(robot, "a.jpg");

        final double countryCode = detailsField(robot, Field.COUNTRY_CODE).getWidth();
        final double latitude = detailsField(robot, Field.LATITUDE).getWidth();
        final double city = detailsField(robot, Field.CITY).getWidth();
        assertTrue(countryCode < latitude, countryCode + " < " + latitude);
        assertTrue(latitude < city, latitude + " < " + city);
    }

    @Test
    void editingCityMarksTheFileAsChanged(FxRobot robot) throws TimeoutException {
        loadTwoImages(robot);
        final MediaFile file = select(robot, "a.jpg");

        enter(robot, Field.CITY, "Bozen");

        assertEquals("Bozen", file.value(Field.CITY));
        assertEquals("●", placeStatus(table(robot), file));
        assertEquals("Save (1)", robot.lookup("#saveButton").queryButton().getText());
    }

    @Test
    void invalidLatitudeIsRejected(FxRobot robot) throws TimeoutException {
        loadTwoImages(robot);
        final MediaFile file = select(robot, "b.jpg");

        enter(robot, Field.LATITUDE, "91");

        final TextField latitude = robot.lookup("#details-LATITUDE").queryAs(TextField.class);
        assertTrue(latitude.getStyle().contains("red"));
        assertFalse(file.isChanged());
    }

    @Test
    void undoAndRedo(FxRobot robot) throws TimeoutException {
        loadTwoImages(robot);
        final MediaFile file = select(robot, "a.jpg");
        enter(robot, Field.CITY, "Bozen");

        robot.clickOn("#undoButton");
        assertEquals("Meran", file.value(Field.CITY));
        assertFalse(file.isChanged());

        robot.clickOn("#redoButton");
        assertEquals("Bozen", file.value(Field.CITY));
        assertTrue(file.isChanged());
    }

    @Test
    void multiSelectionChangesAllFiles(FxRobot robot) throws TimeoutException {
        loadTwoImages(robot);
        final TableView<MediaFile> table = table(robot);
        robot.interact(() -> table.getSelectionModel().selectAll());

        enter(robot, Field.CITY, "Bozen");

        table.getItems().forEach(file -> assertEquals("Bozen", file.value(Field.CITY)));
        assertEquals("Save (2)", robot.lookup("#saveButton").queryButton().getText());
    }

    @Test
    void saveWritesTheFile(FxRobot robot) throws TimeoutException {
        loadTwoImages(robot);
        final MediaFile file = select(robot, "a.jpg");
        enter(robot, Field.CITY, "Bozen");

        robot.clickOn("#saveButton");
        UiTestSupport.waitUntilIdle(controller);

        try (ExifTool tool = new ExifTool(exifTool)) {
            final Map<String, Object> tags = tool.readJson(List.of(file.path()), List.of("XMP-photoshop:City")).get(0);
            assertEquals("Bozen", tags.get("XMP-photoshop:City"));
        }
        assertTrue(Files.exists(dir.resolve("a.jpg_original")));
        assertEquals("✓", placeStatus(table(robot), file));
    }

    @Test
    void saveIsImpossibleWithoutExifTool(FxRobot robot) throws TimeoutException {
        loadTwoImages(robot);
        select(robot, "a.jpg");
        enter(robot, Field.CITY, "Bozen");

        // e.g. a wrong path confirmed in the preferences - the changes must not look saveable
        GeoTaggerPreferences.EXIFTOOL_PATH.put(dir.resolve("missing.exe").toString());
        robot.interact(() -> controller.applyExifTool());

        assertTrue(robot.lookup("#saveButton").queryButton().isDisabled());
    }

    @Test
    void editingKeepsThePreview(FxRobot robot) throws TimeoutException {
        loadTwoImages(robot);
        select(robot, "a.jpg");
        final ImageView preview = robot.lookup("#previewPane .image-view").queryAs(ImageView.class);
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> preview.getImage() != null);
        // the preview of the same file must not be cleared and loaded again after each edit
        final AtomicBoolean cleared = new AtomicBoolean(false);
        robot.interact(() -> preview.imageProperty().addListener((observable, oldImage, newImage) -> {
            if (newImage == null) {
                cleared.set(true);
            }
        }));

        enter(robot, Field.CITY, "Bozen");

        assertEquals("Bozen", fileNamed(table(robot), "a.jpg").value(Field.CITY));
        assertFalse(cleared.get());
    }
}

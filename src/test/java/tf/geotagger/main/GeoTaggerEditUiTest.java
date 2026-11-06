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
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.paint.Color;
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
import tf.geotagger.model.Field;
import tf.geotagger.model.MediaFile;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static tf.geotagger.main.UiTestSupport.edit;
import static tf.geotagger.main.UiTestSupport.editField;
import static tf.geotagger.main.UiTestSupport.openEditDialog;
import static tf.geotagger.main.UiTestSupport.typeInto;

/**
 * UI tests of editing: edit dialog, undo / redo, multi-selection, saving.
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

    // true if the shown cell of this file and column is marked as changed (style class "changed")
    private static boolean isMarkedChanged(FxRobot robot, MediaFile file, String columnId) {
        WaitForAsyncUtils.waitForFxEvents();
        return robot.lookup(".table-cell").queryAllAs(TableCell.class).stream()
                .filter(cell -> cell.getTableColumn() != null && columnId.equals(cell.getTableColumn().getId()))
                .filter(cell -> cell.getTableRow() != null && cell.getTableRow().getItem() == file)
                .findFirst().orElseThrow()
                .getStyleClass().contains("changed");
    }

    // selects the file in the list
    private static MediaFile select(FxRobot robot, String name) {
        final TableView<MediaFile> table = table(robot);
        final MediaFile file = fileNamed(table, name);
        robot.interact(() -> table.getSelectionModel().clearAndSelect(table.getItems().indexOf(file)));
        return file;
    }

    private static boolean editDialogIsOpen(FxRobot robot) {
        WaitForAsyncUtils.waitForFxEvents();
        return robot.lookup("#editOk").tryQuery().map(Node::isVisible).orElse(false);
    }

    @Test
    void undoAndRedoShowIcons(FxRobot robot) {
        for (String id : List.of("#undoButton", "#redoButton")) {
            final Button button = robot.lookup(id).queryButton();
            assertTrue(button.getText() == null || button.getText().isEmpty(), id);
            assertTrue(button.getGraphic() instanceof ImageView view && view.getImage() != null && !view.getImage().isError(), id);
            assertTrue(button.getTooltip() != null
                    && button.getTooltip().getText().startsWith("#undoButton".equals(id) ? "Undo" : "Redo"), id);
            // JavaFX waits a second by default - too long (user feedback)
            assertTrue(button.getTooltip().getShowDelay().toMillis() <= 300, id);
        }
    }

    @Test
    void invalidInputIsShownRightAwayAtTheLeft(FxRobot robot) throws TimeoutException {
        loadTwoImages(robot);
        select(robot, "b.jpg");
        openEditDialog(robot);

        typeInto(robot, Field.LATITUDE, "91");
        robot.type(KeyCode.ENTER);

        final TextField latitude = editField(robot, Field.LATITUDE);
        final Tooltip error = latitude.getTooltip();
        assertTrue(error != null && error.isShowing());
        final double fieldLeft = latitude.localToScreen(latitude.getBoundsInLocal()).getMinX();
        assertEquals(fieldLeft, error.getAnchorX(), 2.0);
        robot.clickOn("#editCancel");
    }

    @Test
    void invalidLatitudeBlocksOk(FxRobot robot) throws TimeoutException {
        loadTwoImages(robot);
        final MediaFile file = select(robot, "b.jpg");
        openEditDialog(robot);

        typeInto(robot, Field.LATITUDE, "91");

        assertTrue(robot.lookup("#editOk").queryButton().isDisabled());
        assertTrue(editField(robot, Field.LATITUDE).getStyle().contains("red"));
        robot.type(KeyCode.ENTER);
        assertTrue(editDialogIsOpen(robot));

        robot.clickOn("#editCancel");
        assertFalse(editDialogIsOpen(robot));
        assertFalse(file.isChanged());
    }

    @Test
    void cancelChangesNothing(FxRobot robot) throws TimeoutException {
        loadTwoImages(robot);
        final MediaFile file = select(robot, "a.jpg");
        openEditDialog(robot);

        typeInto(robot, Field.CITY, "Bozen");
        robot.clickOn("#editCancel");

        assertFalse(editDialogIsOpen(robot));
        assertEquals("Meran", file.value(Field.CITY));
        assertFalse(file.isChanged());
        assertTrue(robot.lookup("#undoButton").queryButton().isDisabled());
    }

    @Test
    void ctrlZUndoesTypingInTheDialog(FxRobot robot) throws TimeoutException {
        loadTwoImages(robot);
        select(robot, "a.jpg");
        openEditDialog(robot);
        typeInto(robot, Field.CITY, "Bozen");

        robot.push(KeyCode.CONTROL, KeyCode.Z);
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals("Meran", editField(robot, Field.CITY).getText());
        robot.clickOn("#editCancel");
    }

    @Test
    void doubleClickOpensTheDialog(FxRobot robot) throws TimeoutException {
        loadTwoImages(robot);
        final MediaFile file = fileNamed(table(robot), "a.jpg");
        final Node row = robot.lookup(".table-row-cell").queryAllAs(TableRow.class).stream()
                .filter(candidate -> candidate.getItem() == file).findFirst().orElseThrow();

        robot.doubleClickOn(row);
        UiTestSupport.waitForEditDialog(robot);

        assertEquals("Meran", editField(robot, Field.CITY).getText());
        robot.clickOn("#editCancel");
    }

    @Test
    void oneUndoStepForSeveralFields(FxRobot robot) throws TimeoutException {
        loadTwoImages(robot);
        final MediaFile file = select(robot, "a.jpg");
        edit(robot, Map.of(Field.CITY, "Bozen", Field.COUNTRY, "Italien"));
        assertEquals("Bozen", file.value(Field.CITY));
        assertEquals("Italien", file.value(Field.COUNTRY));

        robot.clickOn("#undoButton");

        assertEquals("Meran", file.value(Field.CITY));
        assertEquals(null, file.value(Field.COUNTRY));
        assertFalse(file.isChanged());
        assertTrue(robot.lookup("#undoButton").queryButton().isDisabled());
    }

    @Test
    void undoAndRedoWithKeysOutsideTextFields(FxRobot robot) throws TimeoutException {
        loadTwoImages(robot);
        final MediaFile file = select(robot, "a.jpg");
        edit(robot, Map.of(Field.CITY, "Bozen"));
        robot.clickOn(robot.lookup(".table-row-cell").nth(0).queryAs(Node.class));
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
        edit(robot, Map.of(Field.CITY, "Bozen"));

        robot.push(KeyCode.CONTROL, KeyCode.S);
        UiTestSupport.waitUntilIdle(controller);

        assertTrue(Files.exists(dir.resolve("a.jpg_original")));
        assertFalse(file.isChanged());
    }

    @Test
    void fieldsAreOnlyAsWideAsNeeded(FxRobot robot) throws TimeoutException {
        loadTwoImages(robot);
        select(robot, "a.jpg");
        openEditDialog(robot);

        final double countryCode = editField(robot, Field.COUNTRY_CODE).getWidth();
        final double latitude = editField(robot, Field.LATITUDE).getWidth();
        final double city = editField(robot, Field.CITY).getWidth();
        assertTrue(countryCode < latitude, countryCode + " < " + latitude);
        assertTrue(latitude < city, latitude + " < " + city);
        robot.clickOn("#editCancel");
    }

    @Test
    void editingCityMarksTheFileAsChanged(FxRobot robot) throws TimeoutException {
        loadTwoImages(robot);
        final MediaFile file = select(robot, "a.jpg");

        edit(robot, Map.of(Field.CITY, "Bozen"));

        assertEquals("Bozen", file.value(Field.CITY));
        assertTrue(isMarkedChanged(robot, file, "cityColumn"));
        assertEquals("Bozen", table(robot).getColumns().stream().filter(column -> "cityColumn".equals(column.getId()))
                .findFirst().orElseThrow().getCellData(file));
        assertEquals("Save (1)", robot.lookup("#saveButton").queryButton().getText());
    }

    @Test
    void changedCellsAreOrange(FxRobot robot) throws TimeoutException {
        loadTwoImages(robot);
        final MediaFile file = select(robot, "a.jpg");
        edit(robot, Map.of(Field.CITY, "Bozen"));
        // not selected - selected rows have their own colours
        select(robot, "b.jpg");
        WaitForAsyncUtils.waitForFxEvents();

        final TableCell<?, ?> cell = robot.lookup(".table-cell").queryAllAs(TableCell.class).stream()
                .filter(candidate -> candidate.getTableColumn() != null && "cityColumn".equals(candidate.getTableColumn().getId()))
                .filter(candidate -> candidate.getTableRow() != null && candidate.getTableRow().getItem() == file)
                .findFirst().orElseThrow();
        assertEquals(Color.web("#e07000"), ((Text) cell.lookup(".text")).getFill());
    }

    @Test
    void blankInputInADifferentFieldChangesNothing(FxRobot robot) throws TimeoutException {
        loadTwoImages(robot);
        final TableView<MediaFile> table = table(robot);
        robot.interact(() -> table.getSelectionModel().selectAll());

        // City is "Meran" and empty - the field shows "(different)"; a typed space must not delete "Meran"
        edit(robot, Map.of(Field.CITY, " "));

        assertEquals("Meran", fileNamed(table, "a.jpg").value(Field.CITY));
        assertTrue(robot.lookup("#undoButton").queryButton().isDisabled());
    }

    @Test
    void undoAndRedo(FxRobot robot) throws TimeoutException {
        loadTwoImages(robot);
        final MediaFile file = select(robot, "a.jpg");
        edit(robot, Map.of(Field.CITY, "Bozen"));

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

        edit(robot, Map.of(Field.CITY, "Bozen"));

        table.getItems().forEach(file -> assertEquals("Bozen", file.value(Field.CITY)));
        assertEquals("Save (2)", robot.lookup("#saveButton").queryButton().getText());
    }

    @Test
    void saveWritesTheFile(FxRobot robot) throws TimeoutException {
        loadTwoImages(robot);
        final MediaFile file = select(robot, "a.jpg");
        edit(robot, Map.of(Field.CITY, "Bozen"));

        robot.clickOn("#saveButton");
        UiTestSupport.waitUntilIdle(controller);

        try (ExifTool tool = new ExifTool(exifTool)) {
            final Map<String, Object> tags = tool.readJson(List.of(file.path()), List.of("XMP-photoshop:City")).get(0);
            assertEquals("Bozen", tags.get("XMP-photoshop:City"));
        }
        assertTrue(Files.exists(dir.resolve("a.jpg_original")));
        assertFalse(isMarkedChanged(robot, file, "cityColumn"));
    }

    @Test
    void saveIsImpossibleWithoutExifTool(FxRobot robot) throws TimeoutException {
        loadTwoImages(robot);
        select(robot, "a.jpg");
        edit(robot, Map.of(Field.CITY, "Bozen"));

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

        edit(robot, Map.of(Field.CITY, "Bozen"));

        assertEquals("Bozen", fileNamed(table(robot), "a.jpg").value(Field.CITY));
        assertFalse(cleared.get());
    }
}

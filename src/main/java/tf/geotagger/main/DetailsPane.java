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

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.util.Duration;
import tf.geotagger.edit.FieldText;
import tf.geotagger.edit.SelectionValue;
import tf.geotagger.model.Field;
import tf.geotagger.model.MediaFile;

/**
 * Editable fields of the selected files - see spec, section "Hauptfenster", tab "Details".
 * A change is reported when the user presses Enter or leaves the field; invalid input is marked red and not reported.
 */
public class DetailsPane extends GridPane {
    private static final Map<Field, String> LABELS = new EnumMap<>(Map.of(
            Field.LATITUDE, "Latitude",
            Field.LONGITUDE, "Longitude",
            Field.ALTITUDE, "Altitude (m)",
            Field.COUNTRY_CODE, "Country code",
            Field.COUNTRY, "Country",
            Field.STATE, "State / province",
            Field.CITY, "City",
            Field.SUBLOCATION, "Sublocation",
            Field.TIME_OFFSET, "Time zone (UTC offset)"));
    private static final String ERROR_STYLE = "-fx-border-color: red;";
    // width in characters, e.g. "48° 08′ 14.5″ N" for the coordinates
    private static final Map<Field, Integer> COLUMNS = new EnumMap<>(Map.of(
            Field.LATITUDE, 14,
            Field.LONGITUDE, 14,
            Field.ALTITUDE, 8,
            Field.COUNTRY_CODE, 4,
            Field.COUNTRY, 24,
            Field.STATE, 24,
            Field.CITY, 24,
            Field.SUBLOCATION, 24,
            Field.TIME_OFFSET, 7));

    private final Map<Field, TextField> textFields = new EnumMap<>(Field.class);
    // text shown for the current selection - leaving a field with this text changes nothing
    private final Map<Field, String> shownTexts = new EnumMap<>(Field.class);
    private BiConsumer<Field, Object> onEdit = (field, value) -> {};
    // files shown at the moment - invalid input survives a refresh for the same files
    private List<MediaFile> shownFiles = List.of();
    private final Set<Field> invalidFields = EnumSet.noneOf(Field.class);

    public DetailsPane() {
        setHgap(8);
        setVgap(6);
        setPadding(new Insets(8));
        final ColumnConstraints labels = new ColumnConstraints();
        final ColumnConstraints fields = new ColumnConstraints();
        fields.setHgrow(Priority.ALWAYS);
        getColumnConstraints().addAll(labels, fields);

        int row = 0;
        for (Field field : Field.values()) {
            final TextField text = new TextField();
            // UI tests find the fields by ID, e.g. #details-CITY
            text.setId("details-" + field.name());
            // not wider than the values need
            text.setPrefColumnCount(COLUMNS.get(field));
            text.setMaxWidth(Region.USE_PREF_SIZE);
            // the error message pops up right away - typing on hides it, the red border stays until the input is valid
            text.textProperty().addListener((observable, oldText, newText) -> hideErrorMessage(text));
            text.setOnAction(event -> commit(field));
            text.focusedProperty().addListener((observable, wasFocused, focused) -> {
                if (!focused) {
                    commit(field);
                }
            });
            textFields.put(field, text);
            add(new Label(LABELS.get(field)), 0, row);
            add(text, 1, row);
            row++;
        }
        setFiles(List.of());
    }

    /**
     * @param field a field
     * @return its label in the details
     */
    public static String label(Field field) {
        return LABELS.get(field);
    }

    /**
     * @param onEdit called with the field and its new value (null = delete) when the user changed a field
     */
    public void setOnEdit(BiConsumer<Field, Object> onEdit) {
        this.onEdit = onEdit;
    }

    /**
     * Shows the values of the selected files - fields with different values stay empty and show "(different)".
     *
     * @param files selected files - none disables all fields
     */
    public void setFiles(List<MediaFile> files) {
        // same files again (e.g. after another field was changed): invalid input stays for the user to correct it
        final boolean sameFiles = files.equals(shownFiles);
        shownFiles = List.copyOf(files);
        for (Field field : Field.values()) {
            final SelectionValue selection = SelectionValue.of(files, field);
            final String shown = FieldText.format(field, selection.value());
            shownTexts.put(field, shown);
            final TextField text = textFields.get(field);
            text.setPromptText(selection.different() ? "(different)" : "");
            text.setDisable(files.isEmpty());
            if (!sameFiles || !invalidFields.contains(field)) {
                text.setText(shown);
                clearError(field);
            }
        }
    }

    /**
     * Takes over what is typed in the fields but not yet confirmed with Enter or by leaving the field - e.g. before
     * saving with ctrl+s.
     */
    public void commitAll() {
        for (Field field : Field.values()) {
            commit(field);
        }
    }

    private void commit(Field field) {
        final TextField text = textFields.get(field);
        if (text.getText().equals(shownTexts.get(field))) {
            clearError(field);
            return;
        }
        try {
            final Object value = FieldText.parse(field, text.getText());
            clearError(field);
            // Enter and leaving the field would report the same change twice
            shownTexts.put(field, text.getText());
            onEdit.accept(field, value);
        } catch (IllegalArgumentException ex) {
            showError(field, ex.getMessage());
        }
    }

    private void showError(Field field, String message) {
        final TextField text = textFields.get(field);
        invalidFields.add(field);
        text.setStyle(ERROR_STYLE);
        final Tooltip tooltip = new Tooltip(message);
        // when hovering later on: no long wait
        tooltip.setShowDelay(Duration.millis(200));
        text.setTooltip(tooltip);
        // right away, left aligned below the field
        final Bounds bounds = text.localToScreen(text.getBoundsInLocal());
        if (bounds != null) {
            tooltip.setAutoHide(true);
            tooltip.show(text, bounds.getMinX(), bounds.getMaxY());
        }
    }

    private static void hideErrorMessage(TextField text) {
        if (text.getTooltip() != null) {
            text.getTooltip().hide();
        }
    }

    private void clearError(Field field) {
        final TextField text = textFields.get(field);
        invalidFields.remove(field);
        hideErrorMessage(text);
        text.setStyle("");
        text.setTooltip(null);
    }
}

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
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
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

    private final Map<Field, TextField> textFields = new EnumMap<>(Field.class);
    // text shown for the current selection - leaving a field with this text changes nothing
    private final Map<Field, String> shownTexts = new EnumMap<>(Field.class);
    private BiConsumer<Field, Object> onEdit = (field, value) -> {};

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
        for (Field field : Field.values()) {
            final SelectionValue selection = SelectionValue.of(files, field);
            final String shown = FieldText.format(field, selection.value());
            shownTexts.put(field, shown);
            final TextField text = textFields.get(field);
            text.setText(shown);
            text.setPromptText(selection.different() ? "(different)" : "");
            clearError(text);
            text.setDisable(files.isEmpty());
        }
    }

    private void commit(Field field) {
        final TextField text = textFields.get(field);
        if (text.getText().equals(shownTexts.get(field))) {
            clearError(text);
            return;
        }
        try {
            final Object value = FieldText.parse(field, text.getText());
            clearError(text);
            // Enter and leaving the field would report the same change twice
            shownTexts.put(field, text.getText());
            onEdit.accept(field, value);
        } catch (IllegalArgumentException ex) {
            text.setStyle(ERROR_STYLE);
            text.setTooltip(new Tooltip(ex.getMessage()));
        }
    }

    private static void clearError(TextField text) {
        text.setStyle("");
        text.setTooltip(null);
    }
}

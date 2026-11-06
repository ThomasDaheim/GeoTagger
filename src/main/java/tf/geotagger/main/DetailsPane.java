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
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
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
 * Editable fields of the selected files, shown in the edit dialog - see spec, section "Hauptfenster".
 * Each keystroke is checked: invalid input is marked red and makes {@link #validProperty()} false.
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
    // text shown for the files - a field still showing it is no change
    private final Map<Field, String> shownTexts = new EnumMap<>(Field.class);
    // checked with each keystroke
    private final Map<Field, String> errors = new EnumMap<>(Field.class);
    private final BooleanProperty valid = new SimpleBooleanProperty(true);

    /**
     * Creates the fields - empty and disabled until {@link #setFiles(List)} is called.
     */
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
            // red border with each keystroke - the message pops up on Enter or when leaving the field, typing on hides it
            text.textProperty().addListener((observable, oldText, newText) -> check(field));
            text.setOnAction(event -> showErrorMessage(field));
            text.focusedProperty().addListener((observable, wasFocused, focused) -> {
                if (!focused) {
                    showErrorMessage(field);
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
     * Shows the values of the files - fields with different values stay empty and show "(different)".
     *
     * @param files files to edit - none disables all fields
     */
    public void setFiles(List<MediaFile> files) {
        for (Field field : Field.values()) {
            final SelectionValue selection = SelectionValue.of(files, field);
            final String shown = FieldText.format(field, selection.value());
            shownTexts.put(field, shown);
            final TextField text = textFields.get(field);
            text.setPromptText(selection.different() ? "(different)" : "");
            text.setDisable(files.isEmpty());
            text.setText(shown);
            // same text as before: no change event
            check(field);
        }
    }

    /**
     * @return true as long as all fields are valid - false while one is marked red
     */
    public BooleanProperty validProperty() {
        return valid;
    }

    /**
     * The fields the user changed - an empty field showing "(different)" is no change.
     *
     * @return new value per changed and valid field, null means "delete the field"
     */
    public Map<Field, Object> changes() {
        final Map<Field, Object> changes = new EnumMap<>(Field.class);
        for (Field field : Field.values()) {
            final String text = textFields.get(field).getText();
            if (!text.equals(shownTexts.get(field)) && !errors.containsKey(field)) {
                changes.put(field, FieldText.parse(field, text));
            }
        }
        return changes;
    }

    private void check(Field field) {
        final TextField text = textFields.get(field);
        hideErrorMessage(text);
        String error = null;
        if (!text.getText().equals(shownTexts.get(field))) {
            try {
                FieldText.parse(field, text.getText());
            } catch (IllegalArgumentException ex) {
                error = ex.getMessage();
            }
        }
        if (error == null) {
            errors.remove(field);
            text.setStyle("");
            text.setTooltip(null);
        } else {
            errors.put(field, error);
            text.setStyle(ERROR_STYLE);
            final Tooltip tooltip = new Tooltip(error);
            // when hovering later on: no long wait
            tooltip.setShowDelay(Duration.millis(200));
            text.setTooltip(tooltip);
        }
        valid.set(errors.isEmpty());
    }

    // the message of an invalid field, right away and left aligned below the field
    private void showErrorMessage(Field field) {
        final TextField text = textFields.get(field);
        final Bounds bounds = text.localToScreen(text.getBoundsInLocal());
        if (text.getTooltip() != null && bounds != null && text.getScene() != null) {
            text.getTooltip().setAutoHide(true);
            // a click e.g. on Cancel hides the message and still reaches the button
            text.getTooltip().setConsumeAutoHidingEvents(false);
            text.getTooltip().show(text, bounds.getMinX(), bounds.getMaxY());
        }
    }

    private static void hideErrorMessage(TextField text) {
        if (text.getTooltip() != null) {
            text.getTooltip().hide();
        }
    }
}

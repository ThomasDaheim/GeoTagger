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

import java.io.File;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import jfxtras.styles.jmetro.JMetro;
import jfxtras.styles.jmetro.Style;
import tf.exiftool.ExifToolException;
import tf.geotagger.metadata.ExifToolLocator;
import tf.helper.javafx.AbstractStage;

/**
 * Preferences of GeoTagger - for now the ExifTool executable. Built in code like GPXEditor's PreferenceEditor.
 */
public class PreferenceEditor extends AbstractStage {
    private static final PreferenceEditor INSTANCE = new PreferenceEditor();

    private final TextField exifToolPath = new TextField();
    private final Label exifToolCheck = new Label();

    private PreferenceEditor() {
        initViewer();
    }

    /**
     * @return the preferences window - created on first use, must be called on the JavaFX thread
     */
    public static PreferenceEditor getInstance() {
        return INSTANCE;
    }

    private void initViewer() {
        setTitle(GeoTaggerManager.TITLE + " - Preferences");
        initModality(Modality.APPLICATION_MODAL);
        new JMetro(Style.LIGHT).setScene(getScene());

        final GridPane grid = getGridPane();
        final ColumnConstraints growing = new ColumnConstraints();
        growing.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(new ColumnConstraints(), growing, new ColumnConstraints());

        final Label exifToolLabel = new Label("ExifTool:");
        grid.add(exifToolLabel, 0, 0);
        GridPane.setMargin(exifToolLabel, INSET_TOP);
        exifToolPath.setPrefColumnCount(45);
        grid.add(exifToolPath, 1, 0);
        GridPane.setMargin(exifToolPath, INSET_TOP);
        final Button browseButton = new Button("Browse...");
        browseButton.setOnAction(event -> browse());
        grid.add(browseButton, 2, 0);
        GridPane.setMargin(browseButton, INSET_TOP);

        final Button checkButton = new Button("Check");
        checkButton.setOnAction(event -> check());
        grid.add(checkButton, 0, 1);
        GridPane.setMargin(checkButton, INSET_TOP);
        grid.add(exifToolCheck, 1, 1, 2, 1);
        GridPane.setMargin(exifToolCheck, INSET_TOP);

        final Button saveButton = new Button("Save");
        saveButton.setOnAction(event -> {
            GeoTaggerPreferences.EXIFTOOL_PATH.put(exifToolPath.getText().trim());
            close();
        });
        setActionAccelerator(saveButton);
        final Button cancelButton = new Button("Cancel");
        cancelButton.setOnAction(event -> close());
        setCancelAccelerator(cancelButton);

        final HBox buttons = new HBox(8, saveButton, cancelButton);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        buttons.setPadding(INSET_TOP_BOTTOM);
        getRootPane().getChildren().add(buttons);
    }

    /**
     * Shows the preferences and waits until the window is closed.
     *
     * @return true if the preferences were saved
     */
    public boolean editPreferences() {
        exifToolPath.setText(GeoTaggerPreferences.EXIFTOOL_PATH.getAsString());
        exifToolCheck.setText("");
        showAndWait();
        return wasActionButtonPressed();
    }

    private void browse() {
        final FileChooser chooser = new FileChooser();
        chooser.setTitle("Select ExifTool");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Programs", "*.exe"),
                new FileChooser.ExtensionFilter("All files", "*.*"));
        final File current = new File(exifToolPath.getText().trim());
        if (current.getParentFile() != null && current.getParentFile().isDirectory()) {
            chooser.setInitialDirectory(current.getParentFile());
        }
        final File selected = chooser.showOpenDialog(this);
        if (selected != null) {
            exifToolPath.setText(selected.getAbsolutePath());
            check();
        }
    }

    private void check() {
        try {
            exifToolCheck.setText("ExifTool " + ExifToolLocator.version(Path.of(exifToolPath.getText().trim())));
        } catch (ExifToolException | InvalidPathException ex) {
            exifToolCheck.setText("Not usable: " + ex.getMessage());
        }
    }
}

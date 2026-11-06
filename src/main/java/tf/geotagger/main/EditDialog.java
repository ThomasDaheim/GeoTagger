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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import jfxtras.styles.jmetro.JMetro;
import jfxtras.styles.jmetro.Style;
import tf.geotagger.model.Field;
import tf.geotagger.model.MediaFile;

/**
 * Modal dialog to edit the values of the selected files - like GeoSetter's "Edit Data" dialog, see spec, section
 * "Hauptfenster". OK takes over all changed fields at once.
 */
final class EditDialog {
    private EditDialog() {
    }

    /**
     * Shows the dialog and waits until it is closed.
     *
     * @param owner the main window
     * @param files files to edit - at least one
     * @return the changed fields (a null value deletes the field) - empty on Cancel / Esc or without a change
     */
    static Optional<Map<Field, Object>> show(Window owner, List<MediaFile> files) {
        final DetailsPane details = new DetailsPane();
        details.setFiles(files);

        final AtomicBoolean confirmed = new AtomicBoolean(false);
        final Stage stage = new Stage();
        final Button ok = new Button("OK");
        // UI tests find the buttons by ID
        ok.setId("editOk");
        ok.setDefaultButton(true);
        ok.disableProperty().bind(details.validProperty().not());
        ok.setOnAction(event -> {
            confirmed.set(true);
            stage.close();
        });
        final Button cancel = new Button("Cancel");
        cancel.setId("editCancel");
        cancel.setCancelButton(true);
        cancel.setOnAction(event -> stage.close());
        ButtonBar.setButtonData(ok, ButtonBar.ButtonData.OK_DONE);
        ButtonBar.setButtonData(cancel, ButtonBar.ButtonData.CANCEL_CLOSE);
        final ButtonBar buttons = new ButtonBar();
        buttons.getButtons().addAll(ok, cancel);
        buttons.setPadding(new Insets(0, 8, 8, 8));

        final Scene scene = new Scene(new VBox(details, buttons));
        // same look as the main window
        new JMetro(Style.LIGHT).setScene(scene);
        scene.getStylesheets().add(GeoTaggerManager.STYLESHEET);
        stage.setScene(scene);
        stage.setTitle("Edit " + files.size() + (files.size() == 1 ? " file" : " files"));
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setResizable(false);
        stage.showAndWait();

        if (!confirmed.get()) {
            return Optional.empty();
        }
        final Map<Field, Object> changes = details.changes();
        return changes.isEmpty() ? Optional.empty() : Optional.of(changes);
    }
}

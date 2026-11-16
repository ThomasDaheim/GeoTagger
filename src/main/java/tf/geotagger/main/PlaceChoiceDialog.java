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
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import jfxtras.styles.jmetro.JMetro;
import jfxtras.styles.jmetro.Style;
import tf.geo.service.NearbyPlace;
import tf.geo.service.Place;
import tf.geotagger.model.MediaFile;
import tf.geotagger.preview.Preview;

/**
 * "Choose place" - several places (e.g. districts) near a photo, like GeoSetter's "Ortsdaten auswählen": preview,
 * list "state; city; sublocation (distance)" and editable fields below. See plan 5c.
 */
final class PlaceChoiceDialog {
    private static final double PREVIEW_SIZE = 320;

    /**
     * What the user chose.
     *
     * @param place the place - as chosen in the list, with the edited state, city and sublocation
     * @param forAll true for "Use for all": the choice applies to the rest of the run
     */
    record Choice(Place place, boolean forAll) {
    }

    private PlaceChoiceDialog() {
    }

    /**
     * Shows the dialog and waits until it is closed.
     *
     * @param owner the main window
     * @param files files with these places nearby - the first one is shown
     * @param candidates places to choose from, best answer first
     * @param preview preview of the first file - shown when ready, empty shows nothing
     * @param offerForAll true if more files with several places nearby follow in this run
     * @return the choice - empty on "Cancel" / Esc
     */
    static Optional<Choice> show(Window owner, List<MediaFile> files, List<NearbyPlace> candidates,
            CompletableFuture<Optional<Preview>> preview, boolean offerForAll) {
        final Stage stage = new Stage();
        final AtomicReference<Choice> result = new AtomicReference<>();

        final ImageView image = new ImageView();
        image.setFitWidth(PREVIEW_SIZE);
        image.setFitHeight(PREVIEW_SIZE);
        image.setPreserveRatio(true);
        final StackPane previewBox = new StackPane(image);
        previewBox.setMinSize(PREVIEW_SIZE, PREVIEW_SIZE);
        previewBox.setStyle("-fx-background-color: black;");
        preview.thenAccept(found -> found.ifPresent(shown -> Platform.runLater(() -> {
            image.setImage(new Image(shown.image().toUri().toString(), PREVIEW_SIZE, PREVIEW_SIZE, true, true, true));
            image.setRotate(shown.rotation());
        })));

        final Label countryCode = new Label();
        final Label country = new Label();
        final ListView<NearbyPlace> list = new ListView<>();
        // UI tests find the controls by ID
        list.setId("placeChoiceList");
        list.getItems().setAll(candidates);
        list.setPrefHeight(150);
        list.setCellFactory(view -> new ListCell<>() {
            @Override
            protected void updateItem(NearbyPlace item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : text(item));
            }
        });
        final TextField state = field("placeChoiceState");
        final TextField city = field("placeChoiceCity");
        final TextField sublocation = field("placeChoiceSublocation");
        list.getSelectionModel().selectedItemProperty().addListener((observable, oldItem, item) -> {
            if (item != null) {
                countryCode.setText(Objects.requireNonNullElse(item.place().countryCode(), ""));
                country.setText(Objects.requireNonNullElse(item.place().country(), ""));
                state.setText(Objects.requireNonNullElse(item.place().state(), ""));
                city.setText(Objects.requireNonNullElse(item.place().city(), ""));
                sublocation.setText(Objects.requireNonNullElse(item.place().sublocation(), ""));
            }
        });
        list.getSelectionModel().select(0);

        final GridPane fields = new GridPane();
        fields.setHgap(8);
        fields.setVgap(6);
        final ColumnConstraints growing = new ColumnConstraints();
        growing.setHgrow(Priority.ALWAYS);
        fields.getColumnConstraints().addAll(new ColumnConstraints(), growing);
        fields.addRow(0, new Label("Country code:"), countryCode);
        fields.addRow(1, new Label("Country:"), country);
        final GridPane edits = new GridPane();
        edits.setHgap(8);
        edits.setVgap(6);
        edits.getColumnConstraints().addAll(new ColumnConstraints(), growing);
        edits.addRow(0, new Label("State:"), state);
        edits.addRow(1, new Label("City:"), city);
        edits.addRow(2, new Label("Sublocation:"), sublocation);

        final Button use = new Button("Use");
        use.setId("placeChoiceUse");
        use.setDefaultButton(true);
        final Button useForAll = new Button("Use for all");
        useForAll.setId("placeChoiceUseForAll");
        final Button cancel = new Button("Cancel");
        cancel.setId("placeChoiceCancel");
        cancel.setCancelButton(true);
        use.setOnAction(event -> {
            result.set(new Choice(edited(list, state, city, sublocation), false));
            stage.close();
        });
        useForAll.setOnAction(event -> {
            result.set(new Choice(edited(list, state, city, sublocation), true));
            stage.close();
        });
        cancel.setOnAction(event -> stage.close());
        ButtonBar.setButtonData(use, ButtonBar.ButtonData.OK_DONE);
        ButtonBar.setButtonData(useForAll, ButtonBar.ButtonData.OTHER);
        ButtonBar.setButtonData(cancel, ButtonBar.ButtonData.CANCEL_CLOSE);
        final ButtonBar buttons = new ButtonBar();
        buttons.getButtons().add(use);
        if (offerForAll) {
            buttons.getButtons().add(useForAll);
        }
        buttons.getButtons().add(cancel);

        final VBox right = new VBox(8, fields, new Label("Choose state, city and sublocation:"), list, edits, buttons);
        right.setPrefWidth(460);
        final HBox content = new HBox(12, previewBox, right);
        content.setPadding(new Insets(10));

        final Scene scene = new Scene(content);
        // same look as the main window
        new JMetro(Style.LIGHT).setScene(scene);
        scene.getStylesheets().add(GeoTaggerManager.STYLESHEET);
        stage.setScene(scene);
        final String name = files.get(0).path().getFileName().toString();
        stage.setTitle("Choose place - " + name + (files.size() > 1 ? " (+" + (files.size() - 1) + ")" : ""));
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.showAndWait();
        return Optional.ofNullable(result.get());
    }

    private static TextField field(String id) {
        final TextField field = new TextField();
        field.setId(id);
        return field;
    }

    // the chosen entry with the state, city and sublocation as edited - empty fields delete the value
    private static Place edited(ListView<NearbyPlace> list, TextField state, TextField city, TextField sublocation) {
        final NearbyPlace selected = Objects.requireNonNullElse(list.getSelectionModel().getSelectedItem(), list.getItems().get(0));
        return new Place(selected.place().countryCode(), selected.place().country(), state.getText(), city.getText(),
                sublocation.getText());
    }

    /**
     * @param nearby a place nearby
     * @return e.g. "Katalonien; Barcelona; Eixample (0,65 km)" - like GeoSetter, with decimal comma
     */
    static String text(NearbyPlace nearby) {
        final String parts = Stream.of(nearby.place().state(), nearby.place().city(), nearby.place().sublocation())
                .filter(Objects::nonNull)
                .collect(Collectors.joining("; "));
        return parts + String.format(Locale.GERMAN, " (%.2f km)", nearby.distanceKm());
    }
}

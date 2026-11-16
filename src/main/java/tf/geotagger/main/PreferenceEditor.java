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
import java.math.BigDecimal;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.util.StringConverter;
import jfxtras.styles.jmetro.JMetro;
import jfxtras.styles.jmetro.Style;
import tf.exiftool.ExifToolException;
import tf.geo.LatLon;
import tf.geo.service.GeoServiceException;
import tf.geo.service.ReverseGeocoder;
import tf.geotagger.enrich.PlaceService;
import tf.geotagger.metadata.ExifToolLocator;
import tf.helper.javafx.AbstractStage;

/**
 * Preferences of GeoTagger: the ExifTool executable and the service for "Find place". Built in code like GPXEditor's
 * PreferenceEditor.
 */
public class PreferenceEditor extends AbstractStage {
    private static final PreferenceEditor INSTANCE = new PreferenceEditor();
    // "Check" of the place service: Munich
    private static final LatLon CHECK_POSITION = new LatLon(48.137, 11.575);
    private static final double DEFAULT_RADIUS_KM = 1.0;

    private final TextField exifToolPath = new TextField();
    private final Label exifToolCheck = new Label();
    private final ChoiceBox<PlaceService> placeService = new ChoiceBox<>();
    private final TextField geoNamesUsername = new TextField();
    private final TextField mapboxAccessToken = new TextField();
    private final TextField placeLanguage = new TextField();
    private final TextField placeChoiceRadius = new TextField();
    private final Button placeCheckButton = new Button("Check");
    private final Label placeCheck = new Label();

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

        // "Find place"
        placeService.getItems().setAll(PlaceService.values());
        placeService.setConverter(new StringConverter<>() {
            @Override
            public String toString(PlaceService service) {
                return service == null ? "" : service.label();
            }

            @Override
            public PlaceService fromString(String text) {
                return null;
            }
        });
        addRow(grid, 2, "Place service:", placeService);
        geoNamesUsername.setPrefColumnCount(30);
        addRow(grid, 3, "GeoNames user:", geoNamesUsername);
        mapboxAccessToken.setPrefColumnCount(30);
        addRow(grid, 4, "Mapbox access token:", mapboxAccessToken);
        placeLanguage.setPrefColumnCount(5);
        final HBox language = new HBox(8, placeLanguage, new Label("e.g. de, en - empty: service default"));
        language.setAlignment(Pos.CENTER_LEFT);
        addRow(grid, 5, "Language of places:", language);
        placeChoiceRadius.setPrefColumnCount(5);
        final HBox radius = new HBox(8, placeChoiceRadius, new Label("km - several places nearby: choose (0: never ask)"));
        radius.setAlignment(Pos.CENTER_LEFT);
        addRow(grid, 6, "Ask between places within:", radius);
        // one request with the values typed in - also before they are saved
        placeCheckButton.setOnAction(event -> checkPlaceService());
        grid.add(placeCheckButton, 0, 7);
        GridPane.setMargin(placeCheckButton, INSET_TOP);
        grid.add(placeCheck, 1, 7, 2, 1);
        GridPane.setMargin(placeCheck, INSET_TOP);

        final Button saveButton = new Button("Save");
        saveButton.setOnAction(event -> {
            GeoTaggerPreferences.EXIFTOOL_PATH.put(exifToolPath.getText().trim());
            GeoTaggerPreferences.PLACE_SERVICE.put(placeService.getValue().name());
            GeoTaggerPreferences.GEONAMES_USERNAME.put(geoNamesUsername.getText().trim());
            GeoTaggerPreferences.MAPBOX_ACCESS_TOKEN.put(mapboxAccessToken.getText().trim());
            GeoTaggerPreferences.PLACE_LANGUAGE.put(placeLanguage.getText().trim());
            GeoTaggerPreferences.PLACE_CHOICE_RADIUS_KM.put(radiusKm(placeChoiceRadius.getText()));
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
        placeService.setValue(PlaceServiceFactory.service());
        geoNamesUsername.setText(GeoTaggerPreferences.GEONAMES_USERNAME.getAsString());
        mapboxAccessToken.setText(GeoTaggerPreferences.MAPBOX_ACCESS_TOKEN.getAsString());
        placeLanguage.setText(GeoTaggerPreferences.PLACE_LANGUAGE.getAsString());
        final Double radiusKm = GeoTaggerPreferences.PLACE_CHOICE_RADIUS_KM.getAsType();
        placeChoiceRadius.setText(BigDecimal.valueOf(radiusKm).stripTrailingZeros().toPlainString());
        placeCheck.setText("");
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

    // a request in the background with the values typed in - the window stays usable, also offline (timeouts of the
    // transport)
    private void checkPlaceService() {
        final ReverseGeocoder geocoder;
        try {
            geocoder = PlaceServiceFactory.create(placeService.getValue(), geoNamesUsername.getText(), mapboxAccessToken.getText(),
                    placeLanguage.getText());
        } catch (GeoServiceException ex) {
            placeCheck.setText(ex.getMessage());
            return;
        }
        placeCheckButton.setDisable(true);
        placeCheck.setText("Asking " + geocoder.name() + " ...");
        final Thread check = new Thread(() -> {
            String result;
            try {
                result = geocoder.reverse(CHECK_POSITION)
                        .map(place -> "OK - " + Stream.of(place.city(), place.state()).filter(Objects::nonNull)
                                .collect(Collectors.joining(", ")))
                        .orElse("OK");
            } catch (GeoServiceException | RuntimeException ex) {
                result = String.valueOf(ex.getMessage());
            }
            final String text = result;
            Platform.runLater(() -> {
                placeCheck.setText(text);
                placeCheckButton.setDisable(false);
            });
        }, "GeoTagger-check");
        check.setDaemon(true);
        check.start();
    }

    // km from the text field - a comma counts as decimal point, invalid or negative input gives the default
    private static double radiusKm(String text) {
        try {
            final double value = Double.parseDouble(text.trim().replace(',', '.'));
            return value >= 0 && Double.isFinite(value) ? value : DEFAULT_RADIUS_KM;
        } catch (NumberFormatException ex) {
            return DEFAULT_RADIUS_KM;
        }
    }

    // label in the first column, the control over the other two
    private static void addRow(GridPane grid, int row, String label, Node control) {
        final Label rowLabel = new Label(label);
        grid.add(rowLabel, 0, row);
        GridPane.setMargin(rowLabel, INSET_TOP);
        grid.add(control, 1, row, 2, 1);
        GridPane.setMargin(control, INSET_TOP);
    }

    private void check() {
        try {
            exifToolCheck.setText("ExifTool " + ExifToolLocator.version(Path.of(exifToolPath.getText().trim())));
        } catch (ExifToolException | InvalidPathException ex) {
            exifToolCheck.setText("Not usable: " + ex.getMessage());
        }
    }
}

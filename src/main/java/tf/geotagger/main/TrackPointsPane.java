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

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import tf.geo.LatLon;
import tf.geotagger.edit.FieldText;
import tf.geotagger.track.GpxTrack;
import tf.geotagger.track.TrackPoints;

/**
 * Points of the tracks selected in the track list - number, time, coordinates, distance and elevation like GeoSetter.
 */
public class TrackPointsPane extends VBox {
    // local time zone of the computer - like the capture times of the files
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private final TableView<TrackPoints.Row> table = new TableView<>();
    private final Label count = new Label();
    private Consumer<LatLon> onPointSelected = position -> {};

    /**
     * Creates the empty list.
     */
    public TrackPointsPane() {
        setSpacing(4);
        setPadding(new Insets(4));
        table.setId("trackPoints");
        table.setPlaceholder(new Label("Select a loaded track to see its points"));
        table.getColumns().addAll(List.of(
                column("No.", 55,row -> Integer.toString(row.number())),
                column("Time", 70, row -> row.time() == null ? "" : TIME.format(row.time())),
                column("Coordinates", 190, row -> FieldText.formatCoordinates(row.position().latitude(), row.position().longitude())),
                column("Distance", 90, row -> String.format(Locale.ROOT, "%.2f km", row.distanceKm())),
                column("Elevation", 90, row -> row.elevation() == null ? "" : String.format(Locale.ROOT, "%.1f m", row.elevation()))));
        table.getSelectionModel().selectedItemProperty().addListener((observable, oldRow, newRow) -> {
            if (newRow != null) {
                onPointSelected.accept(newRow.position());
            }
        });
        count.setId("trackPointCount");
        VBox.setVgrow(table, Priority.ALWAYS);
        getChildren().addAll(table, count);
        showTracks(List.of());
    }

    private static TableColumn<TrackPoints.Row, String> column(String title, double width, Function<TrackPoints.Row, String> text) {
        final TableColumn<TrackPoints.Row, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(text.apply(cell.getValue())));
        column.setPrefWidth(width);
        column.setSortable(false);
        return column;
    }

    /**
     * @param tracks loaded tracks whose points are listed
     */
    void showTracks(List<GpxTrack> tracks) {
        table.getItems().setAll(TrackPoints.of(tracks));
        count.setText(table.getItems().size() + " points");
    }

    /**
     * @param handler called with the position of a point the user selected
     */
    void setOnPointSelected(Consumer<LatLon> handler) {
        onPointSelected = Objects.requireNonNull(handler);
    }
}

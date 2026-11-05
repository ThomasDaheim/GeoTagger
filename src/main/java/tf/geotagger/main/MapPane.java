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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.scene.layout.StackPane;
import tf.geo.LatLon;
import tf.geo.map.LeafletMapView;
import tf.geo.map.MapConfig;
import tf.geo.map.MapListener;
import tf.geotagger.model.MediaFile;

/**
 * The map of the main window: markers for the files, selection, moving markers - see spec, "Leaflet-Karte".
 */
public class MapPane extends StackPane {
    private static final Logger LOGGER = Logger.getLogger(MapPane.class.getName());

    private final LeafletMapView mapView = new LeafletMapView();
    // files of the markers shown last - the map reports marker ids
    private final Map<String, MediaFile> filesById = new HashMap<>();
    private BiConsumer<MediaFile, Boolean> onFileClicked = (file, shiftDown) -> { };
    private BiConsumer<MediaFile, LatLon> onFileMoved = (file, position) -> { };

    public MapPane() {
        getChildren().add(mapView);
        mapView.setMapListener(new MapListener() {
            @Override
            public void markerClicked(String id, boolean shiftDown) {
                final MediaFile file = filesById.get(id);
                if (file != null) {
                    onFileClicked.accept(file, shiftDown);
                }
            }

            @Override
            public void markerMoved(String id, LatLon position) {
                final MediaFile file = filesById.get(id);
                if (file != null) {
                    onFileMoved.accept(file, position);
                }
            }
        });
        mapView.displayMap(MapConfig.defaults()).exceptionally(ex -> {
            LOGGER.log(Level.SEVERE, "Map can't be shown", ex);
            return null;
        });
    }

    /**
     * @return true once the map page is loaded
     */
    boolean isReady() {
        return mapView.isReady();
    }

    /**
     * Shows a marker for each file with coordinates - selected ones red and outside the clusters.
     *
     * @param files all files of the list
     * @param selected the selected files
     */
    void showFiles(List<MediaFile> files, List<MediaFile> selected) {
        filesById.clear();
        files.forEach(file -> filesById.put(MapMarkers.id(file), file));
        mapView.showMarkers(MapMarkers.of(files, selected));
    }

    /**
     * @param files files to show - those without coordinates are ignored
     */
    void fitToFiles(List<MediaFile> files) {
        mapView.fitBounds(files.stream().map(MapMarkers::position).flatMap(Optional::stream).toList());
    }

    /**
     * @param file file to center on - nothing happens without coordinates
     */
    void panToFile(MediaFile file) {
        MapMarkers.position(file).ifPresent(mapView::panTo);
    }

    /**
     * @param handler called with the file and "shift pressed" when the user clicks a marker
     */
    void setOnFileClicked(BiConsumer<MediaFile, Boolean> handler) {
        onFileClicked = Objects.requireNonNull(handler);
    }

    /**
     * @param handler called with the file and its new position when the user dragged a (selected) marker
     */
    void setOnFileMoved(BiConsumer<MediaFile, LatLon> handler) {
        onFileMoved = Objects.requireNonNull(handler);
    }

    // for the context menu, drag & drop, the track list and the UI tests
    LeafletMapView mapView() {
        return mapView;
    }

    /**
     * Unloads the map - called when GeoTagger ends.
     */
    void dispose() {
        mapView.dispose();
    }
}
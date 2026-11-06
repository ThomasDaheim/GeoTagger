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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.input.DataFormat;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.StackPane;
import javafx.scene.web.WebView;
import tf.geo.LatLon;
import tf.geo.map.LeafletMapView;
import tf.geo.map.MapConfig;
import tf.geo.map.MapListener;
import tf.geo.map.MapMarker;
import tf.geo.map.TrackLine;
import tf.geotagger.model.MediaFile;
import tf.geotagger.track.GpxTrack;

/**
 * The map of the main window: markers for the files, selection, moving markers - see spec, "Leaflet-Karte".
 */
public class MapPane extends StackPane {
    /**
     * Content of a drag from the file list - the dragged files are the selection.
     */
    static final DataFormat SELECTION_FORMAT = new DataFormat("application/x-geotagger-selection");

    private static final Logger LOGGER = Logger.getLogger(MapPane.class.getName());

    // created in start() - the WebView takes about two seconds, the window shall appear without waiting for it
    private LeafletMapView mapView;
    // calls before start(), executed there in order
    private final List<Consumer<LeafletMapView>> pending = new ArrayList<>();
    // files of the markers shown last - the map reports marker ids
    private final Map<String, MediaFile> filesById = new HashMap<>();
    private final ContextMenu contextMenu = new ContextMenu();
    private final MenuItem setPositionHere = new MenuItem();
    // position of the right click that opened the context menu
    private LatLon contextPosition;
    private BiConsumer<MediaFile, Boolean> onFileClicked = (file, shiftDown) -> { };
    private BiConsumer<MediaFile, LatLon> onFileMoved = (file, position) -> { };
    private Supplier<List<MediaFile>> selection = List::of;
    private BiConsumer<List<MediaFile>, LatLon> onSetPosition = (files, position) -> { };
    private BiConsumer<List<MediaFile>, Boolean> onAreaSelected = (files, shiftDown) -> { };

    public MapPane() {
        // the map follows in start()
    }

    /**
     * Creates the map - called once the window is shown. Calls made before are executed now.
     */
    void start() {
        if (mapView != null) {
            return;
        }
        mapView = new LeafletMapView();
        getChildren().add(mapView);
        initContextMenu();
        initDrop();
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

            @Override
            public void areaSelected(LatLon southWest, LatLon northEast, boolean shiftDown) {
                final List<MediaFile> inside = filesById.values().stream()
                        .filter(file -> MapMarkers.position(file).filter(position -> isInside(position, southWest, northEast)).isPresent())
                        .toList();
                onAreaSelected.accept(inside, shiftDown);
            }
        });
        mapView.displayMap(MapConfig.defaults()).exceptionally(ex -> {
            LOGGER.log(Level.SEVERE, "Map can't be shown", ex);
            return null;
        });
        final List<Consumer<LeafletMapView>> calls = new ArrayList<>(pending);
        pending.clear();
        calls.forEach(call -> call.accept(mapView));
    }

    // now or once the map exists - LeafletMapView itself keeps calls until its page is loaded
    private void withMap(Consumer<LeafletMapView> call) {
        if (mapView != null) {
            call.accept(mapView);
        } else {
            pending.add(call);
        }
    }

    private static boolean isInside(LatLon position, LatLon southWest, LatLon northEast) {
        return position.latitude() >= southWest.latitude() && position.latitude() <= northEast.latitude()
                && position.longitude() >= southWest.longitude() && position.longitude() <= northEast.longitude();
    }

    // right click: "Set position of n files here" for the selection - the WebView's own menu is switched off
    private void initContextMenu() {
        setPositionHere.setId("setPositionHere");
        setPositionHere.setOnAction(event -> {
            final List<MediaFile> files = selection.get();
            if (contextPosition != null && !files.isEmpty()) {
                onSetPosition.accept(files, contextPosition);
            }
        });
        contextMenu.getItems().add(setPositionHere);
        final WebView webView = mapView.getWebView();
        webView.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (event.getButton() == MouseButton.SECONDARY) {
                contextPosition = mapView.latLonAt(event.getX(), event.getY()).orElse(null);
                final int count = selection.get().size();
                setPositionHere.setText("Set position of " + count + (count == 1 ? " file" : " files") + " here");
                setPositionHere.setDisable(count == 0 || contextPosition == null);
                contextMenu.show(webView, event.getScreenX(), event.getScreenY());
                event.consume();
            } else {
                contextMenu.hide();
            }
        });
    }

    // files dragged from the list get the position where they are dropped
    private void initDrop() {
        final WebView webView = mapView.getWebView();
        webView.setOnDragOver(event -> {
            if (event.getDragboard().hasContent(SELECTION_FORMAT)) {
                event.acceptTransferModes(TransferMode.LINK);
                event.consume();
            }
        });
        webView.setOnDragDropped(event -> {
            event.setDropCompleted(event.getDragboard().hasContent(SELECTION_FORMAT) && dropSelectionAt(event.getX(), event.getY()));
            event.consume();
        });
    }

    /**
     * The selected files get the position at this point of the map - what a drop from the file list does.
     *
     * @param x x relative to the WebView
     * @param y y relative to the WebView
     * @return true if positions were set - false without selection or while the map isn't ready
     */
    boolean dropSelectionAt(double x, double y) {
        final List<MediaFile> files = selection.get();
        final LatLon position = mapView == null ? null : mapView.latLonAt(x, y).orElse(null);
        if (position == null || files.isEmpty()) {
            return false;
        }
        onSetPosition.accept(files, position);
        return true;
    }

    /**
     * @return true once the map is created and its page is loaded
     */
    boolean isReady() {
        return mapView != null && mapView.isReady();
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
        final List<MapMarker> markers = MapMarkers.of(files, selected);
        withMap(view -> view.showMarkers(markers));
    }

    /**
     * @param files files to show - those without coordinates are ignored
     */
    void fitToFiles(List<MediaFile> files) {
        final List<LatLon> positions = files.stream().map(MapMarkers::position).flatMap(Optional::stream).toList();
        withMap(view -> view.fitBounds(positions));
    }

    /**
     * @param file file to center on - nothing happens without coordinates
     */
    void panToFile(MediaFile file) {
        MapMarkers.position(file).ifPresent(position -> withMap(view -> view.panTo(position)));
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

    /**
     * @param selection supplies the selected files - the context menu and drag &amp; drop set their position
     */
    void setSelectionSupplier(Supplier<List<MediaFile>> selection) {
        this.selection = Objects.requireNonNull(selection);
    }

    /**
     * @param handler called with the files and the position from the context menu or a drop on the map
     */
    void setOnSetPosition(BiConsumer<List<MediaFile>, LatLon> handler) {
        onSetPosition = Objects.requireNonNull(handler);
    }

    /**
     * @param handler called with the files with coordinates in the rectangle drawn with ctrl + drag, and "shift pressed"
     */
    void setOnAreaSelected(BiConsumer<List<MediaFile>, Boolean> handler) {
        onAreaSelected = Objects.requireNonNull(handler);
    }

    /**
     * Shows each segment of a loaded track as its own line - in the colour from the GPX file if it has one.
     *
     * @param track a loaded track
     * @param fallbackColor CSS colour for segments without colour in the file
     */
    void showTrack(GpxTrack track, String fallbackColor) {
        final List<TrackLine> lines = track.lines().stream()
                .map(line -> new TrackLine(line.points(), line.color() != null ? line.color() : fallbackColor))
                .toList();
        withMap(view -> view.showTrack(track.id(), lines));
    }

    /**
     * @param track a track shown with showTrack()
     * @param visible false hides it, true shows it again
     */
    void setTrackVisible(GpxTrack track, boolean visible) {
        withMap(view -> view.setTrackVisible(track.id(), visible));
    }

    /**
     * @param track a track shown with showTrack()
     */
    void removeTrack(GpxTrack track) {
        withMap(view -> view.removeTrack(track.id()));
    }

    // for the UI tests - null before start()
    LeafletMapView mapView() {
        return mapView;
    }

    /**
     * Unloads the map - called when GeoTagger ends.
     */
    void dispose() {
        pending.clear();
        if (mapView != null) {
            mapView.dispose();
        }
    }
}
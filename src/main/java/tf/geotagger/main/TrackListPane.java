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
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javafx.application.Platform;
import javafx.beans.Observable;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import tf.geotagger.track.GpxReader;
import tf.geotagger.track.GpxTrack;
import tf.geotagger.track.GpxContent;

/**
 * The track list: GPX files of the folder with a check box to load and show them on the map, "+ GPX" for more
 * - see spec, "Track-Liste".
 */
public class TrackListPane extends VBox {
    // one colour per track, in this order
    private static final List<String> COLORS = List.of("#e6194b", "#3cb44b", "#4363d8", "#f58231", "#911eb4", "#42d4f4");

    // the list updates its cells when the state of a track changes
    private final ObservableList<GpxTrack> tracks = FXCollections.observableArrayList(track -> new Observable[] {track.stateProperty()});
    private final ListView<GpxTrack> trackList = new ListView<>(tracks);
    private final Map<GpxTrack, String> colors = new HashMap<>();
    // GPX files are read in the background - one at a time
    private final ExecutorService loader = Executors.newSingleThreadExecutor(runnable -> {
        final Thread thread = new Thread(runnable, "GeoTagger-gpx");
        thread.setDaemon(true);
        return thread;
    });
    private MapPane map;
    private int nextColor = 0;

    public TrackListPane() {
        setSpacing(4);
        setPadding(new Insets(4));
        trackList.setId("trackList");
        trackList.setPlaceholder(new Label("No GPX files"));
        trackList.setCellFactory(list -> new TrackCell());
        VBox.setVgrow(trackList, Priority.ALWAYS);
        final Button addGpxButton = new Button("+ GPX");
        addGpxButton.setId("addGpxButton");
        addGpxButton.setOnAction(event -> chooseGpxFiles());
        getChildren().addAll(trackList, addGpxButton);
    }

    /**
     * @param map the map that shows the tracks
     */
    void setMap(MapPane map) {
        this.map = map;
    }

    /**
     * Replaces the list - e.g. with the GPX files of a newly opened folder. The old tracks leave the map.
     *
     * @param gpxFiles GPX files to list, not loaded yet
     */
    void setTracks(List<Path> gpxFiles) {
        for (GpxTrack track : tracks) {
            if (track.state() == GpxTrack.State.LOADED && map != null) {
                map.removeTrack(track);
            }
        }
        colors.clear();
        nextColor = 0;
        tracks.setAll(gpxFiles.stream().map(this::newTrack).toList());
    }

    /**
     * Adds GPX files and shows them right away - files already in the list are skipped.
     *
     * @param gpxFiles GPX files to add
     */
    void addTracks(List<Path> gpxFiles) {
        for (Path file : gpxFiles) {
            if (tracks.stream().noneMatch(track -> track.file().equals(file))) {
                final GpxTrack track = newTrack(file);
                tracks.add(track);
                track.shownProperty().set(true);
            }
        }
    }

    /**
     * @return all tracks of the list
     */
    List<GpxTrack> tracks() {
        return List.copyOf(tracks);
    }

    /**
     * @return the tracks loaded so far - e.g. for the track sync
     */
    List<GpxTrack> loadedTracks() {
        return tracks.stream().filter(track -> track.state() == GpxTrack.State.LOADED).toList();
    }

    /**
     * Stops the background thread - called when GeoTagger ends.
     */
    void dispose() {
        loader.shutdownNow();
    }

    private GpxTrack newTrack(Path file) {
        final GpxTrack track = new GpxTrack(file);
        track.shownProperty().addListener((observable, wasShown, shown) -> shownChanged(track, shown));
        return track;
    }

    private void shownChanged(GpxTrack track, boolean shown) {
        switch (track.state()) {
            case NOT_LOADED -> {
                if (shown) {
                    load(track);
                }
            }
            case LOADED -> {
                if (map != null) {
                    map.setTrackVisible(track, shown);
                }
            }
            case FAILED -> {
                // an unreadable file can't be shown - uncheck again after this change is done
                if (shown) {
                    Platform.runLater(() -> track.shownProperty().set(false));
                }
            }
            case LOADING -> {
                // applied once it is loaded
            }
        }
    }

    private void load(GpxTrack track) {
        track.loading();
        final Path file = track.file();
        loader.submit(() -> {
            GpxContent content = null;
            String failure = null;
            try {
                content = GpxReader.readContent(file);
            } catch (IOException | RuntimeException ex) {
                failure = String.valueOf(ex.getMessage());
            }
            final GpxContent result = content;
            final String error = failure;
            Platform.runLater(() -> loaded(track, result, error));
        });
    }

    private void loaded(GpxTrack track, GpxContent content, String failure) {
        if (!tracks.contains(track)) {
            // another folder was opened meanwhile
            return;
        }
        if (content != null) {
            track.loaded(content);
        } else {
            track.failed(failure);
        }
        if (track.state() != GpxTrack.State.LOADED) {
            track.shownProperty().set(false);
            return;
        }
        if (map != null) {
            // colours of the file first - the colour of the list only for lines without
            map.showTrack(track, colors.computeIfAbsent(track, key -> COLORS.get(nextColor++ % COLORS.size())));
            // unchecked while loading
            map.setTrackVisible(track, track.shownProperty().get());
        }
    }

    private void chooseGpxFiles() {
        final FileChooser chooser = new FileChooser();
        chooser.setTitle("Add GPX files");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("GPX files", "*.gpx"));
        final File recent = new File(GeoTaggerPreferences.RECENT_FOLDER.getAsString());
        if (recent.isDirectory()) {
            chooser.setInitialDirectory(recent);
        }
        final List<File> selected = chooser.showOpenMultipleDialog(getScene() == null ? null : getScene().getWindow());
        if (selected != null) {
            addTracks(selected.stream().map(File::toPath).toList());
        }
    }

    // check box bound to "shown", name in red with "(unreadable)" if the file can't be read
    private static final class TrackCell extends ListCell<GpxTrack> {
        private final CheckBox check = new CheckBox();
        private GpxTrack bound;

        @Override
        protected void updateItem(GpxTrack track, boolean empty) {
            super.updateItem(track, empty);
            if (bound != null) {
                check.selectedProperty().unbindBidirectional(bound.shownProperty());
                bound = null;
            }
            if (empty || track == null) {
                setText(null);
                setGraphic(null);
                setTooltip(null);
                setStyle("");
                return;
            }
            bound = track;
            check.selectedProperty().bindBidirectional(track.shownProperty());
            final boolean failed = track.state() == GpxTrack.State.FAILED;
            setText(track.file().getFileName() + (failed ? " (unreadable)" : ""));
            setStyle(failed ? "-fx-text-fill: red;" : "");
            setTooltip(failed ? new Tooltip(track.failure()) : null);
            setGraphic(check);
        }
    }
}
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
package tf.geotagger.track;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;

/**
 * One GPX file of the track list: loaded on demand, shown on the map or not - see spec, "Track-Liste".
 * Changed on the JavaFX thread only.
 */
public final class GpxTrack {
    /**
     * Loading state of the file.
     */
    public enum State {
        NOT_LOADED, LOADING, LOADED, FAILED
    }

    private final Path file;
    private final ObjectProperty<State> state = new SimpleObjectProperty<>(State.NOT_LOADED);
    private final BooleanProperty shown = new SimpleBooleanProperty(false);
    private List<TrackPoint> points = List.of();
    private String failure = "";

    /**
     * @param file the GPX file
     */
    public GpxTrack(Path file) {
        this.file = Objects.requireNonNull(file);
    }

    /**
     * @return the GPX file
     */
    public Path file() {
        return file;
    }

    /**
     * @return id of the track on the map
     */
    public String id() {
        return file.toString();
    }

    /**
     * @return the points - empty until loaded
     */
    public List<TrackPoint> points() {
        return points;
    }

    /**
     * @return why the file couldn't be loaded - empty otherwise
     */
    public String failure() {
        return failure;
    }

    /**
     * @return the loading state
     */
    public State state() {
        return state.get();
    }

    /**
     * @return the loading state, observable
     */
    public ObjectProperty<State> stateProperty() {
        return state;
    }

    /**
     * @return true while the user wants to see the track on the map
     */
    public BooleanProperty shownProperty() {
        return shown;
    }

    /**
     * The file is read in the background now.
     */
    public void loading() {
        state.set(State.LOADING);
    }

    /**
     * @param trackPoints points read from the file - none counts as failure
     */
    public void loaded(List<TrackPoint> trackPoints) {
        if (trackPoints.isEmpty()) {
            failed("No track points");
            return;
        }
        points = List.copyOf(trackPoints);
        failure = "";
        state.set(State.LOADED);
    }

    /**
     * @param message why the file couldn't be read
     */
    public void failed(String message) {
        points = List.of();
        failure = Objects.requireNonNullElse(message, "");
        state.set(State.FAILED);
    }
}
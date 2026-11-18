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
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import jfxtras.styles.jmetro.JMetro;
import jfxtras.styles.jmetro.Style;
import tf.geotagger.edit.FieldText;
import tf.geotagger.model.MediaFile;
import tf.geotagger.sync.CameraClock;
import tf.geotagger.sync.FileScope;
import tf.geotagger.sync.SyncParameters;
import tf.geotagger.sync.SyncResult;
import tf.geotagger.sync.SyncSummary;
import tf.geotagger.sync.TrackSource;
import tf.geotagger.sync.TrackSync;
import tf.geotagger.track.GpxReader;
import tf.geotagger.track.TrackPoint;
import tf.geotagger.track.TrackTimeline;

/**
 * The track sync dialog - see spec, section "Track-Sync-Dialog", and plan 6: inputs at the top, a preview that is
 * computed anew on every change, apply as one undo step (done by the caller).
 */
final class SyncDialog {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String INVALID = "invalid";
    private static final int MAX_DAYS = 999;
    private static final int MAX_GAP_MINUTES = 1440;
    // unusable GPX files listed below the summary at most
    private static final int MAX_FAILURES = 5;

    /**
     * What the dialog works on.
     *
     * @param folder the loaded folder - file names are shown relative to it
     * @param listed the files of the list (filtered)
     * @param selected the files the user selected - empty if the selection was made by GeoTagger
     * @param loadedPoints the points of the loaded tracks
     * @param loadedTracks number of loaded tracks
     * @param makerZones zones of the maker notes of some files - for the suggested camera zone
     * @param cache GPX files and folders read in this session, by path - filled by the dialog
     */
    record SyncInput(Path folder, List<MediaFile> listed, List<MediaFile> selected, List<TrackPoint> loadedPoints,
            int loadedTracks, Collection<ZoneOffset> makerZones, Map<Path, TrackTimeline.LoadResult> cache) {
    }

    /**
     * What the user applied.
     *
     * @param results the rows of the preview
     * @param parameters the parameters they were computed with
     */
    record SyncOutcome(List<SyncResult> results, SyncParameters parameters) {
    }

    private final SyncInput input;
    private final Stage stage = new Stage();
    private final AtomicReference<SyncOutcome> outcome = new AtomicReference<>();
    private final TrackTimeline loadedTimeline;

    private final ToggleGroup sourceGroup = new ToggleGroup();
    private final RadioButton sourceLoaded = radio("syncSourceLoaded", "", sourceGroup);
    private final RadioButton sourceFile = radio("syncSourceFile", TrackSource.GPX_FILE.label() + ":", sourceGroup);
    private final RadioButton sourceFolder = radio("syncSourceFolder", TrackSource.GPX_FOLDER.label() + " (with subfolders):", sourceGroup);
    private final TextField gpxFile = field("syncGpxFile", 40);
    private final TextField gpxFolder = field("syncGpxFolder", 40);
    private final ToggleGroup filesGroup = new ToggleGroup();
    private final RadioButton filesWithout = radio("syncFilesWithout", "Without coordinates", filesGroup);
    private final RadioButton filesSelection = radio("syncFilesSelection", "", filesGroup);
    private final CheckBox overwrite = check("syncOverwrite", "Overwrite existing");
    private final ComboBox<String> zone = new ComboBox<>();
    private final ChoiceBox<String> offsetSign = new ChoiceBox<>();
    private final TextField offsetDays = field("syncOffsetDays", 3);
    private final TextField offsetHours = field("syncOffsetHours", 2);
    private final TextField offsetMinutes = field("syncOffsetMinutes", 2);
    private final TextField offsetSeconds = field("syncOffsetSeconds", 2);
    private final Label cameraClock = new Label();
    private final TextField maxGap = field("syncMaxGap", 4);
    private final ToggleGroup assignmentGroup = new ToggleGroup();
    private final RadioButton interpolate = radio("syncInterpolate", "Interpolate between track points", assignmentGroup);
    private final RadioButton nearest = radio("syncNearest", "Nearest track point", assignmentGroup);
    private final CheckBox onlyPast = check("syncOnlyPast", "Ignore track points in the future");
    private final CheckBox takeAltitude = check("syncTakeAltitude", "Take altitude from track");
    private final CheckBox writeZone = check("syncWriteZone", "Write time zone to the file");
    private final TableView<SyncResult> preview = new TableView<>();
    private final Label summary = new Label();
    private final Button applyButton = new Button("Apply");
    private final Button cancelButton = new Button("Cancel");
    private final Timeline clock = new Timeline(new KeyFrame(javafx.util.Duration.seconds(1), event -> updateClock()));

    // reads GPX files in the background - one at a time, ends with the dialog
    private final ExecutorService reader = Executors.newSingleThreadExecutor(runnable -> {
        final Thread thread = new Thread(runnable, "GeoTagger-sync-gpx");
        thread.setDaemon(true);
        return thread;
    });
    // the GPX file or folder whose points are shown, null for the loaded tracks or nothing chosen
    private Path sourcePath;
    // its points - empty while it is read or if it couldn't be read
    private Optional<TrackTimeline.LoadResult> sourceResult = Optional.empty();
    // shown instead of the preview while reading or if the path can't be used, null otherwise
    private String sourceMessage;

    // current state of the inputs - null while invalid
    private ZoneId currentZone;
    private Duration currentOffset;
    private Duration currentMaxGap;
    private List<SyncResult> results = List.of();

    private SyncDialog(SyncInput input) {
        this.input = input;
        this.loadedTimeline = new TrackTimeline(input.loadedPoints());
        build();
        fill(SyncSettings.load());
        listen();
        recompute();
    }

    /**
     * Shows the dialog and waits until it is closed.
     *
     * @param owner the main window
     * @param input files, tracks and the session cache
     * @return what the user applied - empty on "Cancel" / Esc
     */
    static Optional<SyncOutcome> show(Window owner, SyncInput input) {
        final SyncDialog dialog = new SyncDialog(input);
        dialog.stage.initOwner(owner);
        dialog.stage.initModality(Modality.WINDOW_MODAL);
        dialog.clock.setCycleCount(Animation.INDEFINITE);
        dialog.clock.play();
        dialog.stage.showAndWait();
        dialog.clock.stop();
        dialog.reader.shutdownNow();
        return Optional.ofNullable(dialog.outcome.get());
    }

    private void build() {
        final GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(6);
        final ColumnConstraints growing = new ColumnConstraints();
        growing.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(new ColumnConstraints(), growing);

        sourceLoaded.setText(TrackSource.LOADED_TRACKS.label() + " (" + input.loadedTracks() + ")");
        sourceLoaded.setDisable(input.loadedTracks() == 0);
        final Button browseFile = new Button("Browse...");
        browseFile.setOnAction(event -> browseFile());
        final Button browseFolder = new Button("Browse...");
        browseFolder.setOnAction(event -> browseFolder());
        grid.addRow(0, new Label("Tracks:"), new VBox(4, sourceLoaded,
                row(sourceFile, gpxFile, browseFile), row(sourceFolder, gpxFolder, browseFolder)));

        filesSelection.setText("Selection (" + input.selected().size() + ")");
        filesSelection.setDisable(input.selected().isEmpty());
        grid.addRow(1, new Label("Files:"), row(filesWithout, filesSelection, overwrite));

        zone.setId("syncZone");
        zone.setEditable(true);
        zone.getItems().setAll(ZoneId.getAvailableZoneIds().stream().sorted().toList());
        zone.setPrefWidth(220);
        grid.addRow(2, new Label("Camera time zone:"), zone);

        offsetSign.setId("syncOffsetSign");
        offsetSign.getItems().setAll("+", "-");
        grid.addRow(3, new Label("Additional offset:"), row(offsetSign, offsetDays, new Label("d"), offsetHours, new Label("h"),
                offsetMinutes, new Label("min"), offsetSeconds, new Label("s"), new Label("(positive: camera ahead)")));

        cameraClock.setId("syncCameraClock");
        grid.addRow(4, new Label("Camera clock:"), cameraClock);

        grid.addRow(5, new Label("Max. time difference:"), row(maxGap, new Label("min")));
        grid.addRow(6, new Label("Assignment:"), row(interpolate, nearest));
        grid.addRow(7, new Label("Options:"), row(onlyPast, takeAltitude, writeZone));

        buildPreview();

        summary.setId("syncSummary");
        summary.setWrapText(true);
        applyButton.setId("syncApply");
        applyButton.setDefaultButton(true);
        cancelButton.setId("syncCancel");
        cancelButton.setCancelButton(true);
        ButtonBar.setButtonData(applyButton, ButtonBar.ButtonData.OK_DONE);
        ButtonBar.setButtonData(cancelButton, ButtonBar.ButtonData.CANCEL_CLOSE);
        final ButtonBar buttons = new ButtonBar();
        buttons.getButtons().addAll(applyButton, cancelButton);
        applyButton.setOnAction(event -> apply());
        cancelButton.setOnAction(event -> stage.close());

        final VBox content = new VBox(10, grid, preview, summary, buttons);
        VBox.setVgrow(preview, Priority.ALWAYS);
        content.setPadding(new Insets(10));
        final Scene scene = new Scene(content, 900, 680);
        // same look as the main window
        new JMetro(Style.LIGHT).setScene(scene);
        scene.getStylesheets().add(GeoTaggerManager.STYLESHEET);
        stage.setScene(scene);
        stage.setTitle(GeoTaggerManager.TITLE + " - Track sync");
    }

    private void buildPreview() {
        preview.setId("syncPreview");
        preview.setPlaceholder(new Label("No files to sync"));
        final Function<Path, String> name = path -> input.folder() == null ? path.getFileName().toString()
                : input.folder().relativize(path).toString();
        preview.getColumns().add(column("File", 220, result -> name.apply(result.file().path())));
        preview.getColumns().add(column("Camera time", 150, result -> result.file().captureTime() == null ? ""
                : TIME.format(result.file().captureTime().dateTime()) + (result.file().captureTime().utc() ? " UTC" : "")));
        preview.getColumns().add(column("Corrected time (UTC)", 150, result -> result.correctedTime() == null ? ""
                : TIME.format(result.correctedTime().atOffset(ZoneOffset.UTC))));
        preview.getColumns().add(column("Position", 220, result -> result.hasPosition()
                ? FieldText.formatCoordinates(result.latitude(), result.longitude()) : ""));
        preview.getColumns().add(column("Gap", 90, result -> SyncSummary.gap(result.gap())));
        preview.setRowFactory(table -> new TableRow<>() {
            @Override
            protected void updateItem(SyncResult item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("sync-matched", "sync-large-gap", "sync-no-match");
                if (!empty && item != null) {
                    getStyleClass().add(switch (item.status()) {
                        case MATCHED -> "sync-matched";
                        case LARGE_GAP -> "sync-large-gap";
                        case NO_MATCH, NO_CAPTURE_TIME -> "sync-no-match";
                    });
                }
            }
        });
    }

    // the stored parameters, all but the zone - that comes from the files (user decision 18.11.2026)
    private void fill(SyncSettings settings) {
        TrackSource source = settings.source();
        if (source == TrackSource.LOADED_TRACKS && input.loadedTracks() == 0) {
            // nothing loaded: the remembered GPX file, otherwise the remembered folder
            source = settings.gpxFile().isBlank() && !settings.gpxFolder().isBlank() ? TrackSource.GPX_FOLDER : TrackSource.GPX_FILE;
        }
        switch (source) {
            case LOADED_TRACKS -> sourceGroup.selectToggle(sourceLoaded);
            case GPX_FILE -> sourceGroup.selectToggle(sourceFile);
            case GPX_FOLDER -> sourceGroup.selectToggle(sourceFolder);
        }
        gpxFile.setText(settings.gpxFile());
        gpxFolder.setText(settings.gpxFolder());
        readSource();
        // own selection: "Selection" like the actions of the toolbar (plan 6)
        filesGroup.selectToggle(input.selected().isEmpty() ? filesWithout : filesSelection);
        overwrite.setSelected(settings.overwrite());
        final long seconds = settings.offset().toSeconds();
        offsetSign.setValue(seconds < 0 ? "-" : "+");
        final long absolute = Math.abs(seconds);
        offsetDays.setText(Long.toString(absolute / 86400));
        offsetHours.setText(Long.toString(absolute % 86400 / 3600));
        offsetMinutes.setText(Long.toString(absolute % 3600 / 60));
        offsetSeconds.setText(Long.toString(absolute % 60));
        maxGap.setText(Long.toString(settings.maxGap().toMinutes()));
        assignmentGroup.selectToggle(settings.interpolate() ? interpolate : nearest);
        onlyPast.setSelected(settings.onlyPast());
        takeAltitude.setSelected(settings.takeAltitude());
        writeZone.setSelected(settings.writeTimeZone());
        zone.getEditor().setText(SyncParameters.suggestedCameraZone(scope(), input.makerZones(), ZoneId.systemDefault()).getId());
    }

    private void listen() {
        sourceGroup.selectedToggleProperty().addListener((observable, oldValue, newValue) -> {
            readSource();
            recompute();
        });
        // Enter or leaving the field reads the path typed in
        for (TextField path : List.of(gpxFile, gpxFolder)) {
            path.setOnAction(event -> {
                readSource();
                recompute();
            });
            path.focusedProperty().addListener((observable, oldValue, focused) -> {
                if (!focused) {
                    readSource();
                    recompute();
                }
            });
        }
        filesGroup.selectedToggleProperty().addListener((observable, oldValue, newValue) -> recompute());
        assignmentGroup.selectedToggleProperty().addListener((observable, oldValue, newValue) -> recompute());
        for (CheckBox box : List.of(overwrite, onlyPast, takeAltitude, writeZone)) {
            box.selectedProperty().addListener((observable, oldValue, newValue) -> recompute());
        }
        for (TextField field : List.of(offsetDays, offsetHours, offsetMinutes, offsetSeconds, maxGap, zone.getEditor())) {
            field.textProperty().addListener((observable, oldValue, newValue) -> recompute());
        }
        offsetSign.valueProperty().addListener((observable, oldValue, newValue) -> recompute());
    }

    // the files of the chosen scope
    private List<MediaFile> scope() {
        return TrackSync.scope(input.listed(), input.selected(), fileScope(), overwrite.isSelected());
    }

    private FileScope fileScope() {
        return filesGroup.getSelectedToggle() == filesSelection ? FileScope.SELECTION : FileScope.WITHOUT_COORDINATES;
    }

    // reads the inputs, marks invalid ones and computes the preview anew
    private void recompute() {
        overwrite.setDisable(fileScope() != FileScope.SELECTION);
        currentZone = zone(zone.getEditor().getText());
        mark(zone, currentZone != null);
        currentOffset = offset();
        currentMaxGap = number(maxGap, 1, MAX_GAP_MINUTES) == null ? null : Duration.ofMinutes(number(maxGap, 1, MAX_GAP_MINUTES));
        mark(maxGap, currentMaxGap != null);
        updateClock();

        final List<MediaFile> files = scope();
        final int skipped = fileScope() == FileScope.SELECTION ? input.selected().size() - files.size() : 0;
        final Optional<TrackTimeline> timeline = timeline();
        final String problem = sourceMessage != null ? sourceMessage
                : currentZone == null ? "Unknown time zone"
                : currentOffset == null ? "Invalid offset"
                : currentMaxGap == null ? "Max. time difference must be 1 - " + MAX_GAP_MINUTES + " min"
                : timeline.isEmpty() ? "No track points" : null;
        if (problem != null) {
            results = List.of();
            preview.getItems().clear();
            summary.setText(problem + failures());
        } else {
            results = TrackSync.match(files, timeline.get(), parameters());
            preview.getItems().setAll(results);
            summary.setText(SyncSummary.text(results, skipped) + failures());
        }
        applyButton.setDisable(results.stream().noneMatch(SyncResult::hasPosition));
    }

    // the track points of the chosen source
    private Optional<TrackTimeline> timeline() {
        if (sourceGroup.getSelectedToggle() == sourceLoaded) {
            return loadedTimeline.isEmpty() ? Optional.empty() : Optional.of(loadedTimeline);
        }
        return sourceResult.map(TrackTimeline.LoadResult::timeline).filter(timeline -> !timeline.isEmpty());
    }

    // GPX files of the source that couldn't be used - at most MAX_FAILURES lines
    private String failures() {
        if (sourceGroup.getSelectedToggle() == sourceLoaded || sourceResult.isEmpty() || sourceResult.get().failures().isEmpty()) {
            return "";
        }
        final StringBuilder text = new StringBuilder();
        sourceResult.get().failures().entrySet().stream().limit(MAX_FAILURES).forEach(failure ->
                text.append('\n').append(failure.getKey().getFileName()).append(": ").append(failure.getValue()));
        if (sourceResult.get().failures().size() > MAX_FAILURES) {
            text.append("\n...");
        }
        return text.toString();
    }

    // the path of the chosen source - read in the background unless read before in this session
    private void readSource() {
        final TextField field = sourceGroup.getSelectedToggle() == sourceFile ? gpxFile
                : sourceGroup.getSelectedToggle() == sourceFolder ? gpxFolder : null;
        if (field == null) {
            sourcePath = null;
            sourceResult = Optional.empty();
            sourceMessage = null;
            return;
        }
        final Path path = path(field.getText());
        if (path == null) {
            sourcePath = null;
            sourceResult = Optional.empty();
            sourceMessage = field == gpxFile ? "Choose a GPX file" : "Choose a folder with GPX files";
            return;
        }
        if (path.equals(sourcePath)) {
            // read already or being read
            return;
        }
        sourcePath = path;
        final TrackTimeline.LoadResult cached = input.cache().get(path);
        if (cached != null) {
            sourceResult = Optional.of(cached);
            sourceMessage = null;
            return;
        }
        sourceResult = Optional.empty();
        sourceMessage = "Reading GPX files ...";
        final boolean folder = field == gpxFolder;
        final Task<TrackTimeline.LoadResult> task = new Task<>() {
            @Override
            protected TrackTimeline.LoadResult call() throws IOException {
                final List<Path> files = folder ? GpxReader.findGpxFiles(path, true) : List.of(path);
                if (files.isEmpty()) {
                    throw new IOException("No GPX files in " + path);
                }
                return TrackTimeline.load(files, (done, total) -> updateMessage("Reading GPX files: " + done + " / " + total));
            }
        };
        task.messageProperty().addListener((observable, oldValue, message) -> {
            if (path.equals(sourcePath) && message != null && !message.isEmpty()) {
                sourceMessage = message;
                summary.setText(message);
            }
        });
        task.setOnSucceeded(event -> {
            input.cache().put(path, task.getValue());
            if (path.equals(sourcePath)) {
                sourceResult = Optional.of(task.getValue());
                sourceMessage = null;
                recompute();
            }
        });
        task.setOnFailed(event -> {
            if (path.equals(sourcePath)) {
                sourceResult = Optional.empty();
                sourceMessage = String.valueOf(task.getException().getMessage());
                recompute();
            }
        });
        reader.submit(task);
    }

    private void browseFile() {
        final FileChooser chooser = new FileChooser();
        chooser.setTitle("Select a GPX file");
        chooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("GPX files", "*.gpx", "*.GPX"),
                new FileChooser.ExtensionFilter("All files", "*.*"));
        final Path current = path(gpxFile.getText());
        if (current != null && current.getParent() != null && Files.isDirectory(current.getParent())) {
            chooser.setInitialDirectory(current.getParent().toFile());
        }
        final File selected = chooser.showOpenDialog(stage);
        if (selected != null) {
            gpxFile.setText(selected.getAbsolutePath());
            sourceGroup.selectToggle(sourceFile);
            readSource();
            recompute();
        }
    }

    private void browseFolder() {
        final DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select a folder with GPX files");
        final Path current = path(gpxFolder.getText());
        if (current != null && Files.isDirectory(current)) {
            chooser.setInitialDirectory(current.toFile());
        }
        final File selected = chooser.showDialog(stage);
        if (selected != null) {
            gpxFolder.setText(selected.getAbsolutePath());
            sourceGroup.selectToggle(sourceFolder);
            readSource();
            recompute();
        }
    }

    // absolute path of the text - null if empty or not a path
    private static Path path(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Path.of(text.trim()).toAbsolutePath().normalize();
        } catch (InvalidPathException ex) {
            return null;
        }
    }

    private SyncParameters parameters() {
        return new SyncParameters(currentZone, currentOffset, currentMaxGap, assignmentGroup.getSelectedToggle() == interpolate,
                onlyPast.isSelected(), takeAltitude.isSelected(), writeZone.isSelected());
    }

    private void apply() {
        if (applyButton.isDisabled()) {
            return;
        }
        final TrackSource source = sourceGroup.getSelectedToggle() == sourceFile ? TrackSource.GPX_FILE
                : sourceGroup.getSelectedToggle() == sourceFolder ? TrackSource.GPX_FOLDER : TrackSource.LOADED_TRACKS;
        new SyncSettings(source, gpxFile.getText().trim(), gpxFolder.getText().trim(), currentOffset, currentMaxGap,
                assignmentGroup.getSelectedToggle() == interpolate, onlyPast.isSelected(), takeAltitude.isSelected(),
                writeZone.isSelected(), overwrite.isSelected()).store();
        outcome.set(new SyncOutcome(List.copyOf(results), parameters()));
        stage.close();
    }

    private void updateClock() {
        cameraClock.setText(currentZone == null || currentOffset == null ? "-"
                : "Camera display should show: " + TIME.format(CameraClock.cameraDisplay(Instant.now(), currentZone, currentOffset)));
    }

    // sign, days, hours, minutes, seconds - an empty field counts as 0, anything else invalid gives null
    private Duration offset() {
        final Long days = number(offsetDays, 0, MAX_DAYS);
        final Long hours = number(offsetHours, 0, 23);
        final Long minutes = number(offsetMinutes, 0, 59);
        final Long seconds = number(offsetSeconds, 0, 59);
        mark(offsetDays, days != null);
        mark(offsetHours, hours != null);
        mark(offsetMinutes, minutes != null);
        mark(offsetSeconds, seconds != null);
        if (days == null || hours == null || minutes == null || seconds == null) {
            return null;
        }
        final Duration offset = Duration.ofDays(days).plusHours(hours).plusMinutes(minutes).plusSeconds(seconds);
        return "-".equals(offsetSign.getValue()) ? offset.negated() : offset;
    }

    // whole number from min to max - empty gives min; null if invalid
    private static Long number(TextField field, long min, long max) {
        final String text = field.getText() == null ? "" : field.getText().trim();
        if (text.isEmpty()) {
            return min;
        }
        try {
            final long value = Long.parseLong(text);
            return value >= min && value <= max ? value : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static ZoneId zone(String text) {
        try {
            return text == null || text.isBlank() ? null : ZoneId.of(text.trim());
        } catch (DateTimeException ex) {
            return null;
        }
    }

    private static void mark(Node node, boolean valid) {
        node.getStyleClass().remove(INVALID);
        if (!valid) {
            node.getStyleClass().add(INVALID);
        }
    }

    private static TableColumn<SyncResult, String> column(String title, double width, Function<SyncResult, String> text) {
        final TableColumn<SyncResult, String> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        column.setSortable(false);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(text.apply(cell.getValue())));
        return column;
    }

    private static HBox row(Node... nodes) {
        final HBox row = new HBox(8, nodes);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private static RadioButton radio(String id, String text, ToggleGroup group) {
        final RadioButton radio = new RadioButton(text);
        radio.setId(id);
        radio.setToggleGroup(group);
        return radio;
    }

    private static CheckBox check(String id, String text) {
        final CheckBox box = new CheckBox(text);
        box.setId(id);
        return box;
    }

    private static TextField field(String id, int columns) {
        final TextField field = new TextField();
        field.setId(id);
        field.setPrefColumnCount(columns);
        return field;
    }
}

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
import java.net.URL;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.MultipleSelectionModel;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SplitMenuButton;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.Node;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.TransferMode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import tf.exiftool.ExifTool;
import tf.exiftool.ExifToolException;
import tf.geo.LatLon;
import tf.geotagger.edit.ChangeFieldsAction;
import tf.geotagger.edit.FieldText;
import tf.geotagger.files.FolderScanner;
import tf.geotagger.metadata.ExifToolLocator;
import tf.geotagger.metadata.MetadataReader;
import tf.geotagger.metadata.MetadataWriter;
import tf.geotagger.metadata.SaveResult;
import tf.geotagger.metadata.SaveSummary;
import tf.geotagger.model.CaptureTime;
import tf.geotagger.model.Field;
import tf.geotagger.model.MediaFile;
import tf.geotagger.model.MediaFilter;
import tf.geotagger.preview.PreviewProvider;
import tf.helper.doundo.DoUndoManager;
import tf.helper.javafx.ShowAlerts;
import tf.helper.javafx.TableViewPreferences;
import tf.helper.javafx.UsefulKeyCodes;

/**
 * Controller of the main window (GeoTagger.fxml): ExifTool start, folder loading, file list with filters, preview,
 * edit dialog, map / image info, tracks, undo / redo and saving.
 */
public class GeoTagger implements Initializable {
    // "2": the columns changed with plan 4c - TableViewPreferences would put the old widths on the new columns
    private static final String TABLE_PREFERENCES = "mediaTable2";
    private static final DateTimeFormatter CAPTURE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    // style class of cells with a changed, not yet saved value - see GeoTagger.css
    private static final String CHANGED_STYLE = "changed";
    // extracted previews of RAW / HEIC / videos - emptied when GeoTagger ends
    private static final Path PREVIEW_CACHE = Path.of(System.getProperty("java.io.tmpdir"), "GeoTagger", "preview");
    // ExifTool uses one CPU core - 4 processes read a folder about three times faster (777 files: 19.3 s -> 6.4 s)
    private static final int READ_PROCESSES = Math.min(4, Runtime.getRuntime().availableProcessors());

    @FXML
    private SplitMenuButton openFolderButton;
    @FXML
    private CheckBox includeSubfoldersCheck;
    @FXML
    private Button saveButton;
    @FXML
    private Button editButton;
    @FXML
    private Button undoButton;
    @FXML
    private Button redoButton;
    @FXML
    private Button preferencesButton;
    @FXML
    private SplitPane centralSplit;
    @FXML
    private SplitPane leftSplit;
    @FXML
    private SplitPane rightSplit;
    @FXML
    private SplitPane trackSplit;
    @FXML
    private HBox filterBox;
    @FXML
    private TableView<MediaFile> mediaTable;
    @FXML
    private TableColumn<MediaFile, String> nameColumn;
    @FXML
    private TableColumn<MediaFile, String> captureTimeColumn;
    @FXML
    private TableColumn<MediaFile, String> coordinatesColumn;
    @FXML
    private TableColumn<MediaFile, String> altitudeColumn;
    @FXML
    private TableColumn<MediaFile, String> countryCodeColumn;
    @FXML
    private TableColumn<MediaFile, String> countryColumn;
    @FXML
    private TableColumn<MediaFile, String> stateColumn;
    @FXML
    private TableColumn<MediaFile, String> cityColumn;
    @FXML
    private TableColumn<MediaFile, String> sublocationColumn;
    @FXML
    private TableColumn<MediaFile, String> timeOffsetColumn;
    @FXML
    private Label statusLabel;
    @FXML
    private ProgressBar progressBar;
    @FXML
    private PreviewPane previewPane;
    @FXML
    private Tab imageInfoTab;
    @FXML
    private ImageInfoPane imageInfoPane;
    @FXML
    private MapPane mapPane;
    @FXML
    private TrackListPane trackListPane;
    @FXML
    private TrackPointsPane trackPointsPane;

    private final ObservableList<MediaFile> files = FXCollections.observableArrayList();
    private final FilteredList<MediaFile> filteredFiles = new FilteredList<>(files);
    private final ToggleGroup filterGroup = new ToggleGroup();
    private final Map<MediaFilter, ToggleButton> filterButtons = new EnumMap<>(MediaFilter.class);
    // one worker thread for loading and saving - ExifTool handles one command at a time anyway
    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        final Thread thread = new Thread(runnable, "GeoTagger-worker");
        thread.setDaemon(true);
        return thread;
    });

    private Stage stage;
    // folder the files were loaded from - names are shown relative to it
    private Path folder;
    // volatile: UI tests wait for it from their own thread
    private volatile boolean busy = false;
    // true while refreshFiles() restores the selection - the selection listeners ignore that
    private boolean restoringSelection = false;
    // counts loadFolder calls - results of an older one are dropped
    private int loadGeneration = 0;
    // DoUndoManager is a singleton - its listener is removed in stop(), otherwise it keeps this window alive
    private final ChangeListener<Number> undoListener = (observable, oldValue, newValue) -> updateButtons();
    private ExifTool exifTool;
    private MetadataReader reader;
    private MetadataWriter writer;
    private PreviewProvider previewProvider;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        initTable();
        initFilters();
        initPreview();
        initDetails();
        initMap();
        updateRecentFolders();
        DoUndoManager.getInstance().changeCountProperty().addListener(undoListener);
    }

    /**
     * Called once the stage is shown: restores the layout, starts ExifTool and guards against losing changes.
     *
     * @param stage the main window
     */
    public void lateInitialize(Stage stage) {
        this.stage = stage;
        final Boolean includeSubfolders = GeoTaggerPreferences.INCLUDE_SUBFOLDERS.getAsType();
        includeSubfoldersCheck.setSelected(includeSubfolders);
        final Double centralDivider = GeoTaggerPreferences.RECENT_CENTRAL_DIVIDER_POS.getAsType();
        centralSplit.setDividerPositions(centralDivider);
        final Double leftDivider = GeoTaggerPreferences.RECENT_LEFT_DIVIDER_POS.getAsType();
        leftSplit.setDividerPositions(leftDivider);
        final Double rightDivider = GeoTaggerPreferences.RECENT_RIGHT_DIVIDER_POS.getAsType();
        rightSplit.setDividerPositions(rightDivider);
        final Double trackDivider = GeoTaggerPreferences.RECENT_TRACK_DIVIDER_POS.getAsType();
        trackSplit.setDividerPositions(trackDivider);
        TableViewPreferences.loadTableViewPreferences(mediaTable, TABLE_PREFERENCES, GeoTaggerPreferences.INSTANCE);

        // like GPXEditor - text fields handle ctrl+z themselves, the rest of the window gets undo / redo
        stage.getScene().addEventHandler(KeyEvent.KEY_PRESSED, event -> {
            if (UsefulKeyCodes.CNTRL_Z.match(event)) {
                undo();
            }
            if (UsefulKeyCodes.CNTRL_Y.match(event)) {
                redo();
            }
        });
        // shortcuts work everywhere in the window, also while typing in a field
        stage.getScene().getAccelerators().put(UsefulKeyCodes.CNTRL_S.getKeyCodeCombination(), () -> {
            if (!busy && writer != null) {
                save();
            }
        });
        stage.getScene().getAccelerators().put(new KeyCodeCombination(KeyCode.O, KeyCombination.SHORTCUT_DOWN), () -> {
            if (!openFolderButton.isDisabled()) {
                openFolder();
            }
        });
        stage.getScene().getAccelerators().put(new KeyCodeCombination(KeyCode.E, KeyCombination.SHORTCUT_DOWN), this::editSelection);
        stage.setOnCloseRequest(event -> {
            if (busy) {
                // loading or saving - closing now could lose changes
                event.consume();
            } else if (hasUnsavedChanges()) {
                event.consume();
                confirmUnsavedChanges(stage::close);
            }
        });
        initExifTool();
        // the map takes about two seconds - the window is already shown meanwhile
        Platform.runLater(mapPane::start);
    }

    /**
     * Saves the layout and stops background work - called when GeoTagger ends.
     */
    public void stop() {
        GeoTaggerPreferences.RECENT_CENTRAL_DIVIDER_POS.put(centralSplit.getDividerPositions()[0]);
        GeoTaggerPreferences.RECENT_LEFT_DIVIDER_POS.put(leftSplit.getDividerPositions()[0]);
        GeoTaggerPreferences.RECENT_RIGHT_DIVIDER_POS.put(rightSplit.getDividerPositions()[0]);
        GeoTaggerPreferences.RECENT_TRACK_DIVIDER_POS.put(trackSplit.getDividerPositions()[0]);
        GeoTaggerPreferences.INCLUDE_SUBFOLDERS.put(includeSubfoldersCheck.isSelected());
        TableViewPreferences.saveTableViewPreferences(mediaTable, TABLE_PREFERENCES, GeoTaggerPreferences.INSTANCE);
        DoUndoManager.getInstance().changeCountProperty().removeListener(undoListener);
        worker.shutdownNow();
        previewPane.dispose();
        trackListPane.dispose();
        mapPane.dispose();
        imageInfoPane.dispose();
        closeExifTool();
        if (previewProvider != null) {
            previewProvider.clearCache();
        }
    }

    private void initTable() {
        final SortedList<MediaFile> sortedFiles = new SortedList<>(filteredFiles);
        sortedFiles.comparatorProperty().bind(mediaTable.comparatorProperty());
        mediaTable.setItems(sortedFiles);
        mediaTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        // columns can be hidden via the menu button in the header - saved with the table preferences
        mediaTable.setTableMenuButtonVisible(true);

        nameColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(displayName(cell.getValue())));
        captureTimeColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(captureTime(cell.getValue())));
        // the values themselves, like GeoSetter - not editable here, see the edit dialog
        valueColumn(coordinatesColumn, file -> FieldText.formatCoordinates(file.latitude(), file.longitude()),
                Field.LATITUDE, Field.LONGITUDE);
        valueColumn(altitudeColumn, file -> FieldText.formatAltitude(file.altitude()), Field.ALTITUDE);
        valueColumn(countryCodeColumn, file -> FieldText.format(Field.COUNTRY_CODE, file.value(Field.COUNTRY_CODE)), Field.COUNTRY_CODE);
        valueColumn(countryColumn, file -> FieldText.format(Field.COUNTRY, file.value(Field.COUNTRY)), Field.COUNTRY);
        valueColumn(stateColumn, file -> FieldText.format(Field.STATE, file.value(Field.STATE)), Field.STATE);
        valueColumn(cityColumn, file -> FieldText.format(Field.CITY, file.value(Field.CITY)), Field.CITY);
        valueColumn(sublocationColumn, file -> FieldText.format(Field.SUBLOCATION, file.value(Field.SUBLOCATION)), Field.SUBLOCATION);
        valueColumn(timeOffsetColumn, file -> FieldText.format(Field.TIME_OFFSET, file.value(Field.TIME_OFFSET)), Field.TIME_OFFSET);
        // sorted by value, not by text - empty cells first
        altitudeColumn.setComparator(Comparator.comparing(GeoTagger::altitudeValue, Comparator.nullsFirst(Comparator.naturalOrder())));
        coordinatesColumn.setComparator(Comparator.comparing(GeoTagger::latitudeValue, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(GeoTagger::longitudeValue, Comparator.nullsFirst(Comparator.naturalOrder())));
    }

    // "512.4 m" -> 512.4, null for an empty cell
    private static Double altitudeValue(String text) {
        return text == null || text.isBlank() ? null : (Double) FieldText.parse(Field.ALTITUDE, text.replace(" m", ""));
    }

    // first part of "N46°40'48.05"; E11°5'28.61"", null for an empty cell
    private static Double latitudeValue(String text) {
        return text == null || text.isBlank() ? null : (Double) FieldText.parse(Field.LATITUDE, text.split("; ")[0]);
    }

    private static Double longitudeValue(String text) {
        return text == null || text.isBlank() ? null : (Double) FieldText.parse(Field.LONGITUDE, text.split("; ")[1]);
    }

    // a column showing a value - its cell is marked when one of the fields has a pending change
    private static void valueColumn(TableColumn<MediaFile, String> column, Function<MediaFile, String> text, Field... fields) {
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(text.apply(cell.getValue())));
        column.setCellFactory(unused -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                final MediaFile file = empty || getTableRow() == null ? null : getTableRow().getItem();
                final boolean changed = file != null && Arrays.stream(fields).anyMatch(file.pendingChanges()::containsKey);
                getStyleClass().remove(CHANGED_STYLE);
                if (changed) {
                    getStyleClass().add(CHANGED_STYLE);
                }
            }
        });
    }

    // e.g. "777 files (464 with coordinates) - 1 selected" like GeoSetter - while loading / saving the progress is shown
    private void updateStatus() {
        if (statusLabel.textProperty().isBound()) {
            return;
        }
        final long withCoordinates = files.stream().filter(MediaFile::hasCoordinates).count();
        statusLabel.setText(files.size() + " files (" + withCoordinates + " with coordinates) - " + selectedFiles().size()
                + " selected");
    }

    private void initFilters() {
        for (MediaFilter filter : MediaFilter.values()) {
            final ToggleButton button = new ToggleButton(filter.label());
            // UI tests find the buttons by ID, e.g. #filter-WITHOUT_COORDINATES
            button.setId("filter-" + filter.name());
            button.setToggleGroup(filterGroup);
            button.setUserData(filter);
            filterButtons.put(filter, button);
            filterBox.getChildren().add(button);
        }
        filterGroup.selectedToggleProperty().addListener((observable, oldToggle, newToggle) -> {
            if (newToggle == null) {
                // one filter is always active
                oldToggle.setSelected(true);
                return;
            }
            filteredFiles.setPredicate((MediaFilter) newToggle.getUserData());
        });
        filterButtons.get(MediaFilter.ALL).setSelected(true);
        updateFilterCounts();
    }

    private void updateFilterCounts() {
        MediaFilter.counts(files).forEach((filter, count) -> filterButtons.get(filter).setText(filter.label() + " (" + count + ")"));
    }

    private void initPreview() {
        mediaTable.getSelectionModel().selectedItemProperty().addListener((observable, oldFile, newFile) -> {
            if (!restoringSelection) {
                showPreview(newFile);
            }
        });
        previewPane.getFirstButton().setOnAction(event -> select(0));
        previewPane.getPreviousButton().setOnAction(event -> select(mediaTable.getSelectionModel().getSelectedIndex() - 1));
        previewPane.getNextButton().setOnAction(event -> select(mediaTable.getSelectionModel().getSelectedIndex() + 1));
        previewPane.getLastButton().setOnAction(event -> select(mediaTable.getItems().size() - 1));
    }

    private void showPreview(MediaFile file) {
        if (file == null) {
            previewPane.clear();
            return;
        }
        final List<MediaFile> items = mediaTable.getItems();
        final int index = items.indexOf(file);
        final List<MediaFile> neighbours = new ArrayList<>();
        if (index > 0) {
            neighbours.add(items.get(index - 1));
        }
        if (index >= 0 && index + 1 < items.size()) {
            neighbours.add(items.get(index + 1));
        }
        previewPane.show(file, neighbours);
    }

    private void initDetails() {
        mediaTable.getSelectionModel().getSelectedItems().addListener((ListChangeListener<MediaFile>) change -> {
            if (!restoringSelection) {
                selectionChanged();
            }
        });
        imageInfoTab.setOnSelectionChanged(event -> showImageInfo());
        // double click on a file: edit it, like in GeoSetter
        mediaTable.setRowFactory(table -> {
            final TableRow<MediaFile> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2 && !row.isEmpty()) {
                    editSelection();
                }
            });
            return row;
        });
    }

    /**
     * Opens the edit dialog for the selected files - the changes become one undo step.
     */
    @FXML
    private void editSelection() {
        final List<MediaFile> selected = selectedFiles();
        if (busy || folder == null || selected.isEmpty()) {
            return;
        }
        EditDialog.show(stage, selected).ifPresent(changes -> {
            final Map<MediaFile, Map<Field, Object>> values = new LinkedHashMap<>();
            selected.forEach(file -> values.put(file, changes));
            perform(new ChangeFieldsAction("Edit", values));
        });
    }

    private void selectionChanged() {
        final List<MediaFile> selected = selectedFiles();
        updateButtons();
        showImageInfo();
        mapPane.showFiles(files, selected);
        // several files: the user picks them - the map stays where it is
        if (selected.size() == 1) {
            mapPane.panToFile(selected.get(0));
        }
        updateStatus();
    }

    private void initMap() {
        mapPane.setOnFileClicked((file, shiftDown) -> {
            final int index = mediaTable.getItems().indexOf(file);
            if (index < 0) {
                // hidden by the filter
                return;
            }
            if (shiftDown) {
                mediaTable.getSelectionModel().select(index);
            } else {
                mediaTable.getSelectionModel().clearAndSelect(index);
            }
            mediaTable.scrollTo(index);
        });
        mapPane.setOnFileMoved((file, position) -> setPosition(List.of(file), position, "Set position"));
        trackListPane.setMap(mapPane);
        trackListPane.setOnSelectionChanged(trackPointsPane::showTracks);
        trackPointsPane.setOnPointSelected(mapPane::panTo);
        mapPane.setSelectionSupplier(this::selectedFiles);
        mapPane.setOnSetPosition((selected, position) -> setPosition(selected, position, "Set position"));
        // ctrl + drag on the map: the files in the rectangle - with shift added to the selection
        mapPane.setOnAreaSelected((inside, shiftDown) -> {
            // in the order of the table - files hidden by the filter can't be selected
            final int[] indices = inside.stream().mapToInt(mediaTable.getItems()::indexOf).filter(index -> index >= 0)
                    .sorted().toArray();
            final MultipleSelectionModel<MediaFile> selection = mediaTable.getSelectionModel();
            if (!shiftDown) {
                selection.clearSelection();
            }
            // all at once: one change for the listeners - not a refresh and a pan to a single file per file
            if (indices.length > 0) {
                selection.selectIndices(indices[0], Arrays.copyOfRange(indices, 1, indices.length));
                // the list shows the first of them
                mediaTable.scrollTo(indices[0]);
            }
        });
        // drag the selected files onto the map
        mediaTable.setOnDragDetected(event -> {
            final List<MediaFile> selected = selectedFiles();
            // only from a file - dragging in the header resizes or moves columns, the scroll bar scrolls
            if (!selected.isEmpty() && startsOnARow(event.getPickResult().getIntersectedNode())) {
                final Dragboard dragboard = mediaTable.startDragAndDrop(TransferMode.LINK);
                final ClipboardContent content = new ClipboardContent();
                content.put(MapPane.SELECTION_FORMAT, selected.size());
                dragboard.setContent(content);
                event.consume();
            }
        });
    }

    /**
     * Drag &amp; drop of the selection starts only on a file - in the header a drag resizes or moves the columns.
     *
     * @param node node where the drag started
     * @return true if the node is part of a row with a file
     */
    boolean startsOnARow(Node node) {
        for (Node current = node; current != null && current != mediaTable; current = current.getParent()) {
            if (current instanceof TableRow<?> row) {
                return !row.isEmpty();
            }
        }
        return false;
    }

    /**
     * Sets the coordinates of files as one undo / redo step.
     *
     * @param changed files to move
     * @param position their new position
     * @param description text of the undo step
     */
    void setPosition(List<MediaFile> changed, LatLon position, String description) {
        if (changed.isEmpty()) {
            return;
        }
        final Map<MediaFile, Map<Field, Object>> values = new LinkedHashMap<>();
        for (MediaFile file : changed) {
            final Map<Field, Object> fileValues = new EnumMap<>(Field.class);
            fileValues.put(Field.LATITUDE, position.latitude());
            fileValues.put(Field.LONGITUDE, position.longitude());
            values.put(file, fileValues);
        }
        perform(new ChangeFieldsAction(description, values));
    }

    // for the UI tests
    MapPane mapPane() {
        return mapPane;
    }

    private void showImageInfo() {
        // reading all tags takes a moment - only while the tab is visible
        if (imageInfoTab.isSelected()) {
            final MediaFile file = mediaTable.getSelectionModel().getSelectedItem();
            imageInfoPane.show(file == null ? null : file.path());
        }
    }

    private List<MediaFile> selectedFiles() {
        return mediaTable.getSelectionModel().getSelectedItems().stream().filter(Objects::nonNull).toList();
    }

    /**
     * Executes a change as one undo / redo step and shows its result.
     *
     * @param action change of pending values - not done yet
     */
    private void perform(ChangeFieldsAction action) {
        if (folder == null) {
            return;
        }
        action.doAction();
        DoUndoManager.getInstance().addDoneAction(action, undoKey());
        refreshFiles();
    }

    @FXML
    private void undo() {
        if (!busy && folder != null && DoUndoManager.getInstance().canUndo(undoKey())) {
            DoUndoManager.getInstance().singleUndo(undoKey());
            refreshFiles();
        }
    }

    @FXML
    private void redo() {
        if (!busy && folder != null && DoUndoManager.getInstance().canDo(undoKey())) {
            DoUndoManager.getInstance().singleDo(undoKey());
            refreshFiles();
        }
    }

    private String undoKey() {
        return "GeoTagger:" + folder;
    }

    // MediaFile has no change events: after each change re-apply the filter, redraw the rows, update counts and
    // buttons - and keep the selection
    private void refreshFiles() {
        final MultipleSelectionModel<MediaFile> selection = mediaTable.getSelectionModel();
        final List<MediaFile> selected = selectedFiles();
        final MediaFile focused = selection.getSelectedItem();

        // the selection only flickers here - preview and image info must not clear and load the same file again
        restoringSelection = true;
        try {
            final Predicate<? super MediaFile> predicate = filteredFiles.getPredicate();
            filteredFiles.setPredicate(null);
            filteredFiles.setPredicate(predicate);

            final List<MediaFile> items = mediaTable.getItems();
            selection.clearSelection();
            for (MediaFile file : selected) {
                if (file != focused && items.contains(file)) {
                    selection.select(file);
                }
            }
            // select the focused file last - it stays the one shown in preview and image info
            if (focused != null && items.contains(focused)) {
                selection.select(focused);
            }
        } finally {
            restoringSelection = false;
        }
        // same file: nothing happens, a file gone from the filtered list: cleared
        showPreview(selection.getSelectedItem());
        showImageInfo();

        mediaTable.refresh();
        updateFilterCounts();
        updateButtons();
        mapPane.showFiles(files, selectedFiles());
        updateStatus();
    }

    private boolean hasUnsavedChanges() {
        return files.stream().anyMatch(MediaFile::isChanged);
    }

    private void updateButtons() {
        final long changed = files.stream().filter(MediaFile::isChanged).count();
        saveButton.setText(changed == 0 ? "Save" : "Save (" + changed + ")");
        // without a usable ExifTool nothing can be written - the changes stay pending
        saveButton.setDisable(busy || changed == 0 || writer == null);
        editButton.setDisable(busy || folder == null || selectedFiles().isEmpty());
        undoButton.setDisable(busy || folder == null || !DoUndoManager.getInstance().canUndo(undoKey()));
        redoButton.setDisable(busy || folder == null || !DoUndoManager.getInstance().canDo(undoKey()));
    }

    private void initExifTool() {
        if (GeoTaggerPreferences.EXIFTOOL_PATH.getAsString().isBlank()) {
            ExifToolLocator.findOnPath().ifPresent(found -> GeoTaggerPreferences.EXIFTOOL_PATH.put(found.toString()));
        }
        if (!applyExifTool()) {
            ShowAlerts.getInstance().showAlert(Alert.AlertType.WARNING, GeoTaggerManager.TITLE, "ExifTool not found",
                    "GeoTagger reads and writes metadata with ExifTool. Please select the ExifTool executable.");
            showPreferences();
        }
    }

    /**
     * (Re)starts ExifTool with the executable from the preferences.
     *
     * @return true if ExifTool is usable - otherwise no folder can be opened
     */
    // package private: UI tests switch to an unusable ExifTool this way, without the preferences dialog
    boolean applyExifTool() {
        closeExifTool();
        final String configured = GeoTaggerPreferences.EXIFTOOL_PATH.getAsString();
        try {
            final Path executable = Path.of(configured);
            final String version = ExifToolLocator.version(executable);
            exifTool = new ExifTool(executable);
            // a folder is read with several ExifTool processes - they exist only while reading
            reader = new MetadataReader(exifTool, () -> new ExifTool(executable), READ_PROCESSES);
            writer = new MetadataWriter(exifTool, reader);
            previewProvider = new PreviewProvider(exifTool, PREVIEW_CACHE);
            previewPane.setPreviewProvider(previewProvider);
            imageInfoPane.setExifTool(exifTool);
            openFolderButton.setDisable(false);
            statusLabel.setText("ExifTool " + version + " - " + executable);
            updateButtons();
            return true;
        } catch (ExifToolException | InvalidPathException ex) {
            previewPane.setPreviewProvider(null);
            imageInfoPane.setExifTool(null);
            openFolderButton.setDisable(true);
            statusLabel.setText("ExifTool not usable - please check the preferences");
            updateButtons();
            return false;
        }
    }

    private void closeExifTool() {
        if (exifTool != null) {
            exifTool.close();
            exifTool = null;
            reader = null;
            writer = null;
        }
    }

    @FXML
    private void showPreferences() {
        if (PreferenceEditor.getInstance().editPreferences()) {
            applyExifTool();
        }
    }

    @FXML
    private void openFolder() {
        confirmUnsavedChanges(() -> {
            final DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("Open folder");
            final File recent = new File(GeoTaggerPreferences.RECENT_FOLDER.getAsString());
            if (recent.isDirectory()) {
                chooser.setInitialDirectory(recent);
            }
            final File selected = chooser.showDialog(stage);
            if (selected != null) {
                loadAndRemember(selected.toPath());
            }
        });
    }

    private void loadAndRemember(Path newFolder) {
        GeoTaggerPreferences.RECENT_FOLDER.put(newFolder.toString());
        GeoTaggerPreferences.INCLUDE_SUBFOLDERS.put(includeSubfoldersCheck.isSelected());
        loadFolder(newFolder, includeSubfoldersCheck.isSelected());
    }

    // the drop down of "Open folder..." lists the recently opened folders, most recent first
    private void updateRecentFolders() {
        openFolderButton.getItems().clear();
        for (String recent : GeoTaggerPreferences.getRecentFolders().getRecentFiles()) {
            final MenuItem item = new MenuItem(recent);
            // the text is a path - no mnemonics
            item.setMnemonicParsing(false);
            item.setOnAction(event -> confirmUnsavedChanges(() -> loadAndRemember(Path.of(recent))));
            openFolderButton.getItems().add(item);
        }
        if (openFolderButton.getItems().isEmpty()) {
            final MenuItem none = new MenuItem("No recent folders");
            none.setDisable(true);
            openFolderButton.getItems().add(none);
        }
    }

    // UI tests load folders this way instead of through the native folder dialog
    void loadFolder(Path newFolder, boolean includeSubfolders) {
        final MetadataReader folderReader = reader;
        // a newer loadFolder makes the results of this one obsolete - e.g. chunks still on their way
        final int generation = ++loadGeneration;
        // the old folder is saved or discarded by now (asked before) - the list fills with the new one while reading
        DoUndoManager.getInstance().clear(undoKey());
        folder = newFolder;
        DoUndoManager.getInstance().clear(undoKey());
        files.clear();
        refreshFiles();
        // found in the background, shown in the track list when loading succeeded
        final AtomicReference<List<Path>> gpxFiles = new AtomicReference<>(List.of());
        final Task<List<MediaFile>> task = new Task<>() {
            @Override
            protected List<MediaFile> call() throws IOException {
                updateMessage("Searching " + newFolder + " ...");
                final FolderScanner.ScanResult scan = FolderScanner.scan(newFolder, includeSubfolders);
                gpxFiles.set(scan.gpxFiles());
                final List<Path> mediaFiles = scan.mediaFiles();
                final int total = mediaFiles.size();
                updateProgress(0, total);
                final AtomicInteger done = new AtomicInteger();
                return folderReader.readChunked(mediaFiles, chunk -> {
                    updateProgress(done.addAndGet(chunk.size()), total);
                    updateMessage("Reading metadata: " + done.get() + " / " + total);
                    // shown right away - like GeoSetter
                    Platform.runLater(() -> {
                        if (generation == loadGeneration) {
                            files.addAll(chunk);
                            refreshFiles();
                        }
                    });
                });
            }
        };
        task.setOnSucceeded(event -> {
            if (generation != loadGeneration) {
                // another folder was opened meanwhile
                return;
            }
            finishTask();
            // the chunks are all shown already - set again in case one is still on its way
            files.setAll(task.getValue());
            refreshFiles();
            mapPane.fitToFiles(files);
            trackListPane.setTracks(gpxFiles.get());
            stage.setTitle(GeoTaggerManager.TITLE + " - " + newFolder);
            GeoTaggerPreferences.getRecentFolders().addRecentFile(newFolder.toString());
            updateRecentFolders();
            select(0);
        });
        task.setOnFailed(event -> {
            if (generation != loadGeneration) {
                return;
            }
            finishTask();
            // no half folder
            files.clear();
            refreshFiles();
            statusLabel.setText("Loading " + newFolder + " failed");
            ShowAlerts.getInstance().showAlert(Alert.AlertType.ERROR, GeoTaggerManager.TITLE, "Can't load " + newFolder,
                    String.valueOf(task.getException().getMessage()));
        });
        runTask(task);
    }

    @FXML
    private void save() {
        saveChanges(null);
    }

    /**
     * Writes all pending changes in the background.
     *
     * @param afterSuccess what to do once everything is saved (e.g. close the window), may be null - not called if a
     * file couldn't be saved
     */
    private void saveChanges(Runnable afterSuccess) {
        final List<MediaFile> changed = files.stream().filter(MediaFile::isChanged).toList();
        if (changed.isEmpty()) {
            if (afterSuccess != null) {
                afterSuccess.run();
            }
            return;
        }
        if (writer == null) {
            // "Save" was chosen - closing or switching the folder now would silently lose the changes
            ShowAlerts.getInstance().showAlert(Alert.AlertType.ERROR, GeoTaggerManager.TITLE, "Nothing saved",
                    "ExifTool isn't usable - please check the preferences. Your changes are kept.");
            return;
        }
        final MetadataWriter fileWriter = writer;
        final Task<SaveResult> task = new Task<>() {
            @Override
            protected SaveResult call() {
                final int total = changed.size();
                updateMessage("Saving " + total + " files ...");
                updateProgress(0, total);
                return fileWriter.save(changed, done -> {
                    updateProgress(done, total);
                    updateMessage("Saving: " + done + " / " + total);
                });
            }
        };
        task.setOnSucceeded(event -> {
            finishTask();
            final SaveResult result = task.getValue();
            // the saved values are the new originals - an undo of older steps would only confuse now
            DoUndoManager.getInstance().clear(undoKey());
            refreshFiles();
            imageInfoPane.reload();
            final String summary = SaveSummary.text(result);
            statusLabel.setText(summary.lines().findFirst().orElse(""));
            if (SaveSummary.hasProblems(result)) {
                ShowAlerts.getInstance().showAlert(Alert.AlertType.WARNING, GeoTaggerManager.TITLE,
                        "Not all files were saved", summary);
            } else if (afterSuccess != null) {
                afterSuccess.run();
            }
        });
        task.setOnFailed(event -> {
            finishTask();
            refreshFiles();
            statusLabel.setText("Saving failed");
            ShowAlerts.getInstance().showAlert(Alert.AlertType.ERROR, GeoTaggerManager.TITLE, "Saving failed",
                    String.valueOf(task.getException().getMessage()));
        });
        runTask(task);
    }

    /**
     * Asks what to do with unsaved changes before they would get lost - see spec, section "Fehlerbehandlung".
     *
     * @param proceed what to do afterwards (e.g. open another folder) - not called on "Cancel" or if saving failed
     */
    private void confirmUnsavedChanges(Runnable proceed) {
        final long changed = files.stream().filter(MediaFile::isChanged).count();
        if (changed == 0) {
            proceed.run();
            return;
        }
        final ButtonType saveChanges = new ButtonType("Save", ButtonBar.ButtonData.YES);
        final ButtonType discard = new ButtonType("Discard", ButtonBar.ButtonData.NO);
        final Optional<ButtonType> answer = ShowAlerts.getInstance().showAlert(Alert.AlertType.CONFIRMATION,
                GeoTaggerManager.TITLE, changed + " files have unsaved changes", "Save them before continuing?",
                saveChanges, discard, ButtonType.CANCEL);
        if (answer.isEmpty() || answer.get() == ButtonType.CANCEL) {
            return;
        }
        if (answer.get() == saveChanges) {
            saveChanges(proceed);
        } else {
            proceed.run();
        }
    }

    private void runTask(Task<?> task) {
        statusLabel.textProperty().bind(task.messageProperty());
        progressBar.progressProperty().bind(task.progressProperty());
        progressBar.setVisible(true);
        setBusy(true);
        worker.submit(task);
    }

    private void finishTask() {
        statusLabel.textProperty().unbind();
        progressBar.progressProperty().unbind();
        progressBar.setVisible(false);
        setBusy(false);
    }

    private void setBusy(boolean busy) {
        this.busy = busy;
        openFolderButton.setDisable(busy);
        includeSubfoldersCheck.setDisable(busy);
        preferencesButton.setDisable(busy);
        // the files are changed in the background while saving - no edits meanwhile
        mediaTable.setDisable(busy);
        updateButtons();
    }

    // for UI tests: loading or saving still running
    boolean isBusy() {
        return busy;
    }

    private void select(int index) {
        if (index >= 0 && index < mediaTable.getItems().size()) {
            mediaTable.getSelectionModel().clearAndSelect(index);
            mediaTable.scrollTo(index);
        }
    }

    private String displayName(MediaFile file) {
        // with sub folders the path below the opened folder tells the files apart
        return folder == null ? file.path().getFileName().toString() : folder.relativize(file.path()).toString();
    }

    private static String captureTime(MediaFile file) {
        final CaptureTime time = file.captureTime();
        if (time == null) {
            return "";
        }
        return CAPTURE_TIME.format(time.dateTime()) + (time.utc() ? " UTC" : "");
    }

}

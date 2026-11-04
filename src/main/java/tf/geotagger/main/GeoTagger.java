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
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import tf.exiftool.ExifTool;
import tf.exiftool.ExifToolException;
import tf.geotagger.files.FolderScanner;
import tf.geotagger.metadata.ExifToolLocator;
import tf.geotagger.metadata.MetadataReader;
import tf.geotagger.model.CaptureTime;
import tf.geotagger.model.Field;
import tf.geotagger.model.MediaFile;
import tf.geotagger.model.MediaFilter;
import tf.geotagger.preview.PreviewProvider;
import tf.helper.javafx.ShowAlerts;
import tf.helper.javafx.TableViewPreferences;

/**
 * Controller of the main window (GeoTagger.fxml): ExifTool start, folder loading, file list with filters.
 */
public class GeoTagger implements Initializable {
    private static final String TABLE_PREFERENCES = "mediaTable";
    private static final DateTimeFormatter CAPTURE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String YES = "\u2713";
    private static final String NO = "\u2013";
    // extracted previews of RAW / HEIC / videos - emptied when GeoTagger ends
    private static final Path PREVIEW_CACHE = Path.of(System.getProperty("java.io.tmpdir"), "GeoTagger", "preview");

    @FXML
    private Button openFolderButton;
    @FXML
    private CheckBox includeSubfoldersCheck;
    @FXML
    private Button preferencesButton;
    @FXML
    private SplitPane centralSplit;
    @FXML
    private SplitPane leftSplit;
    @FXML
    private HBox filterBox;
    @FXML
    private TableView<MediaFile> mediaTable;
    @FXML
    private TableColumn<MediaFile, String> nameColumn;
    @FXML
    private TableColumn<MediaFile, String> captureTimeColumn;
    @FXML
    private TableColumn<MediaFile, String> placeColumn;
    @FXML
    private TableColumn<MediaFile, String> coordinatesStatusColumn;
    @FXML
    private TableColumn<MediaFile, String> placeStatusColumn;
    @FXML
    private TableColumn<MediaFile, String> altitudeStatusColumn;
    @FXML
    private Label statusLabel;
    @FXML
    private ProgressBar progressBar;
    @FXML
    private PreviewPane previewPane;

    private final ObservableList<MediaFile> files = FXCollections.observableArrayList();
    private final FilteredList<MediaFile> filteredFiles = new FilteredList<>(files);
    private final ToggleGroup filterGroup = new ToggleGroup();
    private final Map<MediaFilter, ToggleButton> filterButtons = new EnumMap<>(MediaFilter.class);
    // one loader thread - ExifTool handles one command at a time anyway
    private final ExecutorService loader = Executors.newSingleThreadExecutor(runnable -> {
        final Thread thread = new Thread(runnable, "GeoTagger-loader");
        thread.setDaemon(true);
        return thread;
    });

    private Stage stage;
    // folder the files were loaded from - names are shown relative to it
    private Path folder;
    private ExifTool exifTool;
    private MetadataReader reader;
    private PreviewProvider previewProvider;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        initTable();
        initFilters();
        initPreview();
    }

    /**
     * Called once the stage is shown: restores the layout and starts ExifTool.
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
        TableViewPreferences.loadTableViewPreferences(mediaTable, TABLE_PREFERENCES, GeoTaggerPreferences.INSTANCE);
        initExifTool();
    }

    /**
     * Saves the layout and stops background work - called when GeoTagger ends.
     */
    public void stop() {
        GeoTaggerPreferences.RECENT_CENTRAL_DIVIDER_POS.put(centralSplit.getDividerPositions()[0]);
        GeoTaggerPreferences.RECENT_LEFT_DIVIDER_POS.put(leftSplit.getDividerPositions()[0]);
        GeoTaggerPreferences.INCLUDE_SUBFOLDERS.put(includeSubfoldersCheck.isSelected());
        TableViewPreferences.saveTableViewPreferences(mediaTable, TABLE_PREFERENCES, GeoTaggerPreferences.INSTANCE);
        loader.shutdownNow();
        previewPane.dispose();
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

        nameColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(displayName(cell.getValue())));
        captureTimeColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(captureTime(cell.getValue())));
        placeColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(place(cell.getValue())));
        coordinatesStatusColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(status(cell.getValue().hasCoordinates())));
        placeStatusColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(status(cell.getValue().hasPlace())));
        altitudeStatusColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(status(cell.getValue().hasAltitude())));
    }

    private void initFilters() {
        for (MediaFilter filter : MediaFilter.values()) {
            final ToggleButton button = new ToggleButton(filter.label());
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
        mediaTable.getSelectionModel().selectedItemProperty().addListener((observable, oldFile, newFile) -> showPreview(newFile));
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
    private boolean applyExifTool() {
        closeExifTool();
        final String configured = GeoTaggerPreferences.EXIFTOOL_PATH.getAsString();
        try {
            final Path executable = Path.of(configured);
            final String version = ExifToolLocator.version(executable);
            exifTool = new ExifTool(executable);
            reader = new MetadataReader(exifTool);
            previewProvider = new PreviewProvider(exifTool, PREVIEW_CACHE);
            previewPane.setPreviewProvider(previewProvider);
            openFolderButton.setDisable(false);
            statusLabel.setText("ExifTool " + version + " - " + executable);
            return true;
        } catch (ExifToolException | InvalidPathException ex) {
            previewPane.setPreviewProvider(null);
            openFolderButton.setDisable(true);
            statusLabel.setText("ExifTool not usable - please check the preferences");
            return false;
        }
    }

    private void closeExifTool() {
        if (exifTool != null) {
            exifTool.close();
            exifTool = null;
            reader = null;
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
        final DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Open folder");
        final File recent = new File(GeoTaggerPreferences.RECENT_FOLDER.getAsString());
        if (recent.isDirectory()) {
            chooser.setInitialDirectory(recent);
        }
        final File selected = chooser.showDialog(stage);
        if (selected != null) {
            GeoTaggerPreferences.RECENT_FOLDER.put(selected.getAbsolutePath());
            GeoTaggerPreferences.INCLUDE_SUBFOLDERS.put(includeSubfoldersCheck.isSelected());
            loadFolder(selected.toPath(), includeSubfoldersCheck.isSelected());
        }
    }

    private void loadFolder(Path newFolder, boolean includeSubfolders) {
        final MetadataReader folderReader = reader;
        final Task<List<MediaFile>> task = new Task<>() {
            @Override
            protected List<MediaFile> call() throws IOException {
                updateMessage("Searching " + newFolder + " ...");
                final List<Path> mediaFiles = FolderScanner.scan(newFolder, includeSubfolders).mediaFiles();
                final int total = mediaFiles.size();
                updateProgress(0, total);
                return folderReader.read(mediaFiles, done -> {
                    updateProgress(done, total);
                    updateMessage("Reading metadata: " + done + " / " + total);
                });
            }
        };
        task.setOnSucceeded(event -> {
            finishLoading();
            folder = newFolder;
            files.setAll(task.getValue());
            updateFilterCounts();
            stage.setTitle(GeoTaggerManager.TITLE + " - " + newFolder);
            statusLabel.setText(files.size() + " files in " + newFolder);
            select(0);
        });
        task.setOnFailed(event -> {
            finishLoading();
            statusLabel.setText("Loading " + newFolder + " failed");
            ShowAlerts.getInstance().showAlert(Alert.AlertType.ERROR, GeoTaggerManager.TITLE, "Can't load " + newFolder,
                    String.valueOf(task.getException().getMessage()));
        });

        statusLabel.textProperty().bind(task.messageProperty());
        progressBar.progressProperty().bind(task.progressProperty());
        progressBar.setVisible(true);
        setBusy(true);
        loader.submit(task);
    }

    private void finishLoading() {
        statusLabel.textProperty().unbind();
        progressBar.progressProperty().unbind();
        progressBar.setVisible(false);
        setBusy(false);
    }

    private void setBusy(boolean busy) {
        openFolderButton.setDisable(busy);
        includeSubfoldersCheck.setDisable(busy);
        preferencesButton.setDisable(busy);
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

    private static String place(MediaFile file) {
        final String city = file.text(Field.CITY);
        return city != null ? city : Objects.requireNonNullElse(file.text(Field.COUNTRY), "");
    }

    private static String status(boolean present) {
        return present ? YES : NO;
    }
}

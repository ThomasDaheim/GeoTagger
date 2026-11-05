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

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import tf.exiftool.ExifTool;

/**
 * All tags of the selected file as ExifTool reports them - read only, see spec, tab "Bild-Info (ExifTool)".
 */
public class ImageInfoPane extends BorderPane {
    private final TableView<Map.Entry<String, String>> table = new TableView<>();
    // tags are read with ExifTool - one file at a time
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        final Thread thread = new Thread(runnable, "GeoTagger-info");
        thread.setDaemon(true);
        return thread;
    });

    private ExifTool exifTool;
    // file requested last - answers for older requests are dropped
    private Path current;

    public ImageInfoPane() {
        final TableColumn<Map.Entry<String, String>, String> tagColumn = new TableColumn<>("Tag");
        tagColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getKey()));
        tagColumn.setPrefWidth(240);
        final TableColumn<Map.Entry<String, String>, String> valueColumn = new TableColumn<>("Value");
        valueColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getValue()));
        valueColumn.setPrefWidth(320);
        table.getColumns().addAll(List.of(tagColumn, valueColumn));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        setCenter(table);
        show(null);
    }

    /**
     * @param exifTool used to read the tags - null while ExifTool isn't usable
     */
    public void setExifTool(ExifTool exifTool) {
        this.exifTool = exifTool;
    }

    /**
     * Shows all tags of a file - read in the background. Asking again for the file already shown does nothing.
     *
     * @param file file to show, null for none
     */
    public void show(Path file) {
        if (file != null && file.equals(current)) {
            return;
        }
        current = file;
        table.getItems().clear();
        if (file == null) {
            table.setPlaceholder(new Label("No file selected"));
            return;
        }
        if (exifTool == null) {
            table.setPlaceholder(new Label("ExifTool not usable"));
            return;
        }
        table.setPlaceholder(new Label("Reading " + file.getFileName() + " ..."));
        final ExifTool requestExifTool = exifTool;
        executor.submit(() -> {
            Map<String, String> tags;
            try {
                tags = requestExifTool.readAllTags(file);
            } catch (RuntimeException ex) {
                // ExifTool problems - then there is just nothing to show
                tags = Map.of();
            }
            final List<Map.Entry<String, String>> rows = List.copyOf(tags.entrySet());
            Platform.runLater(() -> {
                if (file.equals(current)) {
                    table.getItems().setAll(rows);
                    table.setPlaceholder(new Label("No tags found"));
                }
            });
        });
    }

    /**
     * Reads the shown file again - e.g. after saving.
     */
    public void reload() {
        final Path file = current;
        current = null;
        show(file);
    }

    /**
     * Stops the background thread - called when GeoTagger ends.
     */
    public void dispose() {
        executor.shutdownNow();
    }
}

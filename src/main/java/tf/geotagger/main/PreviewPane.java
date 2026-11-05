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

import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import tf.geotagger.model.MediaFile;
import tf.geotagger.preview.Preview;
import tf.geotagger.preview.PreviewProvider;

/**
 * Large preview of the selected file with navigation buttons - like GeoSetter's image preview.
 * Fitted previews are scaled down to PREVIEW_SIZE and cached, "100 %" loads the image in full resolution.
 */
public class PreviewPane extends BorderPane {
    // scaled previews keep the memory bounded - a 50 MP image needs 200 MB in full resolution
    private static final double PREVIEW_SIZE = 2560;
    private static final int CACHE_SIZE = 8;

    private final Button firstButton = new Button("|<");
    private final Button previousButton = new Button("<");
    private final Button nextButton = new Button(">");
    private final Button lastButton = new Button(">|");
    private final ToggleButton actualSizeButton = new ToggleButton("100 %");
    private final ImageView imageView = new ImageView();
    private final Label placeholder = new Label();
    private final ScrollPane scrollPane = new ScrollPane();
    // least recently used previews - the neighbours of the selected file are put here in advance
    private final Map<Path, Image> cache = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Path, Image> eldest) {
            return size() > CACHE_SIZE;
        }
    };
    // previews are extracted with ExifTool - one at a time, in the order of the requests
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        final Thread thread = new Thread(runnable, "GeoTagger-preview");
        thread.setDaemon(true);
        return thread;
    });

    private PreviewProvider provider;
    // file requested last - answers for older requests are dropped
    private Path current;
    private Preview currentPreview;

    public PreviewPane() {
        final HBox toolbar = new HBox(4, firstButton, previousButton, nextButton, lastButton,
                new Separator(Orientation.VERTICAL), actualSizeButton);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(4));
        setTop(toolbar);

        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        // a Group takes the rotation of the image into account for its size
        final StackPane content = new StackPane(new Group(imageView), placeholder);
        content.minWidthProperty().bind(Bindings.createDoubleBinding(
                () -> scrollPane.getViewportBounds().getWidth(), scrollPane.viewportBoundsProperty()));
        content.minHeightProperty().bind(Bindings.createDoubleBinding(
                () -> scrollPane.getViewportBounds().getHeight(), scrollPane.viewportBoundsProperty()));
        scrollPane.setContent(content);
        setCenter(scrollPane);

        scrollPane.viewportBoundsProperty().addListener((observable, oldValue, newValue) -> updateFit());
        actualSizeButton.selectedProperty().addListener((observable, oldValue, newValue) -> {
            updateScrollBars();
            if (currentPreview != null) {
                display(currentPreview);
            }
        });
        updateScrollBars();
        clear();
    }

    // Scroll bars only for "100 %": a fitted image sized from the viewport could be one pixel too large after
    // rounding - the scroll bar that appears shrinks the viewport, the image shrinks, the scroll bar disappears, ...
    private void updateScrollBars() {
        final ScrollPane.ScrollBarPolicy policy = actualSizeButton.isSelected()
                ? ScrollPane.ScrollBarPolicy.AS_NEEDED
                : ScrollPane.ScrollBarPolicy.NEVER;
        scrollPane.setHbarPolicy(policy);
        scrollPane.setVbarPolicy(policy);
    }

    /**
     * @return button to show the first file of the list
     */
    public Button getFirstButton() {
        return firstButton;
    }

    /**
     * @return button to show the previous file of the list
     */
    public Button getPreviousButton() {
        return previousButton;
    }

    /**
     * @return button to show the next file of the list
     */
    public Button getNextButton() {
        return nextButton;
    }

    /**
     * @return button to show the last file of the list
     */
    public Button getLastButton() {
        return lastButton;
    }

    /**
     * @param provider source of the previews - null while ExifTool isn't usable
     */
    public void setPreviewProvider(PreviewProvider provider) {
        this.provider = provider;
    }

    /**
     * Shows the preview of a file - the extraction runs in the background, the neighbours are prepared afterwards.
     *
     * @param file file to show
     * @param neighbours files the user will probably look at next (previous / next in the list)
     */
    public void show(MediaFile file, List<MediaFile> neighbours) {
        final Path path = file.path();
        if (path.equals(current)) {
            // already shown or loading - e.g. when the selection is restored after a change
            return;
        }
        current = path;
        currentPreview = null;
        imageView.setImage(null);
        if (provider == null) {
            showMessage("No preview - ExifTool not usable");
            return;
        }
        showMessage("Loading " + path.getFileName() + " ...");
        final PreviewProvider requestProvider = provider;
        executor.submit(() -> {
            final Optional<Preview> preview = load(requestProvider, path);
            Platform.runLater(() -> {
                if (path.equals(current)) {
                    if (preview.isPresent()) {
                        display(preview.get());
                    } else {
                        showMessage("No preview for " + path.getFileName());
                    }
                }
            });
            for (MediaFile neighbour : neighbours) {
                load(requestProvider, neighbour.path()).ifPresent(this::scaled);
            }
        });
    }

    /**
     * Shows nothing - e.g. when no file is selected.
     */
    public void clear() {
        current = null;
        currentPreview = null;
        imageView.setImage(null);
        showMessage("No file selected");
    }

    /**
     * Stops the background thread - called when GeoTagger ends.
     */
    public void dispose() {
        executor.shutdownNow();
    }

    private static Optional<Preview> load(PreviewProvider provider, Path path) {
        try {
            return provider.preview(path);
        } catch (RuntimeException ex) {
            // ExifTool or file problems - then there is just no preview
            return Optional.empty();
        }
    }

    private void showMessage(String message) {
        placeholder.setText(message);
        placeholder.setVisible(true);
    }

    private void display(Preview preview) {
        currentPreview = preview;
        final Image image = actualSizeButton.isSelected()
                // full resolution only on request - not cached
                ? new Image(preview.image().toUri().toString(), true)
                : scaled(preview);
        imageView.setImage(image);
        imageView.setRotate(preview.rotation());
        placeholder.setVisible(false);
        if (image.getProgress() < 1.0) {
            image.errorProperty().addListener((observable, oldValue, error) -> {
                if (error && image == imageView.getImage()) {
                    showMessage("Can't show " + preview.image().getFileName());
                }
            });
            // the size of the image is known only once it is loaded - see updateFit()
            image.progressProperty().addListener((observable, oldValue, progress) -> {
                if (progress.doubleValue() >= 1.0 && image == imageView.getImage()) {
                    updateFit();
                }
            });
        } else if (image.isError()) {
            showMessage("Can't show " + preview.image().getFileName());
        }
        updateFit();
    }

    private synchronized Image scaled(Preview preview) {
        return cache.computeIfAbsent(preview.image(), path -> {
            // JavaFX scales up to the requested size as well - small previews keep their own size
            final ImageSize size = imageSize(path);
            final double width = size == null ? PREVIEW_SIZE : Math.min(PREVIEW_SIZE, size.width());
            final double height = size == null ? PREVIEW_SIZE : Math.min(PREVIEW_SIZE, size.height());
            return new Image(path.toUri().toString(), width, height, true, true, true);
        });
    }

    // size from the image header, without decoding the image - null if ImageIO can't tell
    private record ImageSize(int width, int height) {
    }

    private static ImageSize imageSize(Path path) {
        try (ImageInputStream input = ImageIO.createImageInputStream(path.toFile())) {
            if (input == null) {
                return null;
            }
            final Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                return null;
            }
            final ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                return new ImageSize(reader.getWidth(0), reader.getHeight(0));
            } finally {
                reader.dispose();
            }
        } catch (IOException ex) {
            return null;
        }
    }

    private void updateFit() {
        if (actualSizeButton.isSelected()) {
            imageView.setFitWidth(0);
            imageView.setFitHeight(0);
            return;
        }
        final Bounds viewport = scrollPane.getViewportBounds();
        // rotated by 90 / 270 degrees: width and height of the image swap
        final boolean quarterTurn = Math.abs(imageView.getRotate()) % 180 == 90;
        double fitWidth = quarterTurn ? viewport.getHeight() : viewport.getWidth();
        double fitHeight = quarterTurn ? viewport.getWidth() : viewport.getHeight();
        // only scaled down - small previews (e.g. the 160 x 120 thumbnail of a video) would just get blurred
        final Image image = imageView.getImage();
        if (image != null && image.getProgress() >= 1.0 && !image.isError()) {
            fitWidth = Math.min(fitWidth, image.getWidth());
            fitHeight = Math.min(fitHeight, image.getHeight());
        }
        imageView.setFitWidth(fitWidth);
        imageView.setFitHeight(fitHeight);
    }
}

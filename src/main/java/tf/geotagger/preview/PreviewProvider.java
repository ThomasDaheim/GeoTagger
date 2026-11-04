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
package tf.geotagger.preview;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import tf.exiftool.ExifTool;

/**
 * Finds the image to show for a photo or video - see spec, section "GeoTagger-Pakete", preview.
 * Formats JavaFX can't show (RAW, HEIC, TIFF, videos) are shown via their largest embedded JPEG, which is extracted
 * once into the cache folder.
 */
public class PreviewProvider {
    // largest first - RAW files usually have a full size JpgFromRaw or PreviewImage
    static final List<String> EMBEDDED_IMAGES = List.of("JpgFromRaw", "PreviewImage", "ThumbnailImage");
    // formats JavaFX can show directly
    private static final Set<String> JAVAFX_FORMATS = Set.of("jpg", "jpeg", "png", "gif", "bmp");
    private static final String ORIENTATION = "IFD0:Orientation";

    private final ExifTool exifTool;
    private final Path cacheDir;

    /**
     * @param exifTool used to read the orientation and to extract embedded images
     * @param cacheDir folder for extracted images - created when needed
     */
    public PreviewProvider(ExifTool exifTool, Path cacheDir) {
        this.exifTool = Objects.requireNonNull(exifTool);
        this.cacheDir = Objects.requireNonNull(cacheDir);
    }

    /**
     * @param file photo or video
     * @return the preview, empty if there is nothing to show (e.g. a video without thumbnail)
     */
    public Optional<Preview> preview(Path file) {
        final int rotation = rotation(file);
        if (JAVAFX_FORMATS.contains(extension(file))) {
            return Optional.of(new Preview(file, rotation));
        }
        try {
            Files.createDirectories(cacheDir);
            final Path cached = cacheDir.resolve(cacheName(file));
            if (!Files.isRegularFile(cached) && !extract(file, cached)) {
                return Optional.empty();
            }
            return Optional.of(new Preview(cached, rotation));
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    /**
     * Deletes the extracted images - called when GeoTagger ends.
     */
    public void clearCache() {
        if (!Files.isDirectory(cacheDir)) {
            return;
        }
        try (Stream<Path> files = Files.list(cacheDir)) {
            for (Path file : files.toList()) {
                Files.deleteIfExists(file);
            }
        } catch (IOException ex) {
            // best effort - it's a temp folder
        }
    }

    private boolean extract(Path file, Path target) {
        for (String tag : EMBEDDED_IMAGES) {
            if (exifTool.extractBinary(file, tag, target)) {
                return true;
            }
        }
        return false;
    }

    private int rotation(Path file) {
        final List<Map<String, Object>> entries = exifTool.readJson(List.of(file), List.of(ORIENTATION));
        final Object orientation = entries.isEmpty() ? null : entries.get(0).get(ORIENTATION);
        if (!(orientation instanceof Number number)) {
            return 0;
        }
        // 3 / 6 / 8 are rotations - the mirrored variants (2, 4, 5, 7) are practically never used by cameras
        return switch (number.intValue()) {
            case 3 -> 180;
            case 6 -> 90;
            case 8 -> 270;
            default -> 0;
        };
    }

    private static String extension(Path file) {
        final String name = file.getFileName().toString();
        final int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }

    // unique per file and modification time - a changed file gets a new extract
    private static String cacheName(Path file) throws IOException {
        final String key = file.toAbsolutePath().normalize() + "|" + Files.getLastModifiedTime(file).toMillis();
        try {
            final byte[] hash = MessageDigest.getInstance("SHA-1").digest(key.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash) + ".jpg";
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-1 is part of every JDK", ex);
        }
    }
}

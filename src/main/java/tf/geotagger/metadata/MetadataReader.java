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
package tf.geotagger.metadata;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import tf.exiftool.ExifTool;
import tf.geotagger.model.Field;
import tf.geotagger.model.MediaFile;
import tf.geotagger.model.Snapshot;

/**
 * Reads the metadata of many files with as few ExifTool calls as possible (chunks of CHUNK_SIZE files).
 * For SIDECAR files the values of an existing {@code <name>.xmp} take precedence over the values of the file itself.
 */
public class MetadataReader {
    // 50: the progress moves often enough not to look like hanging, the ExifTool calls stay few
    static final int CHUNK_SIZE = 50;
    // skips maker notes and trailers - none of the ReadTags is stored there; measured on 777 photos and videos of a
    // Canon EOS and a Pixel: 127 s without, 19.5 s with "-fast2", same values
    private static final List<String> READ_OPTIONS = List.of("-fast2");

    private final ExifTool exifTool;

    public MetadataReader(ExifTool exifTool) {
        this.exifTool = Objects.requireNonNull(exifTool);
    }

    public List<MediaFile> read(List<Path> files) {
        return read(files, count -> {});
    }

    /**
     * @param files files to read - files without a FormatProfile are ignored
     * @param progress gets the number of files read so far after each chunk
     * @return the read files in the order of files
     */
    public List<MediaFile> read(List<Path> files, IntConsumer progress) {
        final List<MediaFile> result = new ArrayList<>();
        return readChunked(files, chunk -> {
            result.addAll(chunk);
            progress.accept(result.size());
        });
    }

    /**
     * Reads chunk by chunk and hands each chunk over right away - e.g. to fill the file list while reading.
     *
     * @param files files to read - files without a FormatProfile are ignored
     * @param chunkRead gets the files of each chunk once it is read (called on the reading thread)
     * @return the read files in the order of files
     */
    public List<MediaFile> readChunked(List<Path> files, Consumer<List<MediaFile>> chunkRead) {
        final List<Path> supported = files.stream().filter(file -> FormatProfile.of(file).isPresent()).toList();
        final List<MediaFile> result = new ArrayList<>(supported.size());
        for (int start = 0; start < supported.size(); start += CHUNK_SIZE) {
            final List<MediaFile> chunk = readChunk(supported.subList(start, Math.min(start + CHUNK_SIZE, supported.size())));
            result.addAll(chunk);
            chunkRead.accept(List.copyOf(chunk));
        }
        return result;
    }

    private List<MediaFile> readChunk(List<Path> chunk) {
        final Map<Path, Map<String, Object>> entries = byPath(exifTool.readJson(chunk, ReadTag.keys(), READ_OPTIONS));
        final List<Path> sidecars = chunk.stream()
                .filter(file -> FormatProfile.of(file).orElseThrow() == FormatProfile.SIDECAR)
                .map(FormatProfile::sidecarFor)
                .filter(Files::isRegularFile)
                .toList();
        final Map<Path, Map<String, Object>> sidecarEntries = byPath(exifTool.readJson(sidecars, ReadTag.keys(), READ_OPTIONS));

        final List<MediaFile> files = new ArrayList<>(chunk.size());
        for (Path file : chunk) {
            final FormatProfile profile = FormatProfile.of(file).orElseThrow();
            final Map<String, Object> entry = entries.getOrDefault(key(file), Map.of());
            final Map<Field, Object> values = new EnumMap<>(Field.class);
            values.putAll(MetadataParser.values(entry));
            if (profile == FormatProfile.SIDECAR) {
                values.putAll(MetadataParser.values(sidecarEntries.getOrDefault(key(FormatProfile.sidecarFor(file)), Map.of())));
            }
            final Snapshot snapshot = new Snapshot(MetadataParser.captureTime(entry), lastModified(file), values);
            files.add(new MediaFile(file, profile.mediaType(), snapshot));
        }
        return files;
    }

    private static Map<Path, Map<String, Object>> byPath(List<Map<String, Object>> entries) {
        final Map<Path, Map<String, Object>> result = new HashMap<>();
        for (Map<String, Object> entry : entries) {
            final Object source = entry.get(ReadTag.SOURCE_FILE);
            if (source != null) {
                result.put(key(Path.of(source.toString())), entry);
            }
        }
        return result;
    }

    private static Path key(Path path) {
        return path.toAbsolutePath().normalize();
    }

    private static FileTime lastModified(Path file) {
        try {
            return Files.getLastModifiedTime(file);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}

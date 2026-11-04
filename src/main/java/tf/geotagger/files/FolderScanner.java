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
package tf.geotagger.files;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import tf.geotagger.metadata.FormatProfile;
import tf.geotagger.track.GpxReader;

/**
 * Finds the files GeoTagger works with in a folder: photos / videos (see FormatProfile) and GPX tracks.
 */
public final class FolderScanner {
    /**
     * @param mediaFiles photos and videos, sorted by path
     * @param gpxFiles GPX files, sorted by path
     */
    public record ScanResult(List<Path> mediaFiles, List<Path> gpxFiles) {
        public ScanResult {
            mediaFiles = List.copyOf(mediaFiles);
            gpxFiles = List.copyOf(gpxFiles);
        }
    }

    private FolderScanner() {
    }

    /**
     * @param folder folder to search
     * @param includeSubfolders also search all sub folders
     * @return media and GPX files - other files (e.g. *_original, .xmp) are ignored
     * @throws IOException if the folder or one of its sub folders can't be read
     */
    public static ScanResult scan(Path folder, boolean includeSubfolders) throws IOException {
        final List<Path> mediaFiles = new ArrayList<>();
        final List<Path> gpxFiles = new ArrayList<>();
        try (Stream<Path> files = includeSubfolders ? Files.walk(folder) : Files.list(folder)) {
            files.filter(Files::isRegularFile).sorted().forEach(file -> {
                if (FormatProfile.of(file).isPresent()) {
                    mediaFiles.add(file);
                } else if (GpxReader.isGpxFile(file)) {
                    gpxFiles.add(file);
                }
            });
        } catch (UncheckedIOException ex) {
            // Files.walk reports unreadable sub folders this way
            throw ex.getCause();
        }
        return new ScanResult(mediaFiles, gpxFiles);
    }
}

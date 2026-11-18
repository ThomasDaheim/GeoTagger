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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * Track points of GPX files for the sync dialog, kept per file for the session (final review of plan 6): a file is
 * read again when its size or modification time changed, a failure is never kept - e.g. a file still being copied or a
 * read interrupted by closing the dialog. A file chosen alone and as part of a folder is kept once.
 */
public final class GpxCache {
    /**
     * Reads the points of one GPX file.
     */
    @FunctionalInterface
    public interface Reader {
        /**
         * @param gpx the file
         * @return its points with time
         * @throws IOException if the file can't be read
         */
        List<TrackPoint> read(Path gpx) throws IOException;
    }

    private static final String NO_TIMES = "no points with time";

    private record Entry(long size, FileTime modified, List<TrackPoint> points) {
    }

    private final Reader reader;
    private final Map<Path, Entry> entries = new ConcurrentHashMap<>();

    /**
     * Reads with GpxReader.
     */
    public GpxCache() {
        this(GpxReader::read);
    }

    /**
     * @param reader reads one file - a fake in tests
     */
    public GpxCache(Reader reader) {
        this.reader = Objects.requireNonNull(reader);
    }

    /**
     * @param gpxFiles files to use
     * @param progress called with (files done, files in total) after each file
     * @return the points of all usable files and the reasons for the others - a file without points with time counts
     * as failure
     */
    public TrackTimeline.LoadResult load(List<Path> gpxFiles, BiConsumer<Integer, Integer> progress) {
        final List<TrackPoint> points = new ArrayList<>();
        final Map<Path, String> failures = new LinkedHashMap<>();
        int done = 0;
        for (Path gpx : gpxFiles) {
            try {
                final List<TrackPoint> read = points(gpx);
                if (read.isEmpty()) {
                    failures.put(gpx, NO_TIMES);
                }
                points.addAll(read);
            } catch (IOException ex) {
                failures.put(gpx, String.valueOf(ex.getMessage()));
            }
            progress.accept(++done, gpxFiles.size());
        }
        return new TrackTimeline.LoadResult(new TrackTimeline(points), failures);
    }

    // from the cache if the file didn't change - read failures and files without points aren't kept
    private List<TrackPoint> points(Path gpx) throws IOException {
        final Path key = gpx.toAbsolutePath().normalize();
        final BasicFileAttributes attributes = Files.readAttributes(key, BasicFileAttributes.class);
        final Entry cached = entries.get(key);
        if (cached != null && cached.size() == attributes.size() && cached.modified().equals(attributes.lastModifiedTime())) {
            return cached.points();
        }
        entries.remove(key);
        final List<TrackPoint> read = List.copyOf(reader.read(key));
        if (!read.isEmpty()) {
            entries.put(key, new Entry(attributes.size(), attributes.lastModifiedTime(), read));
        }
        return read;
    }
}

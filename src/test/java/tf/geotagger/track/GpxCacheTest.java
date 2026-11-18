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
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Final review of plan 6: the GPX files of the sync dialog are kept per file for the session - a changed file is
 * read again, a failure is never kept.
 */
class GpxCacheTest {
    private static final Instant T0 = Instant.parse("2026-09-02T09:00:00Z");

    @TempDir
    Path dir;

    private final List<Path> reads = new ArrayList<>();

    private GpxCache cache(GpxCache.Reader reader) {
        return new GpxCache(file -> {
            reads.add(file);
            return reader.read(file);
        });
    }

    private static List<TrackPoint> points(int count) {
        final List<TrackPoint> points = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            points.add(new TrackPoint(T0.plusSeconds(i), 46.0, 11.0, null));
        }
        return points;
    }

    @Test
    void unchangedFileIsReadOnce() throws IOException {
        final Path gpx = Files.writeString(dir.resolve("a.gpx"), "x");
        final GpxCache cache = cache(file -> points(2));

        assertEquals(2, cache.load(List.of(gpx), (done, total) -> { }).timeline().size());
        assertEquals(2, cache.load(List.of(gpx), (done, total) -> { }).timeline().size());

        assertEquals(List.of(gpx), reads);
    }

    @Test
    void changedFileIsReadAgain() throws IOException {
        // e.g. today's track copied into the archive while GeoTagger runs
        final Path gpx = Files.writeString(dir.resolve("a.gpx"), "x");
        final int[] count = {1};
        final GpxCache cache = cache(file -> points(count[0]));
        cache.load(List.of(gpx), (done, total) -> { });

        count[0] = 3;
        Files.writeString(gpx, "xyz");
        Files.setLastModifiedTime(gpx, FileTime.from(Instant.now().plusSeconds(10)));

        assertEquals(3, cache.load(List.of(gpx), (done, total) -> { }).timeline().size());
    }

    @Test
    void failuresAreNotKept() throws IOException {
        // a file still being copied, or a read interrupted by closing the dialog, must not stick for the session
        final Path gpx = Files.writeString(dir.resolve("a.gpx"), "x");
        final boolean[] broken = {true};
        final GpxCache cache = cache(file -> {
            if (broken[0]) {
                throw new IOException("unexpected end of file");
            }
            return points(2);
        });
        assertTrue(cache.load(List.of(gpx), (done, total) -> { }).failures().containsKey(gpx));

        broken[0] = false;

        final TrackTimeline.LoadResult second = cache.load(List.of(gpx), (done, total) -> { });
        assertEquals(2, second.timeline().size());
        assertTrue(second.failures().isEmpty());
    }

    @Test
    void fileWithoutTimesIsAFailure() throws IOException {
        final Path gpx = Files.writeString(dir.resolve("route.gpx"), "x");

        assertEquals("no points with time", cache(file -> List.of()).load(List.of(gpx), (done, total) -> { }).failures().get(gpx));
    }
}

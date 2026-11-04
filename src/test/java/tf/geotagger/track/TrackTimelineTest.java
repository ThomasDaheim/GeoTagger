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
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrackTimelineTest {
    private static final Instant T0 = Instant.parse("2026-09-02T09:00:00Z");

    @TempDir
    Path dir;

    private static TrackPoint point(long seconds) {
        return new TrackPoint(T0.plusSeconds(seconds), 46.0 + seconds / 1000.0, 11.0, null);
    }

    @Test
    void findsNeighboursAcrossUnsortedTracks() {
        final TrackTimeline timeline = new TrackTimeline(List.of(point(120), point(0), point(60)));

        assertEquals(3, timeline.size());
        assertEquals(point(0), timeline.atOrBefore(T0.plusSeconds(30)));
        assertEquals(point(60), timeline.atOrAfter(T0.plusSeconds(30)));
    }

    @Test
    void exactTimeIsItsOwnNeighbour() {
        final TrackTimeline timeline = new TrackTimeline(List.of(point(0), point(60), point(120)));

        assertEquals(point(60), timeline.atOrBefore(T0.plusSeconds(60)));
        assertEquals(point(60), timeline.atOrAfter(T0.plusSeconds(60)));
    }

    @Test
    void noNeighbourOutsideTheTrack() {
        final TrackTimeline timeline = new TrackTimeline(List.of(point(0), point(60)));

        assertNull(timeline.atOrBefore(T0.minusSeconds(1)));
        assertNull(timeline.atOrAfter(T0.plusSeconds(61)));
    }

    @Test
    void emptyTimelineHasNoNeighbours() {
        final TrackTimeline timeline = new TrackTimeline(List.of());

        assertTrue(timeline.isEmpty());
        assertNull(timeline.atOrBefore(T0));
        assertNull(timeline.atOrAfter(T0));
    }

    @Test
    void loadKeepsReadableFilesAndReportsBrokenOnes() throws IOException {
        final Path good = Files.writeString(dir.resolve("gut.gpx"), """
                <gpx version="1.1" xmlns="http://www.topografix.com/GPX/1/1"><trk><trkseg>
                  <trkpt lat="46.4" lon="11.6"><time>2026-09-02T09:08:00Z</time></trkpt>
                  <trkpt lat="46.5" lon="11.7"><time>2026-09-02T09:09:00Z</time></trkpt>
                </trkseg></trk></gpx>
                """);
        final Path broken = Files.writeString(dir.resolve("kaputt.gpx"), "<gpx><trk>");

        final TrackTimeline.LoadResult result = TrackTimeline.load(List.of(good, broken));

        assertEquals(2, result.timeline().size());
        assertEquals(List.of(broken), List.copyOf(result.failures().keySet()));
    }
}

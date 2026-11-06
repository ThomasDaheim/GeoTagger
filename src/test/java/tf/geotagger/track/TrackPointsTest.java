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

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import tf.geo.LatLon;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TrackPointsTest {
    private static GpxPoint point(double latitude, double longitude) {
        return new GpxPoint(new LatLon(latitude, longitude), null, null);
    }

    private static GpxTrack track(String name, List<GpxLine> lines) {
        final GpxTrack track = new GpxTrack(Path.of(name));
        track.loaded(new GpxContent(lines, List.of()));
        return track;
    }

    private static GpxTrack trackWithPoint(GpxPoint point) {
        return track("one.gpx", List.of(new GpxLine(List.of(point), null)));
    }

    @Test
    void distanceRunsPerTrackOverAllSegments() {
        final GpxTrack a = track("a.gpx", List.of(
                new GpxLine(List.of(point(46.0, 11.0), point(46.01, 11.0)), null),
                new GpxLine(List.of(point(46.02, 11.0)), null)));
        final GpxTrack b = track("b.gpx", List.of(new GpxLine(List.of(point(47.0, 12.0)), null)));

        final List<TrackPoints.Row> rows = TrackPoints.of(List.of(a, b));

        assertEquals(List.of(1, 2, 3, 1), rows.stream().map(TrackPoints.Row::number).toList());
        assertEquals(0.0, rows.get(0).distanceKm(), 1e-9);
        assertEquals(1.112, rows.get(1).distanceKm(), 0.001);
        assertEquals(2.224, rows.get(2).distanceKm(), 0.001);
        assertEquals(0.0, rows.get(3).distanceKm(), 1e-9);
    }

    @Test
    void pointsWithoutTimeOrElevationAreKept() {
        final List<TrackPoints.Row> rows = TrackPoints.of(List.of(trackWithPoint(new GpxPoint(new LatLon(46, 11), null, null))));
        assertNull(rows.get(0).time());
        assertNull(rows.get(0).elevation());
    }
}

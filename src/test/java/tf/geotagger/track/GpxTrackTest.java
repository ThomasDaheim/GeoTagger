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
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import tf.geo.LatLon;
import static org.junit.jupiter.api.Assertions.assertEquals;

class GpxTrackTest {
    @Test
    void emptyTrackFails() {
        final GpxTrack track = new GpxTrack(Path.of("C:/gpx/empty.gpx"));
        track.loaded(new GpxContent(List.of(), List.of()));
        assertEquals(GpxTrack.State.FAILED, track.state());
        assertEquals("No track points", track.failure());
    }

    @Test
    void loadedTrackKeepsItsPoints() {
        final GpxTrack track = new GpxTrack(Path.of("C:/gpx/a.gpx"));
        final TrackPoint point = new TrackPoint(Instant.parse("2026-11-04T10:00:00Z"), 46.5, 11.5, null);
        track.loading();
        final GpxLine line = new GpxLine(List.of(new LatLon(46.5, 11.5)), "#0000FF");
        track.loaded(new GpxContent(List.of(line), List.of(point)));
        assertEquals(GpxTrack.State.LOADED, track.state());
        assertEquals(List.of(point), track.points());
        assertEquals(List.of(line), track.lines());
    }
}
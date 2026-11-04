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
package tf.geotagger.sync;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import tf.geotagger.model.MediaFile;
import tf.geotagger.track.TrackPoint;
import tf.geotagger.track.TrackTimeline;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class TrackSyncTest {
    private static final double DELTA = 1e-9;

    // camera clock in UTC keeps capture time = track time in most tests
    private static final SyncParameters UTC = SyncParameters.defaults(ZoneOffset.UTC);

    private static final TrackTimeline TIMELINE = new TrackTimeline(List.of(
            point("09:00:00", 46.0, 11.0, 1000.0),
            point("09:10:00", 46.1, 11.2, 1100.0),
            point("12:00:00", 47.0, 12.0, null)));

    private static TrackPoint point(String time, double latitude, double longitude, Double elevation) {
        return new TrackPoint(Instant.parse("2026-09-02T" + time + "Z"), latitude, longitude, elevation);
    }

    private static MediaFile shotAt(String time) {
        return TestMedia.image(LocalDateTime.parse("2026-09-02T" + time));
    }

    private static SyncResult match(MediaFile file, SyncParameters parameters) {
        return TrackSync.match(List.of(file), TIMELINE, parameters).get(0);
    }

    private static SyncParameters withInterpolate(boolean interpolate) {
        return new SyncParameters(ZoneOffset.UTC, Duration.ZERO, SyncParameters.DEFAULT_MAX_GAP, interpolate, false, true, false);
    }

    private static SyncParameters onlyPast() {
        return new SyncParameters(ZoneOffset.UTC, Duration.ZERO, SyncParameters.DEFAULT_MAX_GAP, true, true, true, false);
    }

    @Test
    void interpolatesBetweenNeighbours() {
        final MediaFile file = shotAt("09:05:00");
        final SyncResult result = match(file, UTC);

        assertSame(file, result.file());
        assertEquals(SyncResult.Status.MATCHED, result.status());
        assertEquals(Instant.parse("2026-09-02T09:05:00Z"), result.correctedTime());
        assertEquals(46.05, result.latitude(), DELTA);
        assertEquals(11.1, result.longitude(), DELTA);
        assertEquals(1050.0, result.elevation(), DELTA);
        assertEquals(Duration.ofMinutes(5), result.gap());
        assertFalse(file.isChanged());
    }

    @Test
    void exactTrackPointIsUsed() {
        final SyncResult result = match(shotAt("09:10:00"), UTC);

        assertEquals(46.1, result.latitude(), DELTA);
        assertEquals(11.2, result.longitude(), DELTA);
        assertEquals(1100.0, result.elevation(), DELTA);
        assertEquals(Duration.ZERO, result.gap());
    }

    @Test
    void nearestPointWithoutInterpolation() {
        assertEquals(46.0, match(shotAt("09:04:00"), withInterpolate(false)).latitude(), DELTA);
        assertEquals(46.1, match(shotAt("09:07:00"), withInterpolate(false)).latitude(), DELTA);
    }

    @Test
    void onlyNeighbourWithinMaxGapIsTakenAndLargeGapIsFlagged() {
        final SyncResult result = match(shotAt("09:30:00"), UTC);

        assertEquals(SyncResult.Status.LARGE_GAP, result.status());
        assertEquals(46.1, result.latitude(), DELTA);
        assertEquals(1100.0, result.elevation(), DELTA);
        assertEquals(Duration.ofMinutes(20), result.gap());
    }

    @Test
    void noMatchBeyondMaxGap() {
        final SyncResult result = match(shotAt("10:30:00"), UTC);

        assertEquals(SyncResult.Status.NO_MATCH, result.status());
        assertNull(result.latitude());
        assertEquals(Duration.ofMinutes(80), result.gap());
    }

    @Test
    void pointAfterIsUsedBeforeTheTrackStarts() {
        final SyncResult result = match(shotAt("08:50:00"), UTC);

        assertEquals(SyncResult.Status.MATCHED, result.status());
        assertEquals(46.0, result.latitude(), DELTA);
        assertEquals(Duration.ofMinutes(10), result.gap());
    }

    @Test
    void onlyPastPointsIgnoresLaterPoints() {
        final SyncResult beforeStart = match(shotAt("08:50:00"), onlyPast());
        assertEquals(SyncResult.Status.NO_MATCH, beforeStart.status());
        assertNull(beforeStart.gap());

        final SyncResult inside = match(shotAt("09:05:00"), onlyPast());
        assertEquals(46.0, inside.latitude(), DELTA);
        assertEquals(Duration.ofMinutes(5), inside.gap());
    }

    @Test
    void cameraZoneAndOffsetAreApplied() {
        // Berlin summer time = UTC+2, camera one minute ahead: 11:06 camera time = 09:05 UTC
        final SyncParameters berlin = new SyncParameters(ZoneId.of("Europe/Berlin"), Duration.ofMinutes(1),
                SyncParameters.DEFAULT_MAX_GAP, true, false, true, false);

        final SyncResult result = match(shotAt("11:06:00"), berlin);

        assertEquals(Instant.parse("2026-09-02T09:05:00Z"), result.correctedTime());
        assertEquals(46.05, result.latitude(), DELTA);
    }

    @Test
    void fileWithoutCaptureTimeCantBeMatched() {
        final SyncResult result = match(TestMedia.image(null), UTC);

        assertEquals(SyncResult.Status.NO_CAPTURE_TIME, result.status());
        assertNull(result.correctedTime());
        assertNull(result.gap());
    }

    @Test
    void missingElevationOfOneNeighbourTakesTheOther() {
        final TrackTimeline timeline = new TrackTimeline(List.of(
                point("09:00:00", 46.0, 11.0, null),
                point("09:10:00", 46.1, 11.2, 1100.0)));

        final SyncResult result = TrackSync.match(List.of(shotAt("09:05:00")), timeline, UTC).get(0);

        assertEquals(1100.0, result.elevation(), DELTA);
    }
}

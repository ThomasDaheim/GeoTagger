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
import java.util.Map;
import org.junit.jupiter.api.Test;
import tf.geotagger.model.CaptureTime;
import tf.geotagger.model.Field;
import tf.geotagger.model.MediaFile;
import tf.geotagger.model.MediaType;
import tf.geotagger.track.TrackPoint;
import tf.geotagger.track.TrackTimeline;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrackSyncChangesTest {
    private static final double DELTA = 1e-9;
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    private static final TrackTimeline TIMELINE = new TrackTimeline(List.of(
            new TrackPoint(Instant.parse("2026-09-02T09:00:00Z"), 46.0, 11.0, 1000.0),
            new TrackPoint(Instant.parse("2026-09-02T09:10:00Z"), 46.1, 11.2, 1100.0)));

    // 11:05 Berlin summer time = 09:05 UTC
    private static final LocalDateTime INSIDE = LocalDateTime.parse("2026-09-02T11:05:00");
    private static final LocalDateTime OUTSIDE = LocalDateTime.parse("2026-09-02T18:00:00");

    private static SyncParameters parameters(boolean takeElevation, boolean writeTimeZone) {
        return new SyncParameters(BERLIN, Duration.ZERO, SyncParameters.DEFAULT_MAX_GAP, true, false, takeElevation, writeTimeZone);
    }

    @Test
    void eligibleSkipsFilesWithCoordinatesUnlessOverwriting() {
        final MediaFile without = TestMedia.image(INSIDE);
        final MediaFile with = TestMedia.file(MediaType.IMAGE, new CaptureTime(INSIDE, false),
                Map.of(Field.LATITUDE, 1.0, Field.LONGITUDE, 2.0));

        assertEquals(List.of(without), TrackSync.eligible(List.of(without, with), false));
        assertEquals(List.of(without, with), TrackSync.eligible(List.of(without, with), true));
    }

    private static Map<MediaFile, Map<Field, Object>> changes(SyncParameters parameters, MediaFile... files) {
        return TrackSync.changes(TrackSync.match(List.of(files), TIMELINE, parameters), parameters);
    }

    @Test
    void matchedFilesGetPositionAndElevation() {
        final MediaFile matched = TestMedia.image(INSIDE);
        final MediaFile unmatched = TestMedia.image(OUTSIDE);

        final Map<MediaFile, Map<Field, Object>> changes = changes(parameters(true, false), matched, unmatched);

        assertEquals(List.of(matched), List.copyOf(changes.keySet()));
        final Map<Field, Object> values = changes.get(matched);
        assertEquals(46.05, (Double) values.get(Field.LATITUDE), DELTA);
        assertEquals(11.1, (Double) values.get(Field.LONGITUDE), DELTA);
        assertEquals(1050.0, (Double) values.get(Field.ALTITUDE), DELTA);
        assertFalse(values.containsKey(Field.TIME_OFFSET));
    }

    @Test
    void filesAreNotChanged() {
        // the changes become one undo step - the files change only when it runs (plan 6)
        final MediaFile matched = TestMedia.image(INSIDE);

        changes(parameters(true, true), matched);

        assertFalse(matched.isChanged());
    }

    @Test
    void onlyResultsWithPositionChange() {
        final MediaFile unmatched = TestMedia.image(OUTSIDE);
        final MediaFile withoutTime = TestMedia.image(null);

        assertTrue(changes(parameters(true, true), unmatched, withoutTime).isEmpty());
    }

    @Test
    void elevationOnlyWhenRequested() {
        final MediaFile matched = TestMedia.image(INSIDE);

        final Map<Field, Object> values = changes(parameters(false, false), matched).get(matched);

        assertTrue(values.containsKey(Field.LATITUDE));
        assertFalse(values.containsKey(Field.ALTITUDE));
    }

    @Test
    void timeZoneIsWrittenForImagesOnly() {
        final MediaFile image = TestMedia.image(INSIDE);
        final MediaFile video = TestMedia.file(MediaType.VIDEO, new CaptureTime(INSIDE, false), Map.of());

        final Map<MediaFile, Map<Field, Object>> changes = changes(parameters(true, true), image, video);

        assertEquals(ZoneOffset.ofHours(2), changes.get(image).get(Field.TIME_OFFSET));
        assertTrue(changes.get(video).containsKey(Field.LATITUDE));
        assertNull(changes.get(video).get(Field.TIME_OFFSET));
    }
}

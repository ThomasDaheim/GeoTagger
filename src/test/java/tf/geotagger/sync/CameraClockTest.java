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
import org.junit.jupiter.api.Test;
import tf.geotagger.model.CaptureTime;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CameraClockTest {
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    private static CaptureTime local(String dateTime) {
        return new CaptureTime(LocalDateTime.parse(dateTime), false);
    }

    @Test
    void localTimeInSummer() {
        assertEquals(Instant.parse("2026-09-02T09:08:00Z"),
                CameraClock.toUtc(local("2026-09-02T11:08:00"), BERLIN, Duration.ZERO));
    }

    @Test
    void localTimeInWinter() {
        assertEquals(Instant.parse("2026-11-04T10:44:03Z"),
                CameraClock.toUtc(local("2026-11-04T11:44:03"), BERLIN, Duration.ZERO));
    }

    @Test
    void cameraAheadOrBehindIsCorrected() {
        assertEquals(Instant.parse("2026-09-02T09:08:00Z"),
                CameraClock.toUtc(local("2026-09-02T11:10:13"), BERLIN, Duration.ofSeconds(133)));
        assertEquals(Instant.parse("2026-09-02T09:08:00Z"),
                CameraClock.toUtc(local("2026-09-02T11:05:47"), BERLIN, Duration.ofSeconds(-133)));
    }

    @Test
    void fixedOffsetZone() {
        assertEquals(Instant.parse("2026-09-02T14:08:00Z"),
                CameraClock.toUtc(local("2026-09-02T09:08:00"), ZoneOffset.ofHours(-5), Duration.ZERO));
    }

    @Test
    void utcCaptureTimeIgnoresZoneAndOffset() {
        // user decision 18.11.2026: zone and offset are for the camera - a phone video (QuickTime UTC) has its own
        // correct time, like the GPX track recorded on the same phone
        final CaptureTime utc = new CaptureTime(LocalDateTime.parse("2026-09-02T09:08:00"), true);
        assertEquals(Instant.parse("2026-09-02T09:08:00Z"), CameraClock.toUtc(utc, null, BERLIN, Duration.ZERO));
        assertEquals(Instant.parse("2026-09-02T09:08:00Z"), CameraClock.toUtc(utc, null, BERLIN, Duration.ofDays(3)));
        assertFalse(CameraClock.isCameraTime(utc, null));
    }

    @Test
    void ownOffsetTimeOriginalIgnoresZoneAndOffset() {
        // a phone photo with OffsetTimeOriginal: its time is absolute
        final CaptureTime local = local("2026-09-02T11:08:00");
        assertEquals(Instant.parse("2026-09-02T09:08:00Z"),
                CameraClock.toUtc(local, ZoneOffset.ofHours(2), ZoneOffset.UTC, Duration.ofDays(3)));
        assertFalse(CameraClock.isCameraTime(local, ZoneOffset.ofHours(2)));
        assertTrue(CameraClock.isCameraTime(local, null));
    }

    @Test
    void cameraDisplayShowsWhatTheCameraShouldShowNow() {
        assertEquals(LocalDateTime.parse("2026-09-02T11:10:13"),
                CameraClock.cameraDisplay(Instant.parse("2026-09-02T09:08:00Z"), BERLIN, Duration.ofSeconds(133)));
    }
}

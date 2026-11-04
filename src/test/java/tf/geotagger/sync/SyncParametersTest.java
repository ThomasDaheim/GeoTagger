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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SyncParametersTest {
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    private static MediaFile withOffset(ZoneOffset offset) {
        return TestMedia.file(MediaType.IMAGE, new CaptureTime(LocalDateTime.parse("2026-09-02T11:08:00"), false),
                Map.of(Field.TIME_OFFSET, offset));
    }

    @Test
    void defaultsFollowTheSpec() {
        final SyncParameters parameters = SyncParameters.defaults(BERLIN);

        assertEquals(BERLIN, parameters.cameraZone());
        assertEquals(Duration.ZERO, parameters.cameraOffset());
        assertEquals(Duration.ofMinutes(30), parameters.maxGap());
        assertTrue(parameters.interpolate());
        assertFalse(parameters.onlyPastPoints());
        assertTrue(parameters.takeElevation());
        assertFalse(parameters.writeTimeZone());
    }

    @Test
    void maxGapMustBePositive() {
        assertThrows(IllegalArgumentException.class,
                () -> new SyncParameters(BERLIN, Duration.ZERO, Duration.ZERO, true, false, true, false));
    }

    @Test
    void suggestedZoneIsTheMostFrequentOffsetOrTheFallback() {
        final List<MediaFile> files = List.of(withOffset(ZoneOffset.ofHours(2)), TestMedia.image(LocalDateTime.parse("2026-09-02T11:08:00")),
                withOffset(ZoneOffset.ofHours(1)), withOffset(ZoneOffset.ofHours(2)));

        assertEquals(ZoneOffset.ofHours(2), SyncParameters.suggestedCameraZone(files, BERLIN));
        assertEquals(BERLIN, SyncParameters.suggestedCameraZone(
                List.of(TestMedia.image(LocalDateTime.parse("2026-09-02T11:08:00"))), BERLIN));
    }
}

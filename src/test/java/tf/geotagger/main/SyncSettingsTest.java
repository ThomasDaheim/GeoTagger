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
package tf.geotagger.main;

import java.time.Duration;
import java.time.ZoneId;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tf.geotagger.sync.SyncParameters;
import tf.geotagger.sync.TrackSource;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SyncSettingsTest {
    @BeforeEach
    void freshPreferences() {
        // started without the Gradle test tasks clear() would wipe the real settings
        Assumptions.assumeTrue("tf/geotagger/test".equals(System.getProperty("geotagger.preferences")),
                "needs -Dgeotagger.preferences=tf/geotagger/test - run it via Gradle");
        GeoTaggerPreferences.store().clear();
    }

    @Test
    void defaultsAreTheSpecDefaults() {
        assertEquals(new SyncSettings(TrackSource.LOADED_TRACKS, "", "", Duration.ZERO, Duration.ofMinutes(30),
                true, false, true, false, false), SyncSettings.load());
    }

    @Test
    void storedValuesComeBack() {
        final Duration offset = Duration.ofDays(1).plusHours(2).plusMinutes(3).plusSeconds(4).negated();
        final SyncSettings settings = new SyncSettings(TrackSource.GPX_FOLDER, "C:/gps/tour.gpx", "C:/gps", offset,
                Duration.ofMinutes(5), false, true, false, true, true);

        settings.store();

        assertEquals(settings, SyncSettings.load());
    }

    @Test
    void invalidMaxGapGivesTheDefault() {
        GeoTaggerPreferences.SYNC_MAX_GAP_MINUTES.put(0L);
        assertEquals(Duration.ofMinutes(30), SyncSettings.load().maxGap());
    }

    @Test
    void parametersUseTheGivenZone() {
        final ZoneId berlin = ZoneId.of("Europe/Berlin");
        assertEquals(new SyncParameters(berlin, Duration.ofSeconds(-5), Duration.ofMinutes(5), false, true, false, true),
                new SyncSettings(TrackSource.GPX_FILE, "", "", Duration.ofSeconds(-5), Duration.ofMinutes(5),
                        false, true, false, true, false).parameters(berlin));
    }
}

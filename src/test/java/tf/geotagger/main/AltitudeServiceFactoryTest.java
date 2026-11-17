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

import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tf.geo.service.GeoServiceException;
import tf.geo.service.SrtmFormat;
import tf.geo.service.SrtmInterpolation;
import tf.geotagger.enrich.AltitudeRun;
import tf.geotagger.enrich.AltitudeSource;
import tf.geotagger.model.Field;
import tf.geotagger.model.MediaFile;
import tf.geotagger.model.MediaType;
import tf.geotagger.model.Snapshot;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AltitudeServiceFactoryTest {
    @TempDir
    Path srtmFolder;

    @BeforeEach
    void freshPreferences() {
        // started without the Gradle test tasks clear() would wipe the real settings
        Assumptions.assumeTrue("tf/geotagger/test".equals(System.getProperty("geotagger.preferences")),
                "needs -Dgeotagger.preferences=tf/geotagger/test - run it via Gradle");
        GeoTaggerPreferences.store().clear();
    }

    @Test
    void srtmNeedsAFolder() {
        for (String folder : List.of("", " ", srtmFolder.resolve("missing").toString())) {
            final GeoServiceException ex = assertThrows(GeoServiceException.class,
                    () -> AltitudeServiceFactory.srtm(folder, SrtmInterpolation.BILINEAR), folder);
            assertEquals(GeoServiceException.Kind.CONFIGURATION, ex.kind());
            assertEquals("Please choose the folder of the SRTM files in the preferences", ex.getMessage());
        }
    }

    @Test
    void orsNeedsAKey() throws GeoServiceException {
        final GeoServiceException ex = assertThrows(GeoServiceException.class, () -> AltitudeServiceFactory.ors(" "));
        assertEquals(GeoServiceException.Kind.CONFIGURATION, ex.kind());
        assertTrue(ex.getMessage().contains("openrouteservice API key"), ex.getMessage());
        assertEquals("openrouteservice", AltitudeServiceFactory.ors("key").name());
    }

    @Test
    void enricherBuildsOnlyWhatTheSourceNeeds() throws GeoServiceException {
        // SRTM only: no key needed
        GeoTaggerPreferences.ALTITUDE_SOURCE.put(AltitudeSource.SRTM_ONLY);
        GeoTaggerPreferences.SRTM_FOLDER.put(srtmFolder.toString());
        AltitudeServiceFactory.enricher(duration -> { });

        // openrouteservice only: no folder needed, but a key
        GeoTaggerPreferences.ALTITUDE_SOURCE.put(AltitudeSource.ORS_ONLY);
        GeoTaggerPreferences.SRTM_FOLDER.put("");
        assertEquals(GeoServiceException.Kind.CONFIGURATION,
                assertThrows(GeoServiceException.class, () -> AltitudeServiceFactory.enricher(duration -> { })).kind());
        GeoTaggerPreferences.ORS_API_KEY.put("key");
        AltitudeServiceFactory.enricher(duration -> { });

        // SRTM first: needs the folder
        GeoTaggerPreferences.ALTITUDE_SOURCE.put(AltitudeSource.SRTM_FIRST);
        assertThrows(GeoServiceException.class, () -> AltitudeServiceFactory.enricher(duration -> { }));
    }

    @Test
    void srtmFirstWithoutKeyNamesTheKeyOnlyForTheGaps() throws GeoServiceException, InterruptedException {
        GeoTaggerPreferences.ALTITUDE_SOURCE.put(AltitudeSource.SRTM_FIRST);
        GeoTaggerPreferences.SRTM_FOLDER.put(srtmFolder.toString());
        final MediaFile file = new MediaFile(Path.of("C:/fotos/a.jpg"), MediaType.IMAGE, new Snapshot(null, FileTime.fromMillis(0),
                Map.of(Field.LATITUDE, 46.67, Field.LONGITUDE, 11.16)));

        // no tile in the folder: the gap would go to openrouteservice
        final AltitudeRun run = AltitudeServiceFactory.enricher(duration -> { }).run(List.of(file), (done, total) -> { });

        assertEquals(List.of(file), run.failed());
        assertTrue(run.problem().contains("openrouteservice API key"), run.problem());
    }

    @Test
    void choicesAreReadAsEnums() {
        assertEquals(AltitudeSource.SRTM_FIRST, GeoTaggerPreferences.ALTITUDE_SOURCE.getAsType());
        assertEquals(SrtmInterpolation.BILINEAR, GeoTaggerPreferences.SRTM_INTERPOLATION.getAsType());
        assertEquals(SrtmFormat.SRTM3,GeoTaggerPreferences.SRTM_DOWNLOAD_FORMAT.getAsType());
    }
}

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
package tf.geotagger.enrich;

import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;
import tf.geo.LatLon;
import tf.geo.service.GeoServiceException;
import tf.geotagger.model.Field;
import tf.geotagger.model.MediaFile;
import tf.geotagger.model.MediaType;
import tf.geotagger.model.Snapshot;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AltitudeEnricherTest {
    private static final Duration INTERVAL = Duration.ofMillis(1500);
    private static final LatLon A = new LatLon(46.67, 11.16);
    private static final LatLon B = new LatLon(46.5, 11.35);

    private final List<Duration> sleeps = new ArrayList<>();

    private static MediaFile file(String name, LatLon position) {
        final Map<Field, Object> values = new EnumMap<>(Field.class);
        if (position != null) {
            values.put(Field.LATITUDE, position.latitude());
            values.put(Field.LONGITUDE, position.longitude());
        }
        return new MediaFile(Path.of("C:/fotos/" + name), MediaType.IMAGE, new Snapshot(null, FileTime.fromMillis(0), values));
    }

    // SRTM knows A only
    private static FakeElevationProvider srtm() {
        return new FakeElevationProvider("SRTM", position -> position.equals(A) ? OptionalDouble.of(325) : OptionalDouble.empty());
    }

    private static FakeElevationProvider ors(GeoServiceException... failures) {
        return new FakeElevationProvider("ORS", position -> OptionalDouble.of(262), failures);
    }

    private AltitudeRun run(FakeElevationProvider srtm, FakeElevationProvider ors, AltitudeSource source, MediaFile... files)
            throws InterruptedException {
        return new AltitudeEnricher(srtm, ors, source, sleeps::add, INTERVAL).run(List.of(files), (done, total) -> { });
    }

    @Test
    void srtmFirstAsksOrsOnlyForTheGaps() throws InterruptedException {
        final FakeElevationProvider ors = ors();
        final MediaFile a = file("a.jpg", A);
        final MediaFile b = file("b.jpg", B);

        final AltitudeRun run = run(srtm(), ors, AltitudeSource.SRTM_FIRST, a, b);

        assertEquals(Map.of(a, 325.0, b, 262.0), run.altitudes());
        assertEquals(List.of(List.of(B)), ors.calls());
    }

    @Test
    void srtmOnlyNeverAsksOrs() throws InterruptedException {
        final MediaFile a = file("a.jpg", A);
        final MediaFile b = file("b.jpg", B);

        final AltitudeRun run = run(srtm(), null, AltitudeSource.SRTM_ONLY, a, b);

        assertEquals(Map.of(a, 325.0), run.altitudes());
        assertEquals(List.of(b), run.notFound());
    }

    @Test
    void orsOnlyNeverReadsSrtm() throws InterruptedException {
        final MediaFile a = file("a.jpg", A);

        final AltitudeRun run = run(null, ors(), AltitudeSource.ORS_ONLY, a);

        assertEquals(Map.of(a, 262.0), run.altitudes());
    }

    @Test
    void samePositionsAreAskedOnce() throws InterruptedException {
        final FakeElevationProvider ors = ors();
        final MediaFile b = file("b.jpg", B);
        final MediaFile c = file("c.jpg", B);

        final AltitudeRun run = run(srtm(), ors, AltitudeSource.SRTM_FIRST, b, c);

        assertEquals(List.of(List.of(B)), ors.calls());
        assertEquals(Map.of(b, 262.0, c, 262.0), run.altitudes());
    }

    @Test
    void orsQuotaKeepsTheSrtmAltitudes() throws InterruptedException {
        final MediaFile a = file("a.jpg", A);
        final MediaFile b = file("b.jpg", B);

        final AltitudeRun run = run(srtm(), ors(new GeoServiceException(GeoServiceException.Kind.QUOTA_EXCEEDED, "quota exceeded")),
                AltitudeSource.SRTM_FIRST, a, b);

        assertEquals(Map.of(a, 325.0), run.altitudes());
        assertEquals(List.of(b), run.failed());
        assertEquals("quota exceeded", run.problem());
    }

    @Test
    void retriesWithGrowingWaits() throws InterruptedException {
        final GeoServiceException busy = new GeoServiceException(GeoServiceException.Kind.RETRYABLE, "busy");
        final MediaFile b = file("b.jpg", B);

        final AltitudeRun run = run(srtm(), ors(busy, busy), AltitudeSource.SRTM_FIRST, b);

        assertEquals(List.of(Duration.ofSeconds(1), Duration.ofSeconds(2)), sleeps);
        assertEquals(Map.of(b, 262.0), run.altitudes());
    }

    @Test
    void blocksAreSplitAndPaced() throws InterruptedException {
        final FakeElevationProvider ors = ors();
        final MediaFile[] files = new MediaFile[1001];
        for (int i = 0; i < files.length; i++) {
            files[i] = file("f" + i + ".jpg", new LatLon(46.0 + i * 0.0001, 11.0));
        }

        final AltitudeRun run = run(null, ors, AltitudeSource.ORS_ONLY, files);

        assertEquals(List.of(1000, 1), ors.calls().stream().map(List::size).toList());
        assertEquals(List.of(INTERVAL), sleeps);
        assertEquals(1001, run.altitudes().size());
    }

    @Test
    void roundedToTenthOfAMetre() throws InterruptedException {
        final MediaFile a = file("a.jpg", A);
        final FakeElevationProvider srtm = new FakeElevationProvider("SRTM", position -> OptionalDouble.of(1234.5678));

        assertEquals(Map.of(a, 1234.6), run(srtm, null, AltitudeSource.SRTM_ONLY, a).altitudes());
    }

    @Test
    void filesWithoutCoordinatesAreSkipped() throws InterruptedException {
        final FakeElevationProvider ors = ors();

        final AltitudeRun run = run(srtm(), ors, AltitudeSource.SRTM_FIRST, file("x.jpg", null));

        assertTrue(run.altitudes().isEmpty() && run.notFound().isEmpty() && run.failed().isEmpty());
        assertTrue(ors.calls().isEmpty());
    }
}

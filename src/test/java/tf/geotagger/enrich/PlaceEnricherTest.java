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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tf.geo.service.GeoServiceException;
import tf.geo.service.Place;
import tf.geotagger.model.Field;
import tf.geotagger.model.MediaFile;
import tf.geotagger.model.MediaType;
import tf.geotagger.model.Snapshot;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaceEnricherTest {
    private static final Duration INTERVAL = Duration.ofMillis(100);
    private static final Place MERAN = new Place("IT", "Italien", "Trentino-Südtirol", "Meran", null);
    private static final Place BOZEN = new Place("IT", "Italien", "Trentino-Südtirol", "Bozen", null);

    private final List<Duration> sleeps = new ArrayList<>();
    private final Map<GridCell, Optional<Place>> cache = new HashMap<>();

    private static MediaFile file(String name, Double latitude, Double longitude) {
        final Map<Field, Object> values = new EnumMap<>(Field.class);
        values.put(Field.LATITUDE, latitude);
        values.put(Field.LONGITUDE, longitude);
        return new MediaFile(Path.of("C:/fotos/" + name), MediaType.IMAGE, new Snapshot(null, FileTime.fromMillis(0), values));
    }

    private PlaceEnricher enricher(FakeGeocoder geocoder) {
        return new PlaceEnricher(geocoder, cache, sleeps::add);
    }

    private static GeoServiceException error(GeoServiceException.Kind kind) {
        return new GeoServiceException(kind, "service says " + kind);
    }

    @Test
    void filesInTheSameCellNeedOneRequest() throws InterruptedException {
        final FakeGeocoder geocoder = new FakeGeocoder(INTERVAL, MERAN);
        final MediaFile a = file("a.jpg", 46.67001, 11.16001);
        final MediaFile b = file("b.jpg", 46.67004, 11.16003);

        final PlaceRun run = enricher(geocoder).run(List.of(a, b), (done, total) -> { });

        assertEquals(1, geocoder.requests().size());
        assertEquals(Map.of(a, MERAN, b, MERAN), run.places());
    }

    @Test
    void cachedCellsAreNotRequestedAgain() throws InterruptedException {
        final FakeGeocoder geocoder = new FakeGeocoder(INTERVAL, MERAN);
        final MediaFile a = file("a.jpg", 46.67, 11.16);
        enricher(geocoder).run(List.of(a), (done, total) -> { });

        final PlaceRun second = enricher(geocoder).run(List.of(a), (done, total) -> { });

        assertEquals(1, geocoder.requests().size());
        assertEquals(Map.of(a, MERAN), second.places());
    }

    @Test
    void waitsBetweenRequests() throws InterruptedException {
        final FakeGeocoder geocoder = new FakeGeocoder(INTERVAL, MERAN, BOZEN, MERAN);
        final List<String> progress = new ArrayList<>();

        enricher(geocoder).run(List.of(file("a.jpg", 46.67, 11.16), file("b.jpg", 46.5, 11.35), file("c.jpg", 46.6, 11.2)),
                (done, total) -> progress.add(done + "/" + total));

        assertEquals(List.of(INTERVAL, INTERVAL), sleeps);
        assertEquals(List.of("1/3", "2/3", "3/3"), progress);
    }

    @Test
    void retriesWithGrowingWaits() throws InterruptedException {
        final FakeGeocoder geocoder = new FakeGeocoder(INTERVAL, error(GeoServiceException.Kind.RETRYABLE),
                error(GeoServiceException.Kind.RETRYABLE), MERAN);
        final MediaFile a = file("a.jpg", 46.67, 11.16);

        final PlaceRun run = enricher(geocoder).run(List.of(a), (done, total) -> { });

        assertEquals(List.of(Duration.ofSeconds(1), Duration.ofSeconds(2)), sleeps);
        assertEquals(Map.of(a, MERAN), run.places());
        assertNull(run.problem());
    }

    @Test
    void givesUpAfterThreeRetries() throws InterruptedException {
        final FakeGeocoder geocoder = new FakeGeocoder(INTERVAL, error(GeoServiceException.Kind.RETRYABLE));
        final MediaFile a = file("a.jpg", 46.67, 11.16);

        final PlaceRun run = enricher(geocoder).run(List.of(a), (done, total) -> { });

        assertEquals(4, geocoder.requests().size());
        assertEquals(List.of(Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(4)), sleeps);
        assertEquals(List.of(a), run.failed());
        assertEquals("service says RETRYABLE", run.problem());
        // failed cells aren't cached - a later run tries again
        assertTrue(cache.isEmpty());
    }

    @Test
    void quotaStopsTheRun() throws InterruptedException {
        final FakeGeocoder geocoder = new FakeGeocoder(INTERVAL, MERAN, error(GeoServiceException.Kind.QUOTA_EXCEEDED), BOZEN);
        final MediaFile a = file("a.jpg", 46.67, 11.16);
        final MediaFile b = file("b.jpg", 46.5, 11.35);
        final MediaFile c = file("c.jpg", 46.6, 11.2);

        final PlaceRun run = enricher(geocoder).run(List.of(a, b, c), (done, total) -> { });

        assertEquals(2, geocoder.requests().size());
        assertEquals(Map.of(a, MERAN), run.places());
        assertEquals(List.of(b, c), run.failed());
        assertEquals("service says QUOTA_EXCEEDED", run.problem());
    }

    @Test
    void notFoundIsReportedAndCached() throws InterruptedException {
        final FakeGeocoder geocoder = new FakeGeocoder(INTERVAL, Optional.empty());
        final MediaFile a = file("a.jpg", 0.5, -30.5);

        final PlaceRun run = enricher(geocoder).run(List.of(a), (done, total) -> { });
        final PlaceRun second = enricher(geocoder).run(List.of(a), (done, total) -> { });

        assertEquals(List.of(a), run.notFound());
        assertEquals(List.of(a), second.notFound());
        assertEquals(1, geocoder.requests().size());
    }

    @Test
    void filesWithoutCoordinatesAreSkipped() throws InterruptedException {
        final FakeGeocoder geocoder = new FakeGeocoder(INTERVAL, MERAN);
        final MediaFile a = file("a.jpg", null, null);

        final PlaceRun run = enricher(geocoder).run(List.of(a), (done, total) -> { });

        assertTrue(run.places().isEmpty() && run.notFound().isEmpty() && run.failed().isEmpty());
        assertTrue(geocoder.requests().isEmpty());
    }
}

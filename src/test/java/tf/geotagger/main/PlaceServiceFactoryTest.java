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

import java.util.Set;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tf.geo.service.GeoServiceException;
import tf.geotagger.enrich.PlaceService;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaceServiceFactoryTest {
    @BeforeEach
    void freshPreferences() {
        // started without the Gradle test tasks clear() would wipe the real settings
        Assumptions.assumeTrue("tf/geotagger/test".equals(System.getProperty("geotagger.preferences")),
                "needs -Dgeotagger.preferences=tf/geotagger/test - run it via Gradle");
        GeoTaggerPreferences.store().clear();
    }

    @Test
    void storedServiceNameIsReadAsEnum() {
        // as stored before plan 5a - plain text
        GeoTaggerPreferences.store().put("placeService", "MAPBOX");
        assertEquals(PlaceService.MAPBOX, PlaceServiceFactory.service());
        assertEquals(PlaceService.MAPBOX, GeoTaggerPreferences.PLACE_SERVICE.getAsType());
    }

    private static void choose(PlaceService service, String geoNamesUser, String mapboxToken, String language) {
        GeoTaggerPreferences.PLACE_SERVICE.put(service);
        GeoTaggerPreferences.GEONAMES_USERNAME.put(geoNamesUser);
        GeoTaggerPreferences.MAPBOX_ACCESS_TOKEN.put(mapboxToken);
        GeoTaggerPreferences.PLACE_LANGUAGE.put(language);
    }

    @Test
    void geoNamesNeedsAUser() {
        choose(PlaceService.GEONAMES, " ", "tok", "de");
        final GeoServiceException ex = assertThrows(GeoServiceException.class, PlaceServiceFactory::create);
        assertEquals(GeoServiceException.Kind.CONFIGURATION, ex.kind());
        assertTrue(ex.getMessage().contains("GeoNames user name"), ex.getMessage());
    }

    @Test
    void mapboxNeedsAToken() {
        choose(PlaceService.MAPBOX, "demo", "", "de");
        assertEquals(GeoServiceException.Kind.CONFIGURATION, assertThrows(GeoServiceException.class, PlaceServiceFactory::create).kind());
    }

    @Test
    void createsTheChosenService() throws GeoServiceException {
        choose(PlaceService.GEONAMES, "demo", "", "de");
        assertEquals("GeoNames", PlaceServiceFactory.create().name());
        choose(PlaceService.MAPBOX, "", "tok", "de");
        assertEquals("Mapbox", PlaceServiceFactory.create().name());
        // an unknown value falls back to GeoNames
        GeoTaggerPreferences.PLACE_SERVICE.put("SOMETHING");
        GeoTaggerPreferences.GEONAMES_USERNAME.put("demo");
        assertEquals("GeoNames", PlaceServiceFactory.create().name());
    }

    @Test
    void createsFromGivenValues() throws GeoServiceException {
        // the "Check" of the preferences uses the values typed in, not yet saved
        assertEquals("Mapbox", PlaceServiceFactory.create(PlaceService.MAPBOX, "", "tok", "en").name());
        assertEquals(GeoServiceException.Kind.CONFIGURATION, assertThrows(GeoServiceException.class,
                () -> PlaceServiceFactory.create(PlaceService.GEONAMES, " ", "tok", "en")).kind());
    }

    @Test
    void cacheKeyDependsOnServiceAndLanguage() {
        choose(PlaceService.GEONAMES, "demo", "tok", "de");
        final String geoNamesGerman = PlaceServiceFactory.cacheKey();
        choose(PlaceService.GEONAMES, "demo", "tok", "en");
        final String geoNamesEnglish = PlaceServiceFactory.cacheKey();
        choose(PlaceService.MAPBOX, "demo", "tok", "de");
        final String mapboxGerman = PlaceServiceFactory.cacheKey();
        assertEquals(3, Set.of(geoNamesGerman, geoNamesEnglish, mapboxGerman).size());
    }
}

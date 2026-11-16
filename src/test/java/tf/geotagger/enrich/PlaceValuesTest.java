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
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tf.geo.service.Place;
import tf.geotagger.model.Field;
import tf.geotagger.model.MediaFile;
import tf.geotagger.model.MediaType;
import tf.geotagger.model.Snapshot;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaceValuesTest {
    private static final Place KINGMAN = new Place("US", "USA", "Arizona", "Kingman", null);

    private static MediaFile file(String city, String sublocation) {
        final Map<Field, Object> values = new EnumMap<>(Field.class);
        values.put(Field.CITY, city);
        values.put(Field.SUBLOCATION, sublocation);
        return new MediaFile(Path.of("C:/fotos/a.jpg"), MediaType.IMAGE, new Snapshot(null, FileTime.fromMillis(0), values));
    }

    @Test
    void countryNameFromTheCodeInTheLanguage() {
        // GeoNames says "USA" with lang=de, Mapbox "Vereinigte Staaten" - user feedback 16.11.2026: like GeoSetter
        final Map<Field, Object> values = PlaceValues.of(KINGMAN, "de", file(null, null));
        assertEquals("US", values.get(Field.COUNTRY_CODE));
        assertEquals("Vereinigte Staaten", values.get(Field.COUNTRY));
        assertEquals("Arizona", values.get(Field.STATE));
        assertEquals("Kingman", values.get(Field.CITY));
    }

    @Test
    void serviceNameWithoutLanguageOrForUnknownCodes() {
        assertEquals("USA", PlaceValues.of(KINGMAN, "", file(null, null)).get(Field.COUNTRY));
        assertEquals("Nowhere", PlaceValues.of(new Place("QQ", "Nowhere", null, null, null), "de", file(null, null)).get(Field.COUNTRY));
    }

    @Test
    void sublocationStaysForTheSameCity() {
        // GeoSetter wrote Kingman / Kingman - the service knows no sublocation there
        final Map<Field, Object> values = PlaceValues.of(KINGMAN, "de", file("Kingman", "Kingman"));
        assertFalse(values.containsKey(Field.SUBLOCATION));
    }

    @Test
    void sublocationIsClearedForAnotherCity() {
        final Map<Field, Object> values = PlaceValues.of(KINGMAN, "de", file("Bozen", "Altstadt"));
        assertTrue(values.containsKey(Field.SUBLOCATION));
        assertNull(values.get(Field.SUBLOCATION));
    }

    @Test
    void sublocationOfTheServiceWins() {
        final Place barcelona = new Place("ES", "Spanien", "Katalonien", "Barcelona", "Eixample");
        assertEquals("Eixample", PlaceValues.of(barcelona, "de", file("Barcelona", "Gràcia")).get(Field.SUBLOCATION));
    }
}

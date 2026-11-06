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
package tf.geotagger.edit;

import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import tf.geotagger.model.Field;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FieldTextTest {
    @Test
    void formatsValuesForTheDetails() {
        assertEquals("46.4071", FieldText.format(Field.LATITUDE, 46.4071));
        assertEquals("1200", FieldText.format(Field.ALTITUDE, 1200.0));
        assertEquals("+02:00", FieldText.format(Field.TIME_OFFSET, ZoneOffset.ofHours(2)));
        assertEquals("+00:00", FieldText.format(Field.TIME_OFFSET, ZoneOffset.UTC));
        assertEquals("Völs", FieldText.format(Field.CITY, "Völs"));
        assertEquals("", FieldText.format(Field.CITY, null));
    }

    @Test
    void parsesValidInput() {
        assertEquals(46.5, FieldText.parse(Field.LATITUDE, " 46,5 "));
        assertEquals(-180.0, FieldText.parse(Field.LONGITUDE, "-180"));
        assertEquals(-12.5, FieldText.parse(Field.ALTITUDE, "-12.5"));
        assertEquals("IT", FieldText.parse(Field.COUNTRY_CODE, "it"));
        assertEquals(ZoneOffset.ofHours(2), FieldText.parse(Field.TIME_OFFSET, "+02:00"));
        assertEquals(ZoneOffset.ofHours(-5), FieldText.parse(Field.TIME_OFFSET, "-5"));
        assertEquals(ZoneOffset.UTC, FieldText.parse(Field.TIME_OFFSET, "Z"));
        assertEquals("Seiser Alm", FieldText.parse(Field.SUBLOCATION, " Seiser Alm "));
    }

    @Test
    void blankInputMeansNoValue() {
        assertNull(FieldText.parse(Field.LATITUDE, "  "));
        assertNull(FieldText.parse(Field.CITY, ""));
        assertNull(FieldText.parse(Field.TIME_OFFSET, null));
    }

    @Test
    void rejectsInvalidInputWithMessage() {
        assertEquals("Latitude must be between -90 and 90",
                assertThrows(IllegalArgumentException.class, () -> FieldText.parse(Field.LATITUDE, "91")).getMessage());
        assertEquals("Longitude must be decimal degrees like 11.5 or degrees like 11° 30' 0\" E",
                assertThrows(IllegalArgumentException.class, () -> FieldText.parse(Field.LONGITUDE, "elf")).getMessage());
        assertThrows(IllegalArgumentException.class, () -> FieldText.parse(Field.ALTITUDE, "NaN"));
        assertThrows(IllegalArgumentException.class, () -> FieldText.parse(Field.TIME_OFFSET, "Europe/Rome"));
    }

    @Test
    void formatsCoordinatesAsDegreesMinutesSeconds() {
        assertEquals("N46°40'48.05\"; E11°5'28.61\"", FieldText.formatCoordinates(46.680014, 11.091281));
        assertEquals("S33°52'30.00\"; W151°12'0.00\"", FieldText.formatCoordinates(-33.875, -151.2));
        // rounded seconds of 60.00 carry over to the minutes
        assertEquals("N47°0'0.00\"; E11°0'0.00\"", FieldText.formatCoordinates(46.9999999, 11.0));
        assertEquals("", FieldText.formatCoordinates(46.5, null));
    }

    @Test
    void formatsAltitudeForTheListWithTwoDecimals() {
        assertEquals("233.93 m", FieldText.formatAltitude(233.93394));
        assertEquals("236.9 m", FieldText.formatAltitude(236.9));
        assertEquals("1000 m", FieldText.formatAltitude(1000.0));
        assertEquals("-0.01 m", FieldText.formatAltitude(-0.005));
        assertEquals("", FieldText.formatAltitude(null));
    }

    @Test
    void parsesDegreesWithHemisphere() {
        assertEquals(48.1372, (Double) FieldText.parse(Field.LATITUDE, "48.1372° N"), 1e-9);
        assertEquals(48 + 8 / 60.0 + 14 / 3600.0, (Double) FieldText.parse(Field.LATITUDE, "48° 08′ 14″ N"), 1e-9);
        assertEquals(48 + 8 / 60.0 + 14 / 3600.0, (Double) FieldText.parse(Field.LATITUDE, "N 48°8'14\""), 1e-9);
        assertEquals(-(33 + 52.5 / 60.0), (Double) FieldText.parse(Field.LATITUDE, "33° 52,5' s"), 1e-9);
        assertEquals(-(11 + 20 / 60.0 + 30.5 / 3600.0), (Double) FieldText.parse(Field.LONGITUDE, "11°20'30.5\"W"), 1e-9);
        assertEquals(11.5, (Double) FieldText.parse(Field.LONGITUDE, "E 11.5"), 1e-9);
        // German "Ost"
        assertEquals(11 + 34 / 60.0 + 32 / 3600.0, (Double) FieldText.parse(Field.LONGITUDE, "11° 34′ 32″ O"), 1e-9);
        assertEquals(11.5, (Double) FieldText.parse(Field.LONGITUDE, "o 11.5"), 1e-9);
        assertEquals(48.5, (Double) FieldText.parse(Field.LATITUDE, "48,5°"), 1e-9);
        assertEquals(-48.5, (Double) FieldText.parse(Field.LATITUDE, "-48.5°"), 1e-9);
    }

    @Test
    void rejectsInvalidDegrees() {
        // minutes / seconds beyond 59, both signs, wrong hemisphere for the field, decimals before minutes
        for (String text : new String[] {"48° 60' N", "48° 8' 60\" N", "-48° N", "N 48 S", "48° E", "48.5° 8' N", "91° N"}) {
            assertThrows(IllegalArgumentException.class, () -> FieldText.parse(Field.LATITUDE, text), text);
        }
        assertThrows(IllegalArgumentException.class, () -> FieldText.parse(Field.LONGITUDE, "11° N"));
        assertThrows(IllegalArgumentException.class, () -> FieldText.parse(Field.LONGITUDE, "181° E"));
    }
}

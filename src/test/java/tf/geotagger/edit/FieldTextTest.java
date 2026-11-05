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
        assertEquals("Longitude must be a number",
                assertThrows(IllegalArgumentException.class, () -> FieldText.parse(Field.LONGITUDE, "elf")).getMessage());
        assertThrows(IllegalArgumentException.class, () -> FieldText.parse(Field.ALTITUDE, "NaN"));
        assertThrows(IllegalArgumentException.class, () -> FieldText.parse(Field.TIME_OFFSET, "Europe/Rome"));
    }
}

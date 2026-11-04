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
package tf.geotagger.metadata;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tf.geotagger.model.CaptureTime;
import tf.geotagger.model.Field;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetadataParserTest {
    private static Map<String, Object> entry(Object... keysAndValues) {
        final Map<String, Object> entry = new HashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            entry.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return entry;
    }

    @Test
    void compositeGpsHasPriorityOverXmp() {
        final Map<Field, Object> values = MetadataParser.values(entry(
                "Composite:GPSLatitude", 46.4071, "Composite:GPSLongitude", -11.5932, "Composite:GPSAltitude", -3.5,
                "XMP-exif:GPSLatitude", 1.0, "XMP-exif:GPSLongitude", 2.0));
        assertEquals(46.4071, values.get(Field.LATITUDE));
        assertEquals(-11.5932, values.get(Field.LONGITUDE));
        assertEquals(-3.5, values.get(Field.ALTITUDE));
    }

    @Test
    void xmpGpsIsUsedWithoutCompositeAndAltitudeRefIsApplied() {
        final Map<Field, Object> values = MetadataParser.values(entry(
                "XMP-exif:GPSLatitude", -33.5, "XMP-exif:GPSLongitude", "151.25",
                "XMP-exif:GPSAltitude", 12.5, "XMP-exif:GPSAltitudeRef", 1));
        assertEquals(-33.5, values.get(Field.LATITUDE));
        assertEquals(151.25, values.get(Field.LONGITUDE));
        assertEquals(-12.5, values.get(Field.ALTITUDE));
    }

    @Test
    void xmpPlaceHasPriorityOverIptc() {
        final Map<Field, Object> values = MetadataParser.values(entry(
                "XMP-photoshop:City", "Völs", "IPTC:City", "Voels",
                "IPTC:Country-PrimaryLocationCode", "IT", "IPTC:Country-PrimaryLocationName", "Italien",
                "IPTC:Province-State", "Südtirol", "IPTC:Sub-location", " Seiser Alm "));
        assertEquals("Völs", values.get(Field.CITY));
        assertEquals("IT", values.get(Field.COUNTRY_CODE));
        assertEquals("Italien", values.get(Field.COUNTRY));
        assertEquals("Südtirol", values.get(Field.STATE));
        assertEquals("Seiser Alm", values.get(Field.SUBLOCATION));
    }

    @Test
    void numericLookingTextIsKeptAsText() {
        assertEquals("1234", MetadataParser.values(entry("XMP-photoshop:City", 1234)).get(Field.CITY));
    }

    @Test
    void offsetIsParsedAndInvalidValuesAreIgnored() {
        assertEquals(ZoneOffset.ofHours(2), MetadataParser.values(entry("ExifIFD:OffsetTimeOriginal", "+02:00")).get(Field.TIME_OFFSET));
        final Map<Field, Object> invalid = MetadataParser.values(entry(
                "ExifIFD:OffsetTimeOriginal", "   ", "Composite:GPSLatitude", "n/a", "XMP-photoshop:City", ""));
        assertTrue(invalid.isEmpty(), invalid.toString());
    }

    @Test
    void captureTimeFromExifThenXmpThenQuickTime() {
        assertEquals(new CaptureTime(LocalDateTime.of(2026, 9, 2, 11, 8, 0), false),
                MetadataParser.captureTime(entry("ExifIFD:DateTimeOriginal", "2026:09:02 11:08:00",
                        "QuickTime:CreateDate", "2026:09:02 09:08:00")));
        assertEquals(new CaptureTime(LocalDateTime.of(2026, 9, 2, 11, 8, 0), false),
                MetadataParser.captureTime(entry("XMP-exif:DateTimeOriginal", "2026:09:02 11:08:00.123+02:00")));
        assertEquals(new CaptureTime(LocalDateTime.of(2026, 9, 2, 9, 8, 0), true),
                MetadataParser.captureTime(entry("QuickTime:CreateDate", "2026:09:02 09:08:00")));
    }

    @Test
    void invalidCaptureTimeIsNull() {
        assertNull(MetadataParser.captureTime(entry("QuickTime:CreateDate", "0000:00:00 00:00:00")));
        assertNull(MetadataParser.captureTime(entry()));
    }
}

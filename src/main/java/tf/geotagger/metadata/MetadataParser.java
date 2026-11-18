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

import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.EnumMap;
import java.util.Map;
import tf.geotagger.model.CaptureTime;
import tf.geotagger.model.Field;

/**
 * Converts one entry of ExifTool's JSON output into GeoTagger values.
 * Priorities: composite GPS (EXIF / QuickTime) before XMP, XMP place before IPTC.
 */
final class MetadataParser {
    private static final DateTimeFormatter EXIF_DATE = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss");
    // ZoneOffset allows +-18 h
    private static final int MAX_ZONE_MINUTES = 18 * 60;
    /** zone of the camera clock in minutes (with -n), daylight saving included - not read with the other tags */
    static final String MAKER_ZONE_TAG = "Canon:TimeZone";

    private MetadataParser() {
    }

    static Map<Field, Object> values(Map<String, Object> entry) {
        final Map<Field, Object> values = new EnumMap<>(Field.class);
        put(values, Field.LATITUDE, firstDouble(entry, ReadTag.COMPOSITE_LATITUDE, ReadTag.XMP_LATITUDE));
        put(values, Field.LONGITUDE, firstDouble(entry, ReadTag.COMPOSITE_LONGITUDE, ReadTag.XMP_LONGITUDE));
        put(values, Field.ALTITUDE, altitude(entry));
        put(values, Field.COUNTRY_CODE, firstText(entry, ReadTag.XMP_COUNTRY_CODE, ReadTag.IPTC_COUNTRY_CODE));
        put(values, Field.COUNTRY, firstText(entry, ReadTag.XMP_COUNTRY, ReadTag.IPTC_COUNTRY));
        put(values, Field.STATE, firstText(entry, ReadTag.XMP_STATE, ReadTag.IPTC_STATE));
        put(values, Field.CITY, firstText(entry, ReadTag.XMP_CITY, ReadTag.IPTC_CITY));
        put(values, Field.SUBLOCATION, firstText(entry, ReadTag.XMP_SUBLOCATION, ReadTag.IPTC_SUBLOCATION));
        put(values, Field.TIME_OFFSET, offset(get(entry, ReadTag.EXIF_OFFSET_TIME_ORIGINAL)));
        return values;
    }

    static CaptureTime captureTime(Map<String, Object> entry) {
        LocalDateTime local = dateTime(get(entry, ReadTag.EXIF_DATE_TIME_ORIGINAL));
        if (local == null) {
            local = dateTime(get(entry, ReadTag.XMP_DATE_TIME_ORIGINAL));
        }
        if (local != null) {
            return new CaptureTime(local, false);
        }
        // QuickTime stores UTC
        final LocalDateTime utc = dateTime(get(entry, ReadTag.QUICKTIME_CREATE_DATE));
        return utc != null ? new CaptureTime(utc, true) : null;
    }

    /**
     * @param entry ExifTool's JSON entry of a file, read with -n and without -fast2 (that skips the maker notes)
     * @return zone the camera clock was set to (Canon:TimeZone in minutes, daylight saving included) - null if missing
     * or not a whole number of minutes within +-18 h
     */
    static ZoneOffset makerZone(Map<String, Object> entry) {
        if (!(entry.get(MAKER_ZONE_TAG) instanceof Number number)) {
            return null;
        }
        final double value = number.doubleValue();
        if (value != Math.rint(value) || Math.abs(value) > MAX_ZONE_MINUTES) {
            return null;
        }
        return ZoneOffset.ofTotalSeconds((int) value * 60);
    }

    private static void put(Map<Field, Object> values, Field field, Object value) {
        if (value != null) {
            values.put(field, value);
        }
    }

    private static Object get(Map<String, Object> entry, ReadTag tag) {
        return entry.get(tag.key());
    }

    private static Double altitude(Map<String, Object> entry) {
        final Double composite = toDouble(get(entry, ReadTag.COMPOSITE_ALTITUDE));
        if (composite != null) {
            return composite;
        }
        final Double xmp = toDouble(get(entry, ReadTag.XMP_ALTITUDE));
        if (xmp == null) {
            return null;
        }
        final Double ref = toDouble(get(entry, ReadTag.XMP_ALTITUDE_REF));
        return ref != null && ref.intValue() == 1 ? -xmp : xmp;
    }

    private static Double firstDouble(Map<String, Object> entry, ReadTag... tags) {
        for (ReadTag tag : tags) {
            final Double value = toDouble(get(entry, tag));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String firstText(Map<String, Object> entry, ReadTag... tags) {
        for (ReadTag tag : tags) {
            final String value = toText(get(entry, tag));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static Double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Double.valueOf(text.trim());
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }

    private static String toText(Object value) {
        if (value == null) {
            return null;
        }
        final String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private static LocalDateTime dateTime(Object value) {
        final String text = toText(value);
        if (text == null || text.length() < 19) {
            return null;
        }
        try {
            // ignore sub seconds and offset ("2026:09:02 11:08:00.123+02:00")
            return LocalDateTime.parse(text.substring(0, 19), EXIF_DATE);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private static ZoneOffset offset(Object value) {
        final String text = toText(value);
        if (text == null) {
            return null;
        }
        try {
            return ZoneOffset.of(text);
        } catch (DateTimeException ex) {
            return null;
        }
    }
}

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

import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import tf.geotagger.model.Field;
import tf.geotagger.model.MediaFile;

/**
 * Builds the ExifTool assignments for the pending changes of a file - see spec, sections "Felder" and "Schreibregeln".
 * Numeric values are written with "#=" (no print conversion), a missing value deletes the tag ("-TAG=").
 * Only tags of changed fields are touched, all other tags of the file stay as they are.
 */
final class WriteArgsBuilder {
    private static final Map<Field, String> IPTC_TAGS = new EnumMap<>(Map.of(
            Field.COUNTRY_CODE, "IPTC:Country-PrimaryLocationCode",
            Field.COUNTRY, "IPTC:Country-PrimaryLocationName",
            Field.STATE, "IPTC:Province-State",
            Field.CITY, "IPTC:City",
            Field.SUBLOCATION, "IPTC:Sub-location"));
    private static final Map<Field, String> XMP_TAGS = new EnumMap<>(Map.of(
            Field.COUNTRY_CODE, "XMP-iptcCore:CountryCode",
            Field.COUNTRY, "XMP-photoshop:Country",
            Field.STATE, "XMP-photoshop:State",
            Field.CITY, "XMP-photoshop:City",
            Field.SUBLOCATION, "XMP-iptcCore:Location"));

    private WriteArgsBuilder() {
    }

    static List<String> assignments(FormatProfile profile, MediaFile file) {
        final Map<Field, Object> changes = file.pendingChanges();
        final List<String> args = new ArrayList<>();
        if (changes.containsKey(Field.LATITUDE)) {
            coordinate(args, profile, "Latitude", file.latitude(), "N", "S");
        }
        if (changes.containsKey(Field.LONGITUDE)) {
            coordinate(args, profile, "Longitude", file.longitude(), "E", "W");
        }
        if (changes.containsKey(Field.ALTITUDE)) {
            altitude(args, profile, file.altitude());
        }
        if (profile == FormatProfile.QUICKTIME && (changes.containsKey(Field.LATITUDE)
                || changes.containsKey(Field.LONGITUDE) || changes.containsKey(Field.ALTITUDE))) {
            quickTimeCoordinates(args, file);
        }
        place(args, profile, changes);
        if (profile.writesExif() && changes.containsKey(Field.TIME_OFFSET)) {
            final ZoneOffset offset = (ZoneOffset) changes.get(Field.TIME_OFFSET);
            // ZoneOffset.UTC.getId() is "Z" - EXIF wants "+00:00"
            final String value = offset == null ? "" : ZoneOffset.UTC.equals(offset) ? "+00:00" : offset.getId();
            args.add("-ExifIFD:OffsetTimeOriginal=" + value);
        }
        return args;
    }

    private static void coordinate(List<String> args, FormatProfile profile, String name, Double value,
            String positiveRef, String negativeRef) {
        if (profile.writesExif()) {
            assignNumeric(args, "GPS:GPS" + name, value == null ? null : number(Math.abs(value)));
            assignNumeric(args, "GPS:GPS" + name + "Ref", value == null ? null : value < 0 ? negativeRef : positiveRef);
        }
        assignNumeric(args, "XMP-exif:GPS" + name, value == null ? null : number(value));
    }

    private static void altitude(List<String> args, FormatProfile profile, Double value) {
        final String absolute = value == null ? null : number(Math.abs(value));
        // AltitudeRef: 0 = above, 1 = below sea level
        final String ref = value == null ? null : value < 0 ? "1" : "0";
        if (profile.writesExif()) {
            assignNumeric(args, "GPS:GPSAltitude", absolute);
            assignNumeric(args, "GPS:GPSAltitudeRef", ref);
        }
        assignNumeric(args, "XMP-exif:GPSAltitude", absolute);
        assignNumeric(args, "XMP-exif:GPSAltitudeRef", ref);
    }

    private static void quickTimeCoordinates(List<String> args, MediaFile file) {
        if (!file.hasCoordinates()) {
            assignNumeric(args, "Keys:GPSCoordinates", null);
            return;
        }
        final Double altitude = file.altitude();
        assignNumeric(args, "Keys:GPSCoordinates", number(file.latitude()) + " " + number(file.longitude())
                + (altitude == null ? "" : " " + number(altitude)));
    }

    private static void place(List<String> args, FormatProfile profile, Map<Field, Object> changes) {
        boolean iptcChanged = false;
        for (Field field : Field.PLACE_FIELDS) {
            if (!changes.containsKey(field)) {
                continue;
            }
            final Object value = changes.get(field);
            final String text = value == null ? "" : value.toString();
            if (profile.writesIptc()) {
                args.add("-" + IPTC_TAGS.get(field) + "=" + text);
                iptcChanged = true;
            }
            args.add("-" + XMP_TAGS.get(field) + "=" + text);
        }
        if (iptcChanged) {
            // write IPTC as UTF-8 - umlauts in place names
            args.add("-IPTC:CodedCharacterSet=UTF8");
            args.add("-charset");
            args.add("iptc=UTF8");
        }
    }

    private static void assignNumeric(List<String> args, String tag, String value) {
        args.add(value == null ? "-" + tag + "=" : "-" + tag + "#=" + value);
    }

    private static String number(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }
}

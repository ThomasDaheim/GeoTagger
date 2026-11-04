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

import java.util.Arrays;
import java.util.List;

/**
 * Tags requested from ExifTool ("-json -n -G1": key = family 1 group + tag name).
 * Every constant is requested when reading - see keys().
 */
enum ReadTag {
    COMPOSITE_LATITUDE("Composite:GPSLatitude"),
    COMPOSITE_LONGITUDE("Composite:GPSLongitude"),
    COMPOSITE_ALTITUDE("Composite:GPSAltitude"),
    XMP_LATITUDE("XMP-exif:GPSLatitude"),
    XMP_LONGITUDE("XMP-exif:GPSLongitude"),
    XMP_ALTITUDE("XMP-exif:GPSAltitude"),
    XMP_ALTITUDE_REF("XMP-exif:GPSAltitudeRef"),

    XMP_COUNTRY_CODE("XMP-iptcCore:CountryCode"),
    XMP_COUNTRY("XMP-photoshop:Country"),
    XMP_STATE("XMP-photoshop:State"),
    XMP_CITY("XMP-photoshop:City"),
    XMP_SUBLOCATION("XMP-iptcCore:Location"),
    IPTC_COUNTRY_CODE("IPTC:Country-PrimaryLocationCode"),
    IPTC_COUNTRY("IPTC:Country-PrimaryLocationName"),
    IPTC_STATE("IPTC:Province-State"),
    IPTC_CITY("IPTC:City"),
    IPTC_SUBLOCATION("IPTC:Sub-location"),

    EXIF_DATE_TIME_ORIGINAL("ExifIFD:DateTimeOriginal"),
    EXIF_OFFSET_TIME_ORIGINAL("ExifIFD:OffsetTimeOriginal"),
    XMP_DATE_TIME_ORIGINAL("XMP-exif:DateTimeOriginal"),
    QUICKTIME_CREATE_DATE("QuickTime:CreateDate");

    /** not a tag - ExifTool adds the file name to every JSON entry */
    static final String SOURCE_FILE = "SourceFile";

    private final String key;

    ReadTag(String key) {
        this.key = key;
    }

    /** name as requested from ExifTool and as key in its JSON output */
    String key() {
        return key;
    }

    static List<String> keys() {
        return Arrays.stream(values()).map(ReadTag::key).toList();
    }
}

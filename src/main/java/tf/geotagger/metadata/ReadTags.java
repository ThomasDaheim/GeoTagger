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

import java.util.List;

/**
 * Tags requested from ExifTool ("-json -n -G1": key = family 1 group + tag name).
 */
final class ReadTags {
    static final String SOURCE_FILE = "SourceFile";

    static final String COMPOSITE_LATITUDE = "Composite:GPSLatitude";
    static final String COMPOSITE_LONGITUDE = "Composite:GPSLongitude";
    static final String COMPOSITE_ALTITUDE = "Composite:GPSAltitude";
    static final String XMP_LATITUDE = "XMP-exif:GPSLatitude";
    static final String XMP_LONGITUDE = "XMP-exif:GPSLongitude";
    static final String XMP_ALTITUDE = "XMP-exif:GPSAltitude";
    static final String XMP_ALTITUDE_REF = "XMP-exif:GPSAltitudeRef";

    static final String XMP_COUNTRY_CODE = "XMP-iptcCore:CountryCode";
    static final String XMP_COUNTRY = "XMP-photoshop:Country";
    static final String XMP_STATE = "XMP-photoshop:State";
    static final String XMP_CITY = "XMP-photoshop:City";
    static final String XMP_SUBLOCATION = "XMP-iptcCore:Location";
    static final String IPTC_COUNTRY_CODE = "IPTC:Country-PrimaryLocationCode";
    static final String IPTC_COUNTRY = "IPTC:Country-PrimaryLocationName";
    static final String IPTC_STATE = "IPTC:Province-State";
    static final String IPTC_CITY = "IPTC:City";
    static final String IPTC_SUBLOCATION = "IPTC:Sub-location";

    static final String EXIF_DATE_TIME_ORIGINAL = "ExifIFD:DateTimeOriginal";
    static final String EXIF_OFFSET_TIME_ORIGINAL = "ExifIFD:OffsetTimeOriginal";
    static final String XMP_DATE_TIME_ORIGINAL = "XMP-exif:DateTimeOriginal";
    static final String QUICKTIME_CREATE_DATE = "QuickTime:CreateDate";

    static final List<String> ALL = List.of(
            COMPOSITE_LATITUDE, COMPOSITE_LONGITUDE, COMPOSITE_ALTITUDE,
            XMP_LATITUDE, XMP_LONGITUDE, XMP_ALTITUDE, XMP_ALTITUDE_REF,
            XMP_COUNTRY_CODE, XMP_COUNTRY, XMP_STATE, XMP_CITY, XMP_SUBLOCATION,
            IPTC_COUNTRY_CODE, IPTC_COUNTRY, IPTC_STATE, IPTC_CITY, IPTC_SUBLOCATION,
            EXIF_DATE_TIME_ORIGINAL, EXIF_OFFSET_TIME_ORIGINAL, XMP_DATE_TIME_ORIGINAL, QUICKTIME_CREATE_DATE);

    private ReadTags() {
    }
}

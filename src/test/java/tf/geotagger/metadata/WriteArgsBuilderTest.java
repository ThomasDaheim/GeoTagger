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

import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tf.geotagger.model.Field;
import tf.geotagger.model.MediaFile;
import tf.geotagger.model.Snapshot;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WriteArgsBuilderTest {
    private static MediaFile file(FormatProfile profile, Map<Field, Object> original) {
        return new MediaFile(Path.of("C:/fotos/x"), profile.mediaType(), new Snapshot(null, FileTime.fromMillis(0), original));
    }

    private static MediaFile file(FormatProfile profile) {
        return file(profile, Map.of());
    }

    @Test
    void noChangesGiveNoArguments() {
        assertTrue(WriteArgsBuilder.assignments(FormatProfile.EXIF_IPTC_XMP, file(FormatProfile.EXIF_IPTC_XMP)).isEmpty());
    }

    @Test
    void coordinatesForExifProfiles() {
        final MediaFile media = file(FormatProfile.EXIF_XMP);
        media.set(Field.LATITUDE, 46.5);
        media.set(Field.LONGITUDE, -0.25);
        assertEquals(List.of(
                "-GPS:GPSLatitude#=46.5", "-GPS:GPSLatitudeRef#=N", "-XMP-exif:GPSLatitude#=46.5",
                "-GPS:GPSLongitude#=0.25", "-GPS:GPSLongitudeRef#=W", "-XMP-exif:GPSLongitude#=-0.25"),
                WriteArgsBuilder.assignments(FormatProfile.EXIF_XMP, media));
    }

    @Test
    void clearedCoordinatesAreDeleted() {
        final Map<Field, Object> original = new EnumMap<>(Field.class);
        original.put(Field.LATITUDE, 46.5);
        final MediaFile media = file(FormatProfile.EXIF_IPTC_XMP, original);
        media.set(Field.LATITUDE, null);
        assertEquals(List.of("-GPS:GPSLatitude=", "-GPS:GPSLatitudeRef=", "-XMP-exif:GPSLatitude="),
                WriteArgsBuilder.assignments(FormatProfile.EXIF_IPTC_XMP, media));
    }

    @Test
    void altitudeBelowSeaLevel() {
        final MediaFile media = file(FormatProfile.EXIF_XMP);
        media.set(Field.ALTITUDE, -12.5);
        assertEquals(List.of(
                "-GPS:GPSAltitude#=12.5", "-GPS:GPSAltitudeRef#=1",
                "-XMP-exif:GPSAltitude#=12.5", "-XMP-exif:GPSAltitudeRef#=1"),
                WriteArgsBuilder.assignments(FormatProfile.EXIF_XMP, media));
    }

    @Test
    void quickTimeGetsCombinedCoordinatesAndXmp() {
        final Map<Field, Object> original = new EnumMap<>(Field.class);
        original.put(Field.ALTITUDE, 100.0);
        final MediaFile media = file(FormatProfile.QUICKTIME, original);
        media.set(Field.LATITUDE, 46.5);
        media.set(Field.LONGITUDE, 11.25);
        assertEquals(List.of(
                "-XMP-exif:GPSLatitude#=46.5", "-XMP-exif:GPSLongitude#=11.25",
                "-Keys:GPSCoordinates#=46.5 11.25 100"),
                WriteArgsBuilder.assignments(FormatProfile.QUICKTIME, media));
    }

    @Test
    void quickTimeCoordinatesAreDeletedWithoutLatitude() {
        final Map<Field, Object> original = new EnumMap<>(Field.class);
        original.put(Field.LATITUDE, 46.5);
        original.put(Field.LONGITUDE, 11.25);
        final MediaFile media = file(FormatProfile.QUICKTIME, original);
        media.set(Field.LATITUDE, null);
        assertTrue(WriteArgsBuilder.assignments(FormatProfile.QUICKTIME, media).contains("-Keys:GPSCoordinates="));
    }

    @Test
    void placeGoesToIptcAndXmpForJpeg() {
        final MediaFile media = file(FormatProfile.EXIF_IPTC_XMP);
        media.set(Field.CITY, "Völs");
        media.set(Field.SUBLOCATION, "Seiser Alm");
        assertEquals(List.of(
                "-IPTC:City=Völs", "-XMP-photoshop:City=Völs",
                "-IPTC:Sub-location=Seiser Alm", "-XMP-iptcCore:Location=Seiser Alm",
                "-IPTC:CodedCharacterSet=UTF8", "-charset", "iptc=UTF8"),
                WriteArgsBuilder.assignments(FormatProfile.EXIF_IPTC_XMP, media));
    }

    @Test
    void placeOnlyToXmpWithoutIptc() {
        final MediaFile media = file(FormatProfile.EXIF_XMP);
        media.set(Field.COUNTRY_CODE, "IT");
        media.set(Field.COUNTRY, "Italien");
        media.set(Field.STATE, "Südtirol");
        assertEquals(List.of(
                "-XMP-iptcCore:CountryCode=IT", "-XMP-photoshop:Country=Italien", "-XMP-photoshop:State=Südtirol"),
                WriteArgsBuilder.assignments(FormatProfile.EXIF_XMP, media));
    }

    @Test
    void timeOffsetOnlyWithExif() {
        final MediaFile jpeg = file(FormatProfile.EXIF_IPTC_XMP);
        jpeg.set(Field.TIME_OFFSET, ZoneOffset.ofHours(2));
        assertEquals(List.of("-ExifIFD:OffsetTimeOriginal=+02:00"), WriteArgsBuilder.assignments(FormatProfile.EXIF_IPTC_XMP, jpeg));

        final MediaFile utc = file(FormatProfile.EXIF_XMP);
        utc.set(Field.TIME_OFFSET, ZoneOffset.UTC);
        assertEquals(List.of("-ExifIFD:OffsetTimeOriginal=+00:00"), WriteArgsBuilder.assignments(FormatProfile.EXIF_XMP, utc));

        final MediaFile video = file(FormatProfile.QUICKTIME);
        video.set(Field.TIME_OFFSET, ZoneOffset.ofHours(2));
        assertTrue(WriteArgsBuilder.assignments(FormatProfile.QUICKTIME, video).isEmpty());
    }

    @Test
    void sidecarGetsOnlyXmp() {
        final MediaFile media = file(FormatProfile.SIDECAR);
        media.set(Field.LATITUDE, 46.5);
        media.set(Field.CITY, "Rom");
        final List<String> args = WriteArgsBuilder.assignments(FormatProfile.SIDECAR, media);
        assertEquals(List.of("-XMP-exif:GPSLatitude#=46.5", "-XMP-photoshop:City=Rom"), args);
        assertFalse(args.stream().anyMatch(arg -> arg.startsWith("-GPS:") || arg.startsWith("-IPTC:") || arg.startsWith("-Keys:")));
    }
}

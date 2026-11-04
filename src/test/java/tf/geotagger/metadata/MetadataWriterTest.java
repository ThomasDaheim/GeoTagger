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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tf.exiftool.ExifTool;
import tf.geotagger.model.CaptureTime;
import tf.geotagger.model.Field;
import tf.geotagger.model.MediaFile;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetadataWriterTest {
    private static ExifTool exifTool;
    private static MetadataReader reader;
    private static MetadataWriter writer;

    @TempDir
    Path dir;

    @BeforeAll
    static void start() {
        exifTool = new ExifTool(ExifToolTestSupport.require());
        reader = new MetadataReader(exifTool);
        writer = new MetadataWriter(exifTool, reader);
    }

    @AfterAll
    static void stop() {
        if (exifTool != null) {
            exifTool.close();
        }
    }

    private static MediaFile load(Path file) {
        return reader.read(List.of(file)).get(0);
    }

    private static void setAll(MediaFile media) {
        media.set(Field.LATITUDE, -33.856784);
        media.set(Field.LONGITUDE, 151.215297);
        media.set(Field.ALTITUDE, -12.5);
        media.set(Field.COUNTRY_CODE, "AU");
        media.set(Field.COUNTRY, "Australien");
        media.set(Field.STATE, "New South Wales");
        media.set(Field.CITY, "Sydney");
        media.set(Field.SUBLOCATION, "Opernhaus Bühne");
        media.set(Field.TIME_OFFSET, ZoneOffset.ofHours(10));
    }

    private static void assertAll(MediaFile media, boolean withOffset) {
        assertEquals(-33.856784, media.latitude(), 1e-5);
        assertEquals(151.215297, media.longitude(), 1e-5);
        assertEquals(-12.5, media.altitude(), 0.01);
        assertEquals("AU", media.text(Field.COUNTRY_CODE));
        assertEquals("Australien", media.text(Field.COUNTRY));
        assertEquals("New South Wales", media.text(Field.STATE));
        assertEquals("Sydney", media.text(Field.CITY));
        assertEquals("Opernhaus Bühne", media.text(Field.SUBLOCATION));
        assertEquals(withOffset ? ZoneOffset.ofHours(10) : null, media.timeOffset());
    }

    private static void assertSaved(SaveResult result) {
        assertEquals(1, result.files().size());
        final SaveResult.FileResult file = result.files().get(0);
        assertEquals(SaveResult.Status.SAVED, file.status(), file.message());
    }

    private static Map<String, Object> tags(Path file, String... tags) {
        return exifTool.readJson(List.of(file), List.of(tags)).get(0);
    }

    @ParameterizedTest
    @ValueSource(strings = {"jpg", "png", "tif"})
    void allFieldsRoundTrip(String extension) {
        final Path file = TestFiles.image(dir, "bild." + extension);
        final MediaFile media = load(file);
        setAll(media);

        assertSaved(writer.save(List.of(media)));

        assertFalse(media.isChanged());
        assertAll(media, true);
        assertAll(load(file), true);
    }

    @Test
    void iptcOnlyForJpeg() {
        final Path jpeg = TestFiles.image(dir, "bild.jpg");
        final Path png = TestFiles.image(dir, "bild.png");
        final MediaFile jpegMedia = load(jpeg);
        final MediaFile pngMedia = load(png);
        jpegMedia.set(Field.CITY, "Völs");
        pngMedia.set(Field.CITY, "Völs");

        writer.save(List.of(jpegMedia, pngMedia));

        assertEquals("Völs", tags(jpeg, "IPTC:City").get("IPTC:City"));
        assertNull(tags(png, "IPTC:City").get("IPTC:City"));
        assertEquals("Völs", tags(png, "XMP-photoshop:City").get("XMP-photoshop:City"));
    }

    @Test
    void originalIsKept() throws IOException {
        final Path file = TestFiles.image(dir, "bild.jpg");
        final byte[] before = Files.readAllBytes(file);
        final MediaFile media = load(file);
        media.set(Field.CITY, "Meran");

        assertSaved(writer.save(List.of(media)));

        final Path original = dir.resolve("bild.jpg_original");
        assertTrue(Files.exists(original));
        assertArrayEquals(before, Files.readAllBytes(original));
    }

    @Test
    void fileTimesArePreserved() throws IOException {
        final Path file = TestFiles.image(dir, "zeit.jpg");
        final FileTime old = FileTime.from(Instant.parse("2020-01-02T03:04:05Z"));
        Files.getFileAttributeView(file, BasicFileAttributeView.class).setTimes(old, null, old);
        final MediaFile media = load(file);
        media.set(Field.CITY, "Meran");

        assertSaved(writer.save(List.of(media)));

        final BasicFileAttributes after = Files.readAttributes(file, BasicFileAttributes.class);
        assertEquals(old, after.lastModifiedTime());
        assertEquals(old, after.creationTime());
    }

    @Test
    void captureTimeIsNotChanged() {
        final Path file = TestFiles.image(dir, "bild.jpg");
        exifTool.execute(List.of("-overwrite_original", "-ExifIFD:DateTimeOriginal=2026:09:02 11:08:00", file.toString()));
        final MediaFile media = load(file);
        final CaptureTime before = media.captureTime();
        assertNotNull(before);
        setAll(media);

        assertSaved(writer.save(List.of(media)));

        assertEquals(before, load(file).captureTime());
        assertEquals("2026:09:02 11:08:00", tags(file, "ExifIFD:DateTimeOriginal").get("ExifIFD:DateTimeOriginal"));
    }

    @Test
    void imageDirectionIsPreserved() {
        final Path file = TestFiles.image(dir, "handy.jpg");
        exifTool.execute(List.of("-overwrite_original", "-GPS:GPSImgDirection#=74", "-GPS:GPSImgDirectionRef#=M",
                "-GPS:GPSDestBearing#=80", file.toString()));
        final MediaFile media = load(file);
        media.set(Field.LATITUDE, 46.4);
        media.set(Field.LONGITUDE, 11.6);
        media.set(Field.ALTITUDE, 1700.0);
        media.set(Field.CITY, "Karersee");

        assertSaved(writer.save(List.of(media)));

        final Map<String, Object> direction = tags(file, "GPS:GPSImgDirection", "GPS:GPSImgDirectionRef", "GPS:GPSDestBearing");
        assertEquals(74.0, ((Number) direction.get("GPS:GPSImgDirection")).doubleValue(), 1e-9);
        assertEquals("M", direction.get("GPS:GPSImgDirectionRef"));
        assertEquals(80.0, ((Number) direction.get("GPS:GPSDestBearing")).doubleValue(), 1e-9);
    }

    @Test
    void clearedFieldIsDeletedInIptcAndXmp() {
        final Path file = TestFiles.image(dir, "bild.jpg");
        final MediaFile media = load(file);
        media.set(Field.CITY, "Meran");
        assertSaved(writer.save(List.of(media)));

        media.set(Field.CITY, null);
        assertSaved(writer.save(List.of(media)));

        assertNull(load(file).text(Field.CITY));
        final Map<String, Object> city = tags(file, "IPTC:City", "XMP-photoshop:City");
        assertNull(city.get("IPTC:City"));
        assertNull(city.get("XMP-photoshop:City"));
    }

    @Test
    void fileChangedOutsideIsSkipped() throws IOException {
        final Path file = TestFiles.image(dir, "bild.jpg");
        final MediaFile media = load(file);
        Files.setLastModifiedTime(file, FileTime.from(Instant.now().plusSeconds(60)));
        media.set(Field.CITY, "Meran");

        final SaveResult result = writer.save(List.of(media));

        assertEquals(SaveResult.Status.SKIPPED_MODIFIED, result.files().get(0).status());
        assertTrue(media.isChanged());
        assertNull(tags(file, "XMP-photoshop:City").get("XMP-photoshop:City"));
    }

    @Test
    void writeErrorKeepsPendingChanges() throws IOException {
        final Path broken = Files.write(dir.resolve("kaputt.jpg"), new byte[] {1, 2, 3, 4});
        final MediaFile media = load(broken);
        media.set(Field.CITY, "Meran");

        final SaveResult result = writer.save(List.of(media));

        assertEquals(SaveResult.Status.FAILED, result.files().get(0).status());
        assertFalse(result.files().get(0).message().isBlank());
        assertTrue(media.isChanged());
    }

    @Test
    void unchangedFilesAreNotWrittenButCounted() {
        final Path file = TestFiles.image(dir, "bild.jpg");
        final MediaFile media = load(file);
        final List<Integer> progress = new ArrayList<>();

        final SaveResult result = writer.save(List.of(media), progress::add);

        assertTrue(result.files().isEmpty());
        assertEquals(List.of(1), progress);
        assertFalse(Files.exists(dir.resolve("bild.jpg_original")));
    }

    @Test
    void sidecarIsCreatedAndUpdated() throws IOException {
        final byte[] content = {1, 2, 3, 4};
        final Path video = Files.write(dir.resolve("clip.avi"), content);
        final MediaFile media = load(video);
        media.set(Field.LATITUDE, 41.9);
        media.set(Field.LONGITUDE, 12.5);
        media.set(Field.CITY, "Rom");

        assertSaved(writer.save(List.of(media)));
        assertTrue(Files.exists(dir.resolve("clip.xmp")));
        assertArrayEquals(content, Files.readAllBytes(video));
        assertEquals("Rom", load(video).text(Field.CITY));

        media.set(Field.CITY, "Bozen");
        assertSaved(writer.save(List.of(media)));
        final MediaFile reloaded = load(video);
        assertEquals("Bozen", reloaded.text(Field.CITY));
        assertEquals(41.9, reloaded.latitude(), 1e-6);
    }

    @TestFactory
    Stream<DynamicTest> samplesRoundTrip() throws IOException {
        final Path samples = Path.of("src/test/resources/samples");
        if (!Files.isDirectory(samples)) {
            return Stream.empty();
        }
        final List<Path> files;
        try (Stream<Path> list = Files.list(samples)) {
            files = list.filter(file -> FormatProfile.of(file).isPresent()).sorted().toList();
        }
        return files.stream().map(sample -> DynamicTest.dynamicTest(sample.getFileName().toString(), () -> {
            final Path copy = Files.copy(sample, dir.resolve(sample.getFileName()));
            final MediaFile media = load(copy);
            setAll(media);

            assertSaved(writer.save(List.of(media)));

            assertAll(load(copy), FormatProfile.of(copy).orElseThrow().writesExif());
        }));
    }
}

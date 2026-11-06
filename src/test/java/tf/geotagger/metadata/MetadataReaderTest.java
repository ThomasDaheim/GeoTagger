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
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tf.exiftool.ExifTool;
import tf.geotagger.model.CaptureTime;
import tf.geotagger.model.Field;
import tf.geotagger.model.MediaFile;
import tf.geotagger.model.MediaType;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetadataReaderTest {
    private static ExifTool exifTool;
    private static MetadataReader reader;

    @TempDir
    Path dir;

    @BeforeAll
    static void start() {
        exifTool = new ExifTool(ExifToolTestSupport.require());
        reader = new MetadataReader(exifTool);
    }

    @AfterAll
    static void stop() {
        if (exifTool != null) {
            exifTool.close();
        }
    }

    @Test
    void readsGpsPlaceOffsetAndCaptureTime() {
        final Path file = TestFiles.image(dir, "bild.jpg");
        exifTool.execute(List.of("-overwrite_original",
                "-GPS:GPSLatitude#=46.4071", "-GPS:GPSLatitudeRef#=N",
                "-GPS:GPSLongitude#=11.5932", "-GPS:GPSLongitudeRef#=E",
                "-GPS:GPSAltitude#=1200", "-GPS:GPSAltitudeRef#=0",
                "-XMP-photoshop:City=Völs", "-ExifIFD:DateTimeOriginal=2026:09:02 11:08:00",
                "-ExifIFD:OffsetTimeOriginal=+02:00", file.toString()));

        final MediaFile media = reader.read(List.of(file)).get(0);

        assertEquals(MediaType.IMAGE, media.mediaType());
        assertEquals(46.4071, media.latitude(), 1e-6);
        assertEquals(11.5932, media.longitude(), 1e-6);
        assertEquals(1200.0, media.altitude(), 1e-6);
        assertEquals("Völs", media.text(Field.CITY));
        assertEquals(ZoneOffset.ofHours(2), media.timeOffset());
        assertEquals(new CaptureTime(LocalDateTime.of(2026, 9, 2, 11, 8, 0), false), media.captureTime());
        assertFalse(media.isChanged());
    }

    @Test
    void iptcIsUsedWhenXmpIsMissing() {
        final Path file = TestFiles.image(dir, "iptc.jpg");
        exifTool.execute(List.of("-overwrite_original", "-charset", "iptc=UTF8", "-IPTC:CodedCharacterSet=UTF8",
                "-IPTC:City=Brixen", file.toString()));
        assertEquals("Brixen", reader.read(List.of(file)).get(0).text(Field.CITY));
    }

    @Test
    void unsupportedFilesAreIgnored() throws IOException {
        final Path text = Files.writeString(dir.resolve("notiz.txt"), "x");
        final Path image = TestFiles.image(dir, "a.png");
        final List<MediaFile> files = reader.read(List.of(text, image));
        assertEquals(1, files.size());
        assertEquals(image, files.get(0).path());
    }

    @Test
    void unreadableFileIsStillListedWithoutValues() throws IOException {
        final Path broken = Files.write(dir.resolve("kaputt.jpg"), new byte[] {1, 2, 3, 4});
        final MediaFile media = reader.read(List.of(broken)).get(0);
        assertTrue(media.original().values().isEmpty());
        assertNull(media.captureTime());
    }

    @Test
    void readsInChunksKeepsOrderAndReportsProgress() throws IOException {
        final Path first = TestFiles.image(dir, "bild_000.jpg");
        final List<Path> files = new ArrayList<>(List.of(first));
        for (int i = 1; i < 105; i++) {
            files.add(Files.copy(first, dir.resolve(String.format("bild_%03d.jpg", i))));
        }
        final List<Integer> progress = new ArrayList<>();

        final List<MediaFile> media = reader.read(files, progress::add);

        assertEquals(105, media.size());
        assertEquals(files, media.stream().map(MediaFile::path).toList());
        // progress every 50 files - with 100 the status looked like hanging (user feedback)
        assertEquals(List.of(50, 100, 105), progress);
    }

    @Test
    void sidecarValuesOverlayTheMediaFile() throws IOException {
        final Path video = Files.write(dir.resolve("clip.avi"), new byte[] {1, 2, 3, 4});
        exifTool.execute(List.of("-XMP-photoshop:City=Rom", "-XMP-exif:GPSLatitude#=41.9", "-o",
                dir.resolve("clip.xmp").toString()));

        final MediaFile media = reader.read(List.of(video)).get(0);

        assertEquals(MediaType.VIDEO, media.mediaType());
        assertEquals("Rom", media.text(Field.CITY));
        assertEquals(41.9, media.latitude(), 1e-6);
    }
}

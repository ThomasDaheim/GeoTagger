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
package tf.geotagger.preview;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tf.exiftool.ExifTool;
import tf.geotagger.metadata.ExifToolTestSupport;
import tf.geotagger.metadata.TestFiles;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreviewProviderTest {
    private static ExifTool exifTool;

    @TempDir
    Path dir;

    private Path cache;
    private PreviewProvider provider;

    @BeforeAll
    static void start() {
        exifTool = new ExifTool(ExifToolTestSupport.require());
    }

    @AfterAll
    static void stop() {
        if (exifTool != null) {
            exifTool.close();
        }
    }

    @BeforeEach
    void createProvider() {
        cache = dir.resolve("cache");
        provider = new PreviewProvider(exifTool, cache);
    }

    // ExifTool recognizes formats by content: a JPEG named like a format JavaFX can't show stands in for HEIC / RAW
    private Path withEmbeddedImage(String name) throws IOException {
        final Path thumbnail = TestFiles.image(dir, "thumb.jpg");
        final Path base = TestFiles.image(dir, "base.jpg");
        exifTool.execute(List.of("-overwrite_original", "-ThumbnailImage<=" + thumbnail, base.toString()));
        return Files.copy(base, dir.resolve(name));
    }

    @Test
    void jpegIsShownDirectly() {
        final Path jpeg = TestFiles.image(dir, "bild.jpg");

        assertEquals(Optional.of(new Preview(jpeg, 0)), provider.preview(jpeg));
    }

    @Test
    void orientationGivesTheRotation() {
        final Path jpeg = TestFiles.image(dir, "hochkant.jpg");
        exifTool.execute(List.of("-overwrite_original", "-IFD0:Orientation#=6", jpeg.toString()));

        assertEquals(90, provider.preview(jpeg).orElseThrow().rotation());
    }

    @Test
    void embeddedImageIsExtractedOnce() throws IOException {
        final Path heic = withEmbeddedImage("kamera.heic");

        final Preview first = provider.preview(heic).orElseThrow();
        assertTrue(first.image().startsWith(cache));
        assertArrayEquals(Files.readAllBytes(dir.resolve("thumb.jpg")), Files.readAllBytes(first.image()));
        final FileTime extracted = Files.getLastModifiedTime(first.image());

        assertEquals(first, provider.preview(heic).orElseThrow());
        assertEquals(extracted, Files.getLastModifiedTime(first.image()));
    }

    @Test
    void nothingToShowWithoutEmbeddedImage() {
        final Path tiff = TestFiles.image(dir, "scan.tif");

        assertTrue(provider.preview(tiff).isEmpty());
    }

    @Test
    void clearCacheRemovesExtractedImages() throws IOException {
        final Preview preview = provider.preview(withEmbeddedImage("kamera.heic")).orElseThrow();

        provider.clearCache();

        assertFalse(Files.exists(preview.image()));
    }
}

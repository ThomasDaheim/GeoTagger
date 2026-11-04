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
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tf.geotagger.model.MediaType;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FormatProfileTest {
    @Test
    void profileIsChosenByExtensionIgnoringCase() {
        assertEquals(Optional.of(FormatProfile.EXIF_IPTC_XMP), FormatProfile.of(Path.of("a/IMG_1.JPG")));
        assertEquals(Optional.of(FormatProfile.EXIF_IPTC_XMP), FormatProfile.of(Path.of("a/scan.tiff")));
        assertEquals(Optional.of(FormatProfile.EXIF_XMP), FormatProfile.of(Path.of("a/IMG_1.CR3")));
        assertEquals(Optional.of(FormatProfile.EXIF_XMP), FormatProfile.of(Path.of("a/PXL_1.heic")));
        assertEquals(Optional.of(FormatProfile.EXIF_XMP), FormatProfile.of(Path.of("a/screen.png")));
        assertEquals(Optional.of(FormatProfile.QUICKTIME), FormatProfile.of(Path.of("a/MVI_1.MOV")));
        assertEquals(Optional.of(FormatProfile.SIDECAR), FormatProfile.of(Path.of("a/clip.avi")));
    }

    @Test
    void unknownFilesHaveNoProfile() {
        assertTrue(FormatProfile.of(Path.of("a/track.gpx")).isEmpty());
        assertTrue(FormatProfile.of(Path.of("a/clip.xmp")).isEmpty());
        assertTrue(FormatProfile.of(Path.of("a/noextension")).isEmpty());
    }

    @Test
    void tagFamiliesPerProfile() {
        assertTrue(FormatProfile.EXIF_IPTC_XMP.writesIptc());
        assertFalse(FormatProfile.EXIF_XMP.writesIptc());
        assertTrue(FormatProfile.EXIF_XMP.writesExif());
        assertFalse(FormatProfile.QUICKTIME.writesExif());
        assertFalse(FormatProfile.SIDECAR.writesExif());
        assertEquals(MediaType.VIDEO, FormatProfile.QUICKTIME.mediaType());
        assertEquals(MediaType.IMAGE, FormatProfile.EXIF_XMP.mediaType());
    }

    @Test
    void sidecarReplacesTheExtension() {
        assertEquals(Path.of("C:/fotos/clip.xmp"), FormatProfile.sidecarFor(Path.of("C:/fotos/clip.avi")));
        assertEquals(Path.of("C:/fotos/my.clip.xmp"), FormatProfile.sidecarFor(Path.of("C:/fotos/my.clip.mts")));
    }
}

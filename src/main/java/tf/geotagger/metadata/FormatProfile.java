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
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import tf.geotagger.model.MediaType;

/**
 * Which tag families GeoTagger writes for a file extension - see spec, section "Schreibregeln".
 */
public enum FormatProfile {
    /** EXIF + IPTC + XMP directly in the file. */
    EXIF_IPTC_XMP(MediaType.IMAGE, true, true, "jpg", "jpeg", "tif", "tiff"),
    /** EXIF + XMP directly in the file - IPTC isn't standard here. */
    EXIF_XMP(MediaType.IMAGE, true, false, "png", "heic", "heif", "dng", "cr2", "cr3", "nef", "arw", "orf", "rw2", "raf"),
    /** QuickTime GPS (Keys:GPSCoordinates) + XMP directly in the file. */
    QUICKTIME(MediaType.VIDEO, false, false, "mov", "mp4", "m4v"),
    /** ExifTool can't write these - XMP goes into <name without extension>.xmp next to the file. */
    SIDECAR(MediaType.VIDEO, false, false, "avi", "mts", "m2ts");

    private final MediaType mediaType;
    private final boolean exif;
    private final boolean iptc;
    private final List<String> extensions;

    FormatProfile(MediaType mediaType, boolean exif, boolean iptc, String... extensions) {
        this.mediaType = mediaType;
        this.exif = exif;
        this.iptc = iptc;
        this.extensions = List.of(extensions);
    }

    public MediaType mediaType() {
        return mediaType;
    }

    public boolean writesExif() {
        return exif;
    }

    public boolean writesIptc() {
        return iptc;
    }

    public static Optional<FormatProfile> of(Path file) {
        final String extension = extension(file);
        for (FormatProfile profile : values()) {
            if (profile.extensions.contains(extension)) {
                return Optional.of(profile);
            }
        }
        return Optional.empty();
    }

    public static Path sidecarFor(Path file) {
        final String name = file.getFileName().toString();
        final int dot = name.lastIndexOf('.');
        return file.resolveSibling((dot > 0 ? name.substring(0, dot) : name) + ".xmp");
    }

    private static String extension(Path file) {
        final String name = file.getFileName().toString();
        final int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }
}

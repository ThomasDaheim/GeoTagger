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
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.IntConsumer;
import java.util.regex.Pattern;
import tf.geo.exiftool.ExecResult;
import tf.geo.exiftool.ExifTool;
import tf.geo.exiftool.ExifToolException;
import tf.geotagger.metadata.SaveResult.FileResult;
import tf.geotagger.metadata.SaveResult.Status;
import tf.geotagger.model.MediaFile;

/**
 * Writes the pending changes of files - see spec, section "Schreibregeln":
 * ExifTool keeps the original as *_original, -P keeps the modification date and the creation date is restored here
 * as well. After writing, the file is read again and becomes the new original.
 */
public class MetadataWriter {
    private static final Pattern SUCCESS = Pattern.compile("\\b[1-9]\\d* image files (updated|created)\\b");

    private final ExifTool exifTool;
    private final MetadataReader reader;

    public MetadataWriter(ExifTool exifTool, MetadataReader reader) {
        this.exifTool = Objects.requireNonNull(exifTool);
        this.reader = Objects.requireNonNull(reader);
    }

    public SaveResult save(List<MediaFile> files) {
        return save(files, count -> {});
    }

    /**
     * @param progress gets the number of files handled so far (changed or not)
     */
    public SaveResult save(List<MediaFile> files, IntConsumer progress) {
        final List<FileResult> results = new ArrayList<>();
        int done = 0;
        for (MediaFile file : files) {
            if (file.isChanged()) {
                results.add(saveOne(file));
            }
            progress.accept(++done);
        }
        return new SaveResult(results);
    }

    private FileResult saveOne(MediaFile file) {
        final Path path = file.path();
        final FormatProfile profile = FormatProfile.of(path).orElseThrow();
        try {
            final BasicFileAttributes before = Files.readAttributes(path, BasicFileAttributes.class);
            if (!before.lastModifiedTime().equals(file.original().lastModified())) {
                return new FileResult(file, Status.SKIPPED_MODIFIED, "File was changed outside GeoTagger since it was loaded");
            }

            final List<String> args = new ArrayList<>(List.of("-P"));
            args.addAll(WriteArgsBuilder.assignments(profile, file));
            if (profile == FormatProfile.SIDECAR) {
                final Path sidecar = FormatProfile.sidecarFor(path).toAbsolutePath();
                if (Files.exists(sidecar)) {
                    args.add(sidecar.toString());
                } else {
                    // ExifTool creates a new XMP file from the assignments alone
                    args.add("-o");
                    args.add(sidecar.toString());
                }
            } else {
                args.add(path.toAbsolutePath().toString());
            }

            final ExecResult result = exifTool.execute(args);
            if (result.hasErrors() || !SUCCESS.matcher(result.stdoutText()).find()) {
                final List<String> details = result.hasErrors() ? result.errors() : result.stdout();
                return new FileResult(file, Status.FAILED, String.join("; ", details));
            }

            restoreTimes(path, before);
            file.replaceOriginal(reader.read(List.of(path)).get(0).original());
            return new FileResult(file, Status.SAVED, String.join("; ", result.warnings()));
        } catch (IOException | UncheckedIOException | ExifToolException ex) {
            return new FileResult(file, Status.FAILED, ex.getMessage());
        }
    }

    private static void restoreTimes(Path path, BasicFileAttributes before) throws IOException {
        // -P keeps the modification date - the creation date is restored here in case ExifTool doesn't
        Files.getFileAttributeView(path, BasicFileAttributeView.class)
                .setTimes(before.lastModifiedTime(), null, before.creationTime());
    }
}

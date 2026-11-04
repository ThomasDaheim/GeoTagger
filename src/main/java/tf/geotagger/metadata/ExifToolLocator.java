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

import java.io.File;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import tf.exiftool.ExifTool;
import tf.exiftool.ExifToolException;

/**
 * Finds ExifTool and checks that a configured executable really is ExifTool.
 */
public final class ExifToolLocator {
    // a wrong executable (e.g. any other program) must not block the preferences dialog for long
    private static final Duration CHECK_TIMEOUT = Duration.ofSeconds(20);

    private ExifToolLocator() {
    }

    /**
     * @return exiftool(.exe) from the first PATH entry that contains it
     */
    public static Optional<Path> findOnPath() {
        return findOnPath(System.getenv("PATH"));
    }

    static Optional<Path> findOnPath(String pathVariable) {
        if (pathVariable == null) {
            return Optional.empty();
        }
        for (String dir : pathVariable.split(File.pathSeparator)) {
            if (dir.isBlank()) {
                continue;
            }
            for (String name : List.of("exiftool.exe", "exiftool")) {
                try {
                    final Path candidate = Path.of(dir.trim().replace("\"", ""), name);
                    if (Files.isRegularFile(candidate)) {
                        return Optional.of(candidate);
                    }
                } catch (InvalidPathException ex) {
                    // broken PATH entry - ignore
                }
            }
        }
        return Optional.empty();
    }

    /**
     * @param executable ExifTool executable to check
     * @return the version reported by ExifTool, e.g. "13.38"
     * @throws ExifToolException if the file doesn't exist or doesn't answer like ExifTool
     */
    public static String version(Path executable) {
        if (!Files.isRegularFile(executable)) {
            throw new ExifToolException("ExifTool not found: " + executable);
        }
        try (ExifTool exifTool = new ExifTool(executable, CHECK_TIMEOUT)) {
            return exifTool.version();
        }
    }
}

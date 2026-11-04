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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tf.exiftool.ExifToolException;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExifToolLocatorTest {
    @TempDir
    Path dir;

    @Test
    void findsExecutableOnPath() throws IOException {
        final Path exifTool = Files.createFile(dir.resolve("exiftool.exe"));
        final String path = "C:\\does\\not\\exist" + File.pathSeparator + " " + File.pathSeparator + dir;

        assertEquals(Optional.of(exifTool), ExifToolLocator.findOnPath(path));
    }

    @Test
    void nothingFoundWithoutExecutable() {
        assertTrue(ExifToolLocator.findOnPath(dir.toString()).isEmpty());
        assertTrue(ExifToolLocator.findOnPath(null).isEmpty());
    }

    @Test
    void versionOfRealExifTool() {
        assertTrue(ExifToolLocator.version(ExifToolTestSupport.require()).matches("\\d+\\.\\d+.*"));
    }

    @Test
    void missingExecutableFails() {
        assertThrows(ExifToolException.class, () -> ExifToolLocator.version(dir.resolve("exiftool.exe")));
    }

    @Test
    void otherFileFails() throws IOException {
        final Path text = Files.writeString(dir.resolve("notiz.txt"), "no program");
        assertThrows(ExifToolException.class, () -> ExifToolLocator.version(text));
    }
}

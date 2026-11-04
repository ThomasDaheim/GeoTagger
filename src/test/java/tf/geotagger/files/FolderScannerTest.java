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
package tf.geotagger.files;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FolderScannerTest {
    @TempDir
    Path dir;

    @BeforeEach
    void createFiles() throws IOException {
        for (String name : List.of("a.jpg", "b.CR3", "c.mov", "d.gpx", "e.txt", "f.jpg_original", "g.xmp")) {
            Files.createFile(dir.resolve(name));
        }
        Files.createDirectory(dir.resolve("sub"));
        Files.createFile(dir.resolve("sub").resolve("h.jpg"));
        Files.createFile(dir.resolve("sub").resolve("i.GPX"));
    }

    @Test
    void findsMediaAndGpxFilesOfTheFolder() throws IOException {
        final FolderScanner.ScanResult result = FolderScanner.scan(dir, false);

        assertEquals(List.of(dir.resolve("a.jpg"), dir.resolve("b.CR3"), dir.resolve("c.mov")), result.mediaFiles());
        assertEquals(List.of(dir.resolve("d.gpx")), result.gpxFiles());
    }

    @Test
    void includesSubfoldersOnRequest() throws IOException {
        final FolderScanner.ScanResult result = FolderScanner.scan(dir, true);

        assertEquals(List.of(dir.resolve("a.jpg"), dir.resolve("b.CR3"), dir.resolve("c.mov"),
                dir.resolve("sub").resolve("h.jpg")), result.mediaFiles());
        assertEquals(List.of(dir.resolve("d.gpx"), dir.resolve("sub").resolve("i.GPX")), result.gpxFiles());
    }

    @Test
    void missingFolderFails() {
        assertThrows(IOException.class, () -> FolderScanner.scan(dir.resolve("gibt-es-nicht"), false));
    }
}

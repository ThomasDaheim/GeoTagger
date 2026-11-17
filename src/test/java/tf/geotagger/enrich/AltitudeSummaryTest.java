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
package tf.geotagger.enrich;

import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import tf.geotagger.model.MediaFile;
import tf.geotagger.model.MediaType;
import tf.geotagger.model.Snapshot;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AltitudeSummaryTest {
    private static List<MediaFile> files(String prefix, int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> new MediaFile(Path.of("C:/fotos/" + prefix + i + ".jpg"), MediaType.IMAGE,
                        new Snapshot(null, FileTime.fromMillis(0), Map.of())))
                .toList();
    }

    private static AltitudeRun run(int found, int notFound, int failed) {
        final Map<MediaFile, Double> altitudes = new LinkedHashMap<>();
        files("found", found).forEach(file -> altitudes.put(file, 300.0));
        return new AltitudeRun(altitudes, files("none", notFound), files("failed", failed), failed > 0 ? "quota" : null);
    }

    @Test
    void countsFoundNotFoundAndFailed() {
        assertEquals("Altitude found for 3 files, 1 without result, 2 failed", AltitudeSummary.text(run(3, 1, 2)));
        assertTrue(AltitudeSummary.hasProblems(run(3, 1, 2)));
    }

    @Test
    void singularAndZeroParts() {
        assertEquals("Altitude found for 1 file", AltitudeSummary.text(run(1, 0, 0)));
        assertFalse(AltitudeSummary.hasProblems(run(1, 2, 0)));
    }
}

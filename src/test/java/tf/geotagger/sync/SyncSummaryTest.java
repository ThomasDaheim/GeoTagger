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
package tf.geotagger.sync;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import tf.geotagger.sync.SyncResult.Status;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SyncSummaryTest {
    private static List<SyncResult> results(int matched, int largeGap, int noMatch, int noTime) {
        final List<SyncResult> results = new ArrayList<>();
        add(results, Status.MATCHED, matched);
        add(results, Status.LARGE_GAP, largeGap);
        add(results, Status.NO_MATCH, noMatch);
        add(results, Status.NO_CAPTURE_TIME, noTime);
        return results;
    }

    private static void add(List<SyncResult> results, Status status, int count) {
        for (int i = 0; i < count; i++) {
            results.add(new SyncResult(TestMedia.image(null), status, null, null, null, null, null));
        }
    }

    @Test
    void countsEveryStatus() {
        assertEquals("12 matched, 3 with large gap, 5 without match, 1 without capture time - 2 skipped (they have coordinates)",
                SyncSummary.text(results(12, 3, 5, 1), 2));
    }

    @Test
    void zeroPartsAreLeftOut() {
        assertEquals("0 matched, 2 without match", SyncSummary.text(results(0, 0, 2, 0), 0));
        assertEquals("No files to sync", SyncSummary.text(List.of(), 0));
        assertEquals("No files to sync - 3 skipped (they have coordinates)", SyncSummary.text(List.of(), 3));
    }

    @Test
    void gapTexts() {
        assertEquals("45 s", SyncSummary.gap(Duration.ofSeconds(45)));
        assertEquals("12 min 5 s", SyncSummary.gap(Duration.ofSeconds(12 * 60 + 5)));
        assertEquals("5 min", SyncSummary.gap(Duration.ofMinutes(5)));
        assertEquals("2 h 3 min", SyncSummary.gap(Duration.ofMinutes(123)));
        assertEquals("3 d 4 h", SyncSummary.gap(Duration.ofHours(76)));
        assertEquals("0 s", SyncSummary.gap(Duration.ZERO));
        assertEquals("", SyncSummary.gap(null));
    }

    @Test
    void singularFile() {
        assertEquals("Track sync: positions for 1 file", SyncSummary.applied(1));
        assertEquals("Track sync: positions for 12 files", SyncSummary.applied(12));
    }
}

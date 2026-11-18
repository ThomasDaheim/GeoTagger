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
import java.util.List;
import tf.geotagger.sync.SyncResult.Status;

/**
 * Texts of the sync dialog for the user.
 */
public final class SyncSummary {
    private SyncSummary() {
    }

    /**
     * @param results the rows of the preview
     * @param skipped selected files left out because they have coordinates and aren't overwritten
     * @return e.g. "12 matched, 3 with large gap, 5 without match, 1 without capture time - 2 skipped (they have
     * coordinates)" - parts with 0 are left out, except the first
     */
    public static String text(List<SyncResult> results, int skipped) {
        final StringBuilder text = new StringBuilder();
        if (results.isEmpty()) {
            text.append("No files to sync");
        } else {
            text.append(count(results, Status.MATCHED)).append(" matched");
            part(text, count(results, Status.LARGE_GAP), "with large gap");
            part(text, count(results, Status.NO_MATCH), "without match");
            part(text, count(results, Status.NO_CAPTURE_TIME), "without capture time");
        }
        if (skipped > 0) {
            text.append(" - ").append(skipped).append(" skipped (they have coordinates)");
        }
        return text.toString();
    }

    /**
     * @param gap time to the nearest track point
     * @return e.g. "45 s", "12 min 5 s", "2 h 3 min", "3 d 4 h" - empty for null
     */
    public static String gap(Duration gap) {
        if (gap == null) {
            return "";
        }
        final long seconds = gap.abs().toSeconds();
        if (seconds < 60) {
            return seconds + " s";
        }
        if (seconds < 3600) {
            return twoUnits(seconds / 60, "min", seconds % 60, "s");
        }
        if (seconds < 86400) {
            return twoUnits(seconds / 3600, "h", seconds % 3600 / 60, "min");
        }
        return twoUnits(seconds / 86400, "d", seconds % 86400 / 3600, "h");
    }

    /**
     * @param count files that got a position
     * @return e.g. "Track sync: positions for 12 files"
     */
    public static String applied(int count) {
        return "Track sync: positions for " + count + (count == 1 ? " file" : " files");
    }

    private static long count(List<SyncResult> results, Status status) {
        return results.stream().filter(result -> result.status() == status).count();
    }

    private static void part(StringBuilder text, long count, String label) {
        if (count > 0) {
            text.append(", ").append(count).append(' ').append(label);
        }
    }

    // the second unit only if it isn't 0
    private static String twoUnits(long first, String firstUnit, long second, String secondUnit) {
        return first + " " + firstUnit + (second == 0 ? "" : " " + second + " " + secondUnit);
    }
}

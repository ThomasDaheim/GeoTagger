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
import java.time.Instant;
import java.util.List;
import tf.geotagger.model.MediaFile;
import tf.geotagger.sync.SyncResult.Status;
import tf.geotagger.track.TrackPoint;
import tf.geotagger.track.TrackTimeline;

/**
 * Assigns positions from GPX tracks to files by their capture time - see spec, section "Track-Sync-Dialog",
 * "Algorithmus". Pure logic, the files are not changed by match().
 */
public final class TrackSync {
    private TrackSync() {
    }

    public static List<SyncResult> match(List<MediaFile> files, TrackTimeline timeline, SyncParameters parameters) {
        return files.stream().map(file -> match(file, timeline, parameters)).toList();
    }

    private static SyncResult match(MediaFile file, TrackTimeline timeline, SyncParameters parameters) {
        if (file.captureTime() == null) {
            return new SyncResult(file, Status.NO_CAPTURE_TIME, null, null, null, null, null);
        }
        final Instant time = CameraClock.toUtc(file.captureTime(), parameters.cameraZone(), parameters.cameraOffset());

        final TrackPoint before = timeline.atOrBefore(time);
        final TrackPoint after = parameters.onlyPastPoints() ? null : timeline.atOrAfter(time);
        final Duration gapBefore = before == null ? null : Duration.between(before.time(), time);
        final Duration gapAfter = after == null ? null : Duration.between(time, after.time());
        final boolean beforeUsable = gapBefore != null && gapBefore.compareTo(parameters.maxGap()) <= 0;
        final boolean afterUsable = gapAfter != null && gapAfter.compareTo(parameters.maxGap()) <= 0;
        final Duration gap = shorter(gapBefore, gapAfter);

        if (!beforeUsable && !afterUsable) {
            return new SyncResult(file, Status.NO_MATCH, time, null, null, null, gap);
        }
        final Status status = gap.compareTo(parameters.maxGap().dividedBy(2)) > 0 ? Status.LARGE_GAP : Status.MATCHED;

        if (parameters.interpolate() && beforeUsable && afterUsable && !before.time().equals(after.time())) {
            final double fraction = (double) gapBefore.toMillis() / Duration.between(before.time(), after.time()).toMillis();
            return new SyncResult(file, status, time,
                    interpolate(before.latitude(), after.latitude(), fraction),
                    interpolate(before.longitude(), after.longitude(), fraction),
                    elevation(before.elevation(), after.elevation(), fraction),
                    gap);
        }

        final TrackPoint nearest = !afterUsable || (beforeUsable && gapBefore.compareTo(gapAfter) <= 0) ? before : after;
        return new SyncResult(file, status, time, nearest.latitude(), nearest.longitude(), nearest.elevation(), gap);
    }

    private static Duration shorter(Duration first, Duration second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        return first.compareTo(second) <= 0 ? first : second;
    }

    private static double interpolate(double from, double to, double fraction) {
        return from + (to - from) * fraction;
    }

    private static Double elevation(Double from, Double to, double fraction) {
        if (from == null) {
            return to;
        }
        if (to == null) {
            return from;
        }
        return interpolate(from, to, fraction);
    }
}

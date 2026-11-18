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
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tf.geotagger.model.Field;
import tf.geotagger.model.MediaFile;
import tf.geotagger.model.MediaType;
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

    /**
     * Files the sync should handle: candidates are all files ("Ohne Koordinaten") or the selection ("Auswahl").
     *
     * @param candidates all files or the selected ones
     * @param overwriteExisting include files that already have coordinates
     * @return the candidates the sync should handle, in their order
     */
    public static List<MediaFile> eligible(List<MediaFile> candidates, boolean overwriteExisting) {
        return candidates.stream().filter(file -> overwriteExisting || !file.hasCoordinates()).toList();
    }

    /**
     * Files the sync dialog handles - see spec, section "Track-Sync-Dialog", "Dateien".
     *
     * @param listed the files of the list (filtered)
     * @param selected the files the user selected
     * @param scope "without coordinates" or "selection"
     * @param overwrite with "selection": include selected files that already have coordinates
     * @return the files to match, in their order
     */
    public static List<MediaFile> scope(List<MediaFile> listed, List<MediaFile> selected, FileScope scope, boolean overwrite) {
        return switch (scope) {
            case WITHOUT_COORDINATES -> eligible(listed, false);
            case SELECTION -> eligible(selected, overwrite);
        };
    }

    /**
     * The values the results with a position give - for one undo step; the files themselves aren't changed.
     *
     * @param results results of match()
     * @param parameters the parameters used for match() - elevation and time zone options
     * @return new values per file with a position, in the order of the results
     */
    public static Map<MediaFile, Map<Field, Object>> changes(List<SyncResult> results, SyncParameters parameters) {
        final Map<MediaFile, Map<Field, Object>> changes = new LinkedHashMap<>();
        for (SyncResult result : results) {
            if (!result.hasPosition()) {
                continue;
            }
            final MediaFile file = result.file();
            final Map<Field, Object> values = new EnumMap<>(Field.class);
            values.put(Field.LATITUDE, result.latitude());
            values.put(Field.LONGITUDE, result.longitude());
            if (parameters.takeElevation() && result.elevation() != null) {
                values.put(Field.ALTITUDE, result.elevation());
            } else if (parameters.takeElevation() && file.altitude() != null) {
                // the track has no altitude here - the old one belongs to the old position (final review of plan 6)
                values.put(Field.ALTITUDE, null);
            }
            // videos get no time zone - see spec, section "Felder"; a file with a zone of its own keeps it (user
            // decision 18.11.2026)
            if (parameters.writeTimeZone() && file.mediaType() == MediaType.IMAGE
                    && CameraClock.isCameraTime(file.captureTime(), file.timeOffset())) {
                values.put(Field.TIME_OFFSET, parameters.cameraZone().getRules().getOffset(result.correctedTime()));
            }
            changes.put(file, values);
        }
        return changes;
    }

    private static SyncResult match(MediaFile file, TrackTimeline timeline, SyncParameters parameters) {
        if (file.captureTime() == null) {
            return new SyncResult(file, Status.NO_CAPTURE_TIME, null, null, null, null, null);
        }
        // zone and offset only for the camera's files - phone files keep their own time (user decision 18.11.2026)
        final Instant time = CameraClock.toUtc(file.captureTime(), file.timeOffset(), parameters.cameraZone(), parameters.cameraOffset());

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

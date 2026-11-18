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
package tf.geotagger.main;

import java.time.Duration;
import java.time.ZoneId;
import tf.geotagger.sync.SyncParameters;
import tf.geotagger.sync.TrackSource;

/**
 * The last used parameters of the sync dialog - all but the camera zone, which comes from the files each time (user
 * decision 18.11.2026).
 *
 * @param source where the track points come from
 * @param gpxFile the GPX file of the source "GPX file"
 * @param gpxFolder the folder of the source "GPX folder"
 * @param offset additional deviation of the camera clock, positive = camera ahead
 * @param maxGap maximum time to the nearest track point
 * @param interpolate interpolate between the track points instead of taking the nearest one
 * @param onlyPast ignore track points after the capture time
 * @param takeAltitude take the altitude of the track
 * @param writeTimeZone write the camera zone as OffsetTimeOriginal
 * @param overwrite with "Selection": also files that have coordinates
 */
record SyncSettings(TrackSource source, String gpxFile, String gpxFolder, Duration offset, Duration maxGap,
        boolean interpolate, boolean onlyPast, boolean takeAltitude, boolean writeTimeZone, boolean overwrite) {

    /**
     * @return the stored parameters - the spec's defaults for those never stored; a maximum gap of 0 or less gives
     * the default
     */
    static SyncSettings load() {
        final Long offsetSeconds = GeoTaggerPreferences.SYNC_OFFSET_SECONDS.getAsType();
        final Long maxGapMinutes = GeoTaggerPreferences.SYNC_MAX_GAP_MINUTES.getAsType();
        final Boolean interpolate = GeoTaggerPreferences.SYNC_INTERPOLATE.getAsType();
        final Boolean onlyPast = GeoTaggerPreferences.SYNC_ONLY_PAST.getAsType();
        final Boolean takeAltitude = GeoTaggerPreferences.SYNC_TAKE_ALTITUDE.getAsType();
        final Boolean writeTimeZone = GeoTaggerPreferences.SYNC_WRITE_TIME_ZONE.getAsType();
        final Boolean overwrite = GeoTaggerPreferences.SYNC_OVERWRITE.getAsType();
        return new SyncSettings(GeoTaggerPreferences.SYNC_TRACK_SOURCE.getAsType(),
                GeoTaggerPreferences.SYNC_GPX_FILE.getAsString(), GeoTaggerPreferences.SYNC_GPX_FOLDER.getAsString(),
                Duration.ofSeconds(offsetSeconds),
                maxGapMinutes > 0 ? Duration.ofMinutes(maxGapMinutes) : SyncParameters.DEFAULT_MAX_GAP,
                interpolate, onlyPast, takeAltitude, writeTimeZone, overwrite);
    }

    /**
     * Stores the parameters for the next time.
     */
    void store() {
        GeoTaggerPreferences.SYNC_TRACK_SOURCE.put(source);
        GeoTaggerPreferences.SYNC_GPX_FILE.put(gpxFile);
        GeoTaggerPreferences.SYNC_GPX_FOLDER.put(gpxFolder);
        GeoTaggerPreferences.SYNC_OFFSET_SECONDS.put(offset.toSeconds());
        GeoTaggerPreferences.SYNC_MAX_GAP_MINUTES.put(maxGap.toMinutes());
        GeoTaggerPreferences.SYNC_INTERPOLATE.put(interpolate);
        GeoTaggerPreferences.SYNC_ONLY_PAST.put(onlyPast);
        GeoTaggerPreferences.SYNC_TAKE_ALTITUDE.put(takeAltitude);
        GeoTaggerPreferences.SYNC_WRITE_TIME_ZONE.put(writeTimeZone);
        GeoTaggerPreferences.SYNC_OVERWRITE.put(overwrite);
    }

    /**
     * @param zone the camera zone chosen in the dialog
     * @return the parameters of the sync
     */
    SyncParameters parameters(ZoneId zone) {
        return new SyncParameters(zone, offset, maxGap, interpolate, onlyPast, takeAltitude, writeTimeZone);
    }
}

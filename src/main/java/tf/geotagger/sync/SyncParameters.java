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
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import tf.geotagger.model.MediaFile;

/**
 * Parameters of the track sync dialog - see spec, section "Track-Sync-Dialog".
 *
 * @param cameraZone zone the camera clock was set to
 * @param cameraOffset additional deviation of the camera clock, positive = camera ahead
 * @param maxGap maximum time between capture time and the nearest track point
 * @param interpolate interpolate between the neighbouring track points instead of taking the nearest one
 * @param onlyPastPoints only use track points before the capture time
 * @param takeElevation set the elevation of the track for files that get coordinates
 * @param writeTimeZone set the camera zone as OffsetTimeOriginal for images that get coordinates
 */
public record SyncParameters(ZoneId cameraZone, Duration cameraOffset, Duration maxGap, boolean interpolate,
        boolean onlyPastPoints, boolean takeElevation, boolean writeTimeZone) {
    public static final Duration DEFAULT_MAX_GAP = Duration.ofMinutes(30);

    public SyncParameters {
        Objects.requireNonNull(cameraZone);
        Objects.requireNonNull(cameraOffset);
        Objects.requireNonNull(maxGap);
        if (maxGap.isNegative() || maxGap.isZero()) {
            throw new IllegalArgumentException("maxGap must be positive: " + maxGap);
        }
    }

    public static SyncParameters defaults(ZoneId cameraZone) {
        return new SyncParameters(cameraZone, Duration.ZERO, DEFAULT_MAX_GAP, true, false, true, false);
    }

    /**
     * Most frequent OffsetTimeOriginal of the files - fallback (usually the system zone) if none has one.
     */
    public static ZoneId suggestedCameraZone(List<MediaFile> files, ZoneId fallback) {
        final Map<ZoneOffset, Long> counts = files.stream()
                .map(MediaFile::timeOffset)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        return counts.entrySet().stream()
                // ties: deterministic by offset
                .max(Map.Entry.<ZoneOffset, Long>comparingByValue().thenComparing(Map.Entry.<ZoneOffset, Long>comparingByKey()))
                .<ZoneId>map(Map.Entry::getKey)
                .orElse(fallback);
    }
}

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

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.function.BiConsumer;
import tf.geo.LatLon;
import tf.geo.service.ElevationProvider;
import tf.geo.service.GeoServiceException;
import tf.geo.service.OpenRouteServiceElevationProvider;
import tf.geotagger.model.MediaFile;

/**
 * Looks up the altitudes of files - each position once (no grid: the altitude changes within meters), first from the
 * local SRTM tiles, the gaps from openrouteservice in blocks with a pause between them and retries with growing
 * waits. Runs in a background thread.
 */
public final class AltitudeEnricher {
    // waits before the 1st, 2nd and 3rd retry of a temporary problem
    private static final List<Duration> RETRY_WAITS = List.of(Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(4));

    private final ElevationProvider srtm;
    private final ElevationProvider ors;
    private final AltitudeSource source;
    private final Sleeper sleeper;
    private final Duration orsInterval;

    /**
     * @param srtm local SRTM tiles - may be null if the source doesn't use them
     * @param ors openrouteservice - may be null if the source doesn't use it
     * @param source which of the two to ask
     * @param sleeper waits between requests
     * @param orsInterval pause between two requests to openrouteservice
     */
    public AltitudeEnricher(ElevationProvider srtm, ElevationProvider ors, AltitudeSource source, Sleeper sleeper,
            Duration orsInterval) {
        this.source = Objects.requireNonNull(source);
        this.srtm = source.usesSrtm() ? Objects.requireNonNull(srtm) : null;
        this.ors = source.usesOrs() ? Objects.requireNonNull(ors) : null;
        this.sleeper = Objects.requireNonNull(sleeper);
        this.orsInterval = Objects.requireNonNull(orsInterval);
    }

    /**
     * @param files files to look up - those without coordinates are skipped
     * @param progress called with (positions done, positions in total) after SRTM and after each openrouteservice block
     * @return altitudes found, files without altitude and failed files
     * @throws InterruptedException if the thread was interrupted while waiting
     */
    public AltitudeRun run(List<MediaFile> files, BiConsumer<Integer, Integer> progress) throws InterruptedException {
        final Map<LatLon, List<MediaFile>> positions = new LinkedHashMap<>();
        for (MediaFile file : files) {
            if (file.hasCoordinates()) {
                positions.computeIfAbsent(new LatLon(file.latitude(), file.longitude()), position -> new ArrayList<>()).add(file);
            }
        }
        final int total = positions.size();
        final Map<LatLon, Double> found = new LinkedHashMap<>();
        List<LatLon> open = new ArrayList<>(positions.keySet());
        final List<LatLon> failedPositions = new ArrayList<>();
        String problem = null;

        if (source.usesSrtm() && !open.isEmpty()) {
            // local files - a failure here is a broken tile folder, not worth retrying
            try {
                open = collect(open, srtm.elevations(open), found);
            } catch (GeoServiceException ex) {
                problem = ex.getMessage();
            }
            progress.accept(found.size(), total);
        }

        if (source.usesOrs() && !open.isEmpty()) {
            final List<LatLon> missing = new ArrayList<>();
            int done = found.size();
            boolean stopped = false;
            for (int start = 0; start < open.size(); start += OpenRouteServiceElevationProvider.BLOCK_SIZE) {
                final List<LatLon> block = open.subList(start, Math.min(start + OpenRouteServiceElevationProvider.BLOCK_SIZE, open.size()));
                if (stopped) {
                    failedPositions.addAll(block);
                    continue;
                }
                if (start > 0) {
                    sleeper.sleep(orsInterval);
                }
                try {
                    missing.addAll(collect(block, request(block), found));
                } catch (GeoServiceException ex) {
                    failedPositions.addAll(block);
                    if (problem == null) {
                        problem = ex.getMessage();
                    }
                    // further blocks would fail the same way - quota, key, or a problem that lasted through all
                    // retries
                    stopped = ex.kind() != GeoServiceException.Kind.FAILED;
                }
                done += block.size();
                progress.accept(done, total);
            }
            open = missing;
        } else if (problem != null) {
            // SRTM failed and nothing else to ask
            failedPositions.addAll(open);
            open = List.of();
        }

        final Map<MediaFile, Double> altitudes = new LinkedHashMap<>();
        found.forEach((position, altitude) -> positions.get(position).forEach(file -> altitudes.put(file, altitude)));
        final List<MediaFile> notFound = new ArrayList<>();
        open.forEach(position -> notFound.addAll(positions.get(position)));
        final List<MediaFile> failed = new ArrayList<>();
        failedPositions.forEach(position -> failed.addAll(positions.get(position)));
        return new AltitudeRun(altitudes, notFound, failed, problem);
    }

    // with retries of temporary problems
    private List<OptionalDouble> request(List<LatLon> block) throws GeoServiceException, InterruptedException {
        for (int retry = 0; ; retry++) {
            try {
                return ors.elevations(block);
            } catch (GeoServiceException ex) {
                if (ex.kind() != GeoServiceException.Kind.RETRYABLE || retry >= RETRY_WAITS.size()) {
                    throw ex;
                }
                sleeper.sleep(RETRY_WAITS.get(retry));
            }
        }
    }

    // the values found go into found (rounded to 0.1 m), the positions without value are returned
    private static List<LatLon> collect(List<LatLon> asked, List<OptionalDouble> values, Map<LatLon, Double> found) {
        final List<LatLon> missing = new ArrayList<>();
        for (int i = 0; i < asked.size(); i++) {
            final OptionalDouble value = values.get(i);
            if (value.isPresent()) {
                found.put(asked.get(i), Math.round(value.getAsDouble() * 10) / 10.0);
            } else {
                missing.add(asked.get(i));
            }
        }
        return missing;
    }
}

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
import java.util.function.BiConsumer;
import tf.geo.LatLon;
import tf.geo.service.GeoServiceException;
import tf.geo.service.NearbyPlace;
import tf.geo.service.Place;
import tf.geo.service.ReverseGeocoder;
import tf.geotagger.model.MediaFile;

/**
 * Looks up the places of files - one request per grid cell, cached, with a pause between the requests and retries
 * with growing waits, see spec "Datenfluss 3. Anreichern" and "Fehlerbehandlung". Runs in a background thread.
 */
public final class PlaceEnricher {
    // waits before the 1st, 2nd and 3rd retry of a temporary problem
    private static final List<Duration> RETRY_WAITS = List.of(Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(4));

    private final ReverseGeocoder geocoder;
    private final Map<GridCell, List<NearbyPlace>> cache;
    private final Sleeper sleeper;
    private final double choiceRadiusKm;

    /**
     * @param geocoder service to ask
     * @param cache answers of earlier runs with the same service and language (places nearby, empty = nothing found) -
     * filled by this run
     * @param sleeper waits between requests
     * @param choiceRadiusKm more than one place within this distance: the user chooses - 0 never asks
     */
    public PlaceEnricher(ReverseGeocoder geocoder, Map<GridCell, List<NearbyPlace>> cache, Sleeper sleeper, double choiceRadiusKm) {
        this.geocoder = Objects.requireNonNull(geocoder);
        this.cache = Objects.requireNonNull(cache);
        this.sleeper = Objects.requireNonNull(sleeper);
        this.choiceRadiusKm = choiceRadiusKm;
    }

    /**
     * @param files files to look up - those without coordinates are skipped
     * @param progress called with (cells done, cells in total) after each cell
     * @return places found, files without place and failed files
     * @throws InterruptedException if the thread was interrupted while waiting
     */
    public PlaceRun run(List<MediaFile> files, BiConsumer<Integer, Integer> progress) throws InterruptedException {
        final Map<GridCell, List<MediaFile>> cells = new LinkedHashMap<>();
        for (MediaFile file : files) {
            if (file.hasCoordinates()) {
                cells.computeIfAbsent(GridCell.of(new LatLon(file.latitude(), file.longitude())), cell -> new ArrayList<>()).add(file);
            }
        }

        final Map<MediaFile, Place> places = new LinkedHashMap<>();
        final Map<MediaFile, List<NearbyPlace>> choices = new LinkedHashMap<>();
        final List<MediaFile> notFound = new ArrayList<>();
        final List<MediaFile> failed = new ArrayList<>();
        String problem = null;
        boolean stopped = false;
        boolean requested = false;
        int done = 0;
        for (Map.Entry<GridCell, List<MediaFile>> entry : cells.entrySet()) {
            final GridCell cell = entry.getKey();
            final List<MediaFile> cellFiles = entry.getValue();
            if (stopped) {
                failed.addAll(cellFiles);
            } else if (cache.containsKey(cell)) {
                add(cache.get(cell), cellFiles, places, choices, notFound);
            } else {
                if (requested) {
                    sleeper.sleep(geocoder.minRequestInterval());
                }
                requested = true;
                try {
                    final List<NearbyPlace> nearby = request(cell);
                    cache.put(cell, nearby);
                    add(nearby, cellFiles, places, choices, notFound);
                } catch (GeoServiceException ex) {
                    failed.addAll(cellFiles);
                    if (problem == null) {
                        problem = ex.getMessage();
                    }
                    // further requests would fail the same way - also a problem that lasted through all retries (e.g.
                    // offline): otherwise each cell would cost 4 requests and 7 s while the window is locked
                    stopped = ex.kind() != GeoServiceException.Kind.FAILED;
                }
            }
            progress.accept(++done, cells.size());
        }
        return new PlaceRun(places, notFound, failed, problem, choices);
    }

    // with retries of temporary problems
    private List<NearbyPlace> request(GridCell cell) throws GeoServiceException, InterruptedException {
        for (int retry = 0; ; retry++) {
            try {
                return geocoder.nearby(cell.center());
            } catch (GeoServiceException ex) {
                if (ex.kind() != GeoServiceException.Kind.RETRYABLE || retry >= RETRY_WAITS.size()) {
                    throw ex;
                }
                sleeper.sleep(RETRY_WAITS.get(retry));
            }
        }
    }

    // the best answer for all files of the cell - and the places to choose from, if more than one is near enough
    private void add(List<NearbyPlace> nearby, List<MediaFile> files, Map<MediaFile, Place> places,
            Map<MediaFile, List<NearbyPlace>> choices, List<MediaFile> notFound) {
        if (nearby.isEmpty()) {
            notFound.addAll(files);
            return;
        }
        files.forEach(file -> places.put(file, nearby.get(0).place()));
        final List<NearbyPlace> close = choiceRadiusKm <= 0 ? List.of()
                : nearby.stream().filter(place -> place.distanceKm() <= choiceRadiusKm).toList();
        if (close.size() > 1) {
            final List<NearbyPlace> offered = withCitiesAlone(close, nearby);
            files.forEach(file -> choices.put(file, offered));
        }
    }

    // each city of the choice also without district - its centre is often further away than the radius
    private static List<NearbyPlace> withCitiesAlone(List<NearbyPlace> close, List<NearbyPlace> nearby) {
        final List<NearbyPlace> offered = new ArrayList<>(close);
        for (NearbyPlace entry : close) {
            if (entry.place().city() == null || entry.place().sublocation() == null) {
                continue;
            }
            final Place alone = new Place(entry.place().countryCode(), entry.place().country(), entry.place().state(),
                    entry.place().city(), null);
            if (offered.stream().noneMatch(known -> known.place().equals(alone))) {
                // the service's entry of the city with its distance, otherwise made up from the district
                offered.add(nearby.stream().filter(other -> other.place().equals(alone)).findFirst()
                        .orElse(new NearbyPlace(alone, entry.distanceKm())));
            }
        }
        return List.copyOf(offered);
    }
}

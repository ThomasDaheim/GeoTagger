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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import tf.geo.LatLon;
import tf.geo.service.GeoServiceException;
import tf.geo.service.NearbyPlace;
import tf.geo.service.Place;
import tf.geo.service.ReverseGeocoder;

/**
 * Reverse geocoder for the tests: answers in the given order (the last one repeats), counts the requests.
 * Also used by the UI tests of "Find place".
 */
public final class FakeGeocoder implements ReverseGeocoder {
    private final Deque<Object> answers = new ArrayDeque<>();
    private final List<LatLon> requests = new ArrayList<>();
    private final Duration interval;
    private Object last;

    /**
     * @param interval pause it asks for between two requests
     * @param answers in this order: a Place, a List of NearbyPlace (several places nearby), Optional.empty() for
     * "nothing found" or a GeoServiceException to throw
     */
    public FakeGeocoder(Duration interval, Object... answers) {
        this.interval = interval;
        this.answers.addAll(List.of(answers));
    }

    /**
     * @return the requested positions so far
     */
    public List<LatLon> requests() {
        return requests;
    }

    @Override
    public Optional<Place> reverse(LatLon position) throws GeoServiceException {
        return nearby(position).stream().findFirst().map(NearbyPlace::place);
    }

    @Override
    public synchronized List<NearbyPlace> nearby(LatLon position) throws GeoServiceException {
        requests.add(position);
        if (!answers.isEmpty()) {
            last = answers.poll();
        }
        if (last instanceof GeoServiceException ex) {
            throw ex;
        }
        if (last instanceof Place place) {
            return List.of(new NearbyPlace(place, 0));
        }
        if (last instanceof List<?> list) {
            return list.stream().map(NearbyPlace.class::cast).toList();
        }
        return List.of();
    }

    @Override
    public Duration minRequestInterval() {
        return interval;
    }

    @Override
    public String name() {
        return "Fake";
    }
}

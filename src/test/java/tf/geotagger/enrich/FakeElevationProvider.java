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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.OptionalDouble;
import java.util.function.Function;
import tf.geo.LatLon;
import tf.geo.service.ElevationProvider;
import tf.geo.service.GeoServiceException;

/**
 * Elevations for the tests: a function per position, optionally some exceptions first; the calls are kept.
 * Also used by the UI tests of "Find altitude".
 */
public final class FakeElevationProvider implements ElevationProvider {
    private final String name;
    private final Function<LatLon, OptionalDouble> values;
    private final Deque<GeoServiceException> failures = new ArrayDeque<>();
    private final List<List<LatLon>> calls = new ArrayList<>();

    /**
     * @param name name of the provider
     * @param values elevation per position
     * @param failures thrown by the first calls, in this order
     */
    public FakeElevationProvider(String name, Function<LatLon, OptionalDouble> values, GeoServiceException... failures) {
        this.name = name;
        this.values = values;
        this.failures.addAll(List.of(failures));
    }

    /**
     * @return the positions of each call so far
     */
    public List<List<LatLon>> calls() {
        return calls;
    }

    @Override
    public synchronized List<OptionalDouble> elevations(List<LatLon> positions) throws GeoServiceException {
        calls.add(List.copyOf(positions));
        if (!failures.isEmpty()) {
            throw failures.poll();
        }
        return positions.stream().map(values).toList();
    }

    @Override
    public String name() {
        return name;
    }
}

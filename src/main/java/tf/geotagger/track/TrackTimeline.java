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
package tf.geotagger.track;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * All points of the tracks used for a sync, sorted by time - tracks may overlap or come in any order.
 */
public final class TrackTimeline {
    /**
     * @param failures GPX files that couldn't be read, with the reason - the other files are part of the timeline
     */
    public record LoadResult(TrackTimeline timeline, Map<Path, String> failures) {
        public LoadResult {
            failures = Map.copyOf(failures);
        }
    }

    private final List<TrackPoint> points;
    private final Instant[] times;

    public TrackTimeline(Collection<TrackPoint> points) {
        this.points = points.stream().sorted(Comparator.comparing(TrackPoint::time)).toList();
        this.times = this.points.stream().map(TrackPoint::time).toArray(Instant[]::new);
    }

    public static LoadResult load(List<Path> gpxFiles) {
        final List<TrackPoint> points = new ArrayList<>();
        final Map<Path, String> failures = new LinkedHashMap<>();
        for (Path gpx : gpxFiles) {
            try {
                points.addAll(GpxReader.read(gpx));
            } catch (IOException ex) {
                failures.put(gpx, ex.getMessage());
            }
        }
        return new LoadResult(new TrackTimeline(points), failures);
    }

    public int size() {
        return points.size();
    }

    public boolean isEmpty() {
        return points.isEmpty();
    }

    /**
     * Last point at or before time - null if the track starts later.
     */
    public TrackPoint atOrBefore(Instant time) {
        final int index = firstIndexAtOrAfter(time);
        if (index < times.length && times[index].equals(time)) {
            return points.get(index);
        }
        return index > 0 ? points.get(index - 1) : null;
    }

    /**
     * First point at or after time - null if the track ends earlier.
     */
    public TrackPoint atOrAfter(Instant time) {
        final int index = firstIndexAtOrAfter(time);
        return index < times.length ? points.get(index) : null;
    }

    private int firstIndexAtOrAfter(Instant time) {
        int low = 0;
        int high = times.length;
        while (low < high) {
            final int mid = (low + high) >>> 1;
            if (times[mid].isBefore(time)) {
                low = mid + 1;
            } else {
                high = mid;
            }
        }
        return low;
    }
}

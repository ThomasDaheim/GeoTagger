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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import tf.geo.LatLon;

/**
 * Rows of the point list below the tracks - like GeoSetter's list of track points.
 */
public final class TrackPoints {
    // mean earth radius (IUGG) in km
    private static final double EARTH_RADIUS_KM = 6371.0088;

    /**
     * One point of a track.
     *
     * @param number number of the point within its track, from 1
     * @param time time of the point, null if the file has none
     * @param position position of the point
     * @param distanceKm distance from the start of its track, over all its segments
     * @param elevation elevation in meters, null if the file has none
     */
    public record Row(int number, Instant time, LatLon position, double distanceKm, Double elevation) {
    }

    private TrackPoints() {
    }

    /**
     * @param tracks loaded tracks
     * @return the points of all segments of the tracks, in order
     */
    public static List<Row> of(List<GpxTrack> tracks) {
        final List<Row> rows = new ArrayList<>();
        for (GpxTrack track : tracks) {
            int number = 0;
            double distance = 0;
            LatLon previous = null;
            for (GpxLine line : track.lines()) {
                for (GpxPoint point : line.points()) {
                    if (previous != null) {
                        distance += distanceKm(previous, point.position());
                    }
                    previous = point.position();
                    rows.add(new Row(++number, point.time(), point.position(), distance, point.elevation()));
                }
            }
        }
        return rows;
    }

    // haversine - exact enough for the short steps between track points
    private static double distanceKm(LatLon from, LatLon to) {
        final double lat1 = Math.toRadians(from.latitude());
        final double lat2 = Math.toRadians(to.latitude());
        final double deltaLat = lat2 - lat1;
        final double deltaLon = Math.toRadians(to.longitude() - from.longitude());
        final double a = Math.pow(Math.sin(deltaLat / 2), 2) + Math.cos(lat1) * Math.cos(lat2) * Math.pow(Math.sin(deltaLon / 2), 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(a));
    }
}

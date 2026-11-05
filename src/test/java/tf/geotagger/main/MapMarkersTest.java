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

import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tf.geo.LatLon;
import tf.geo.map.MapMarker;
import tf.geo.map.MarkerColor;
import tf.geotagger.model.Field;
import tf.geotagger.model.MediaFile;
import tf.geotagger.model.MediaType;
import tf.geotagger.model.Snapshot;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapMarkersTest {
    private static MediaFile file(String name, Map<Field, Object> values) {
        return new MediaFile(Path.of("C:/fotos/" + name), MediaType.IMAGE, new Snapshot(null, FileTime.fromMillis(0), values));
    }

    @Test
    void onlyFilesWithValidCoordinates() {
        final MediaFile with = file("a.jpg", Map.of(Field.LATITUDE, 46.5, Field.LONGITUDE, 11.5));
        final MediaFile without = file("b.jpg", Map.of());
        // e.g. a broken file - it must not break the map
        final MediaFile invalid = file("c.jpg", Map.of(Field.LATITUDE, 95.0, Field.LONGITUDE, 11.5));
        final List<MapMarker> markers = MapMarkers.of(List.of(with, without, invalid), List.of());
        assertEquals(1, markers.size());
        assertEquals(new MapMarker(with.path().toString(), new LatLon(46.5, 11.5), "a.jpg", MarkerColor.BLUE, false), markers.get(0));
    }

    @Test
    void changedCoordinatesAreOrange() {
        final MediaFile moved = file("a.jpg", Map.of(Field.LATITUDE, 46.5, Field.LONGITUDE, 11.5));
        moved.set(Field.LATITUDE, 47.0);
        final MediaFile cityOnly = file("b.jpg", Map.of(Field.LATITUDE, 46.5, Field.LONGITUDE, 11.5));
        cityOnly.set(Field.CITY, "Bozen");
        final List<MapMarker> markers = MapMarkers.of(List.of(moved, cityOnly), List.of());
        assertEquals(MarkerColor.ORANGE, markers.get(0).color());
        assertEquals(MarkerColor.BLUE, markers.get(1).color());
    }

    @Test
    void selectedFilesAreRedAndSelected() {
        final MediaFile file = file("a.jpg", Map.of(Field.LATITUDE, 46.5, Field.LONGITUDE, 11.5));
        final MapMarker marker = MapMarkers.of(List.of(file), List.of(file)).get(0);
        assertEquals(MarkerColor.RED, marker.color());
        assertTrue(marker.selected());
    }
}
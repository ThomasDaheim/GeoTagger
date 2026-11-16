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

import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tf.geo.service.NearbyPlace;
import tf.geo.service.Place;
import tf.geotagger.model.MediaFile;
import tf.geotagger.model.MediaType;
import tf.geotagger.model.Snapshot;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PlaceChoicesTest {
    private static final List<NearbyPlace> BARCELONA = List.of(
            new NearbyPlace(new Place("ES", "Spanien", "Katalonien", "Barcelona", "Eixample"), 0.65),
            new NearbyPlace(new Place("ES", "Spanien", "Katalonien", "Barcelona", "el Raval"), 0.74));
    private static final List<NearbyPlace> ROME = List.of(
            new NearbyPlace(new Place("IT", "Italien", "Latium", "Rom", "Trastevere"), 0.3),
            new NearbyPlace(new Place("IT", "Italien", "Latium", "Rom", "Testaccio"), 0.6));

    private static MediaFile file(String name) {
        return new MediaFile(Path.of("C:/fotos/" + name), MediaType.IMAGE, new Snapshot(null, FileTime.fromMillis(0), Map.of()));
    }

    @Test
    void groupsKeepTheOrderOfTheRun() {
        final MediaFile a = file("a.jpg");
        final MediaFile b = file("b.jpg");
        final MediaFile c = file("c.jpg");
        final Map<MediaFile, List<NearbyPlace>> choices = new LinkedHashMap<>();
        choices.put(a, BARCELONA);
        choices.put(b, ROME);
        choices.put(c, BARCELONA);

        assertEquals(List.of(List.of(a, c), List.of(b)), PlaceChoices.groups(choices));
    }

    @Test
    void useForAllOnlyWhereThePlaceIsNearby() {
        // chosen and edited in the dialog: el Raval, typed sublocation "Sant Antoni"
        final Place chosen = new Place("ES", "Spanien", "Katalonien", "Barcelona", "Sant Antoni");

        assertEquals(chosen, PlaceChoices.forOtherGroup(chosen, List.of(BARCELONA.get(1), BARCELONA.get(0))));
        // a district of Barcelona must never land on photos from Rome
        assertEquals(ROME.get(0).place(), PlaceChoices.forOtherGroup(chosen, ROME));
    }
}

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
package tf.geotagger.model;

import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MediaFilterTest {
    private static MediaFile file(String name, Map<Field, Object> values) {
        return new MediaFile(Path.of("C:/fotos/" + name), MediaType.IMAGE, new Snapshot(null, FileTime.fromMillis(0), values));
    }

    private static final MediaFile COMPLETE = file("complete.jpg",
            Map.of(Field.LATITUDE, 46.4, Field.LONGITUDE, 11.6, Field.ALTITUDE, 1200.0, Field.CITY, "Meran"));
    private static final MediaFile EMPTY = file("empty.jpg", Map.of());

    @Test
    void filtersTestTheStatusOfAFile() {
        assertTrue(MediaFilter.ALL.test(COMPLETE));
        assertFalse(MediaFilter.WITHOUT_COORDINATES.test(COMPLETE));
        assertTrue(MediaFilter.WITHOUT_COORDINATES.test(EMPTY));
        assertTrue(MediaFilter.WITHOUT_PLACE.test(EMPTY));
        assertTrue(MediaFilter.WITHOUT_ALTITUDE.test(EMPTY));
        assertFalse(MediaFilter.CHANGED.test(EMPTY));
    }

    @Test
    void countsPerFilterIncludePendingChanges() {
        final MediaFile changed = file("changed.jpg", Map.of(Field.LATITUDE, 46.4, Field.LONGITUDE, 11.6));
        changed.set(Field.CITY, "Bozen");

        final Map<MediaFilter, Integer> counts = MediaFilter.counts(List.of(COMPLETE, EMPTY, changed));

        assertEquals(Map.of(MediaFilter.ALL, 3, MediaFilter.WITHOUT_COORDINATES, 1, MediaFilter.WITHOUT_PLACE, 1,
                MediaFilter.WITHOUT_ALTITUDE, 2, MediaFilter.CHANGED, 1), counts);
    }
}

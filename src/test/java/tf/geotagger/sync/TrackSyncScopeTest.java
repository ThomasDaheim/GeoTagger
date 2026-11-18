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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tf.geotagger.model.CaptureTime;
import tf.geotagger.model.Field;
import tf.geotagger.model.MediaFile;
import tf.geotagger.model.MediaType;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TrackSyncScopeTest {
    private static final LocalDateTime TIME = LocalDateTime.parse("2026-09-02T11:05:00");
    private static final MediaFile WITHOUT = TestMedia.image(TIME);
    private static final MediaFile OTHER_WITHOUT = TestMedia.image(TIME);
    private static final MediaFile WITH = TestMedia.file(MediaType.IMAGE, new CaptureTime(TIME, false),
            Map.of(Field.LATITUDE, 41.9, Field.LONGITUDE, 12.5));
    private static final List<MediaFile> LISTED = List.of(WITHOUT, WITH, OTHER_WITHOUT);

    @Test
    void withoutCoordinatesTakesListedFilesWithoutCoordinates() {
        assertEquals(List.of(WITHOUT, OTHER_WITHOUT), TrackSync.scope(LISTED, List.of(WITH), FileScope.WITHOUT_COORDINATES, true));
    }

    @Test
    void selectionWithoutOverwriteSkipsFilesWithCoordinates() {
        assertEquals(List.of(WITHOUT), TrackSync.scope(LISTED, List.of(WITHOUT, WITH), FileScope.SELECTION, false));
    }

    @Test
    void selectionWithOverwriteTakesAll() {
        assertEquals(List.of(WITHOUT, WITH), TrackSync.scope(LISTED, List.of(WITHOUT, WITH), FileScope.SELECTION, true));
    }
}

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
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tf.geotagger.model.CaptureTime;
import tf.geotagger.model.Field;
import tf.geotagger.model.MediaFile;
import tf.geotagger.model.MediaType;
import tf.geotagger.model.Snapshot;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SyncDialogTextTest {
    private static final LocalDateTime TIME = LocalDateTime.parse("2026-09-02T11:05:00");

    private static MediaFile file(CaptureTime time, Map<Field, Object> values) {
        return new MediaFile(Path.of("C:/fotos/a.jpg"), MediaType.IMAGE, new Snapshot(time, FileTime.fromMillis(0), values));
    }

    @Test
    void cameraTimeShowsWhoseClockItIs() {
        // user decision 18.11.2026: files with a zone of their own aren't shifted - the preview says so
        assertEquals("2026-09-02 11:05:00", SyncDialog.cameraTime(file(new CaptureTime(TIME, false), Map.of())));
        assertEquals("2026-09-02 11:05:00 +02:00 (own zone)",
                SyncDialog.cameraTime(file(new CaptureTime(TIME, false), Map.of(Field.TIME_OFFSET, ZoneOffset.ofHours(2)))));
        assertEquals("2026-09-02 11:05:00 UTC (own zone)", SyncDialog.cameraTime(file(new CaptureTime(TIME, true), Map.of())));
        assertEquals("", SyncDialog.cameraTime(file(null, Map.of())));
    }
}

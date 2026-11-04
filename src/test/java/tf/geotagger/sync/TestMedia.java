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

import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import tf.geotagger.model.CaptureTime;
import tf.geotagger.model.Field;
import tf.geotagger.model.MediaFile;
import tf.geotagger.model.MediaType;
import tf.geotagger.model.Snapshot;

/**
 * MediaFiles for sync tests - no file on disk needed.
 */
final class TestMedia {
    private static final AtomicInteger COUNTER = new AtomicInteger();

    private TestMedia() {
    }

    /**
     * Image with a camera local capture time (null: no capture time) and no metadata values.
     *
     * @param localCaptureTime capture time of the camera clock, null for none
     * @return a new MediaFile with a unique path
     */
    static MediaFile image(LocalDateTime localCaptureTime) {
        return file(MediaType.IMAGE, localCaptureTime == null ? null : new CaptureTime(localCaptureTime, false), Map.of());
    }

    static MediaFile file(MediaType type, CaptureTime captureTime, Map<Field, Object> values) {
        final Path path = Path.of("C:/fotos/media_" + COUNTER.incrementAndGet() + (type == MediaType.IMAGE ? ".jpg" : ".mov"));
        return new MediaFile(path, type, new Snapshot(captureTime, FileTime.fromMillis(0), values));
    }
}

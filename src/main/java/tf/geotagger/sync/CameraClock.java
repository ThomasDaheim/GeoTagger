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

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import tf.geotagger.model.CaptureTime;

/**
 * Converts between the camera clock and real (UTC) time.
 * cameraOffset > 0 means the camera clock is ahead - the real time is earlier than the recorded one.
 */
public final class CameraClock {
    private CameraClock() {
    }

    public static Instant toUtc(CaptureTime captureTime, ZoneId cameraZone, Duration cameraOffset) {
        final Instant recorded = captureTime.utc()
                // QuickTime CreateDate - already UTC, the camera zone doesn't matter
                ? captureTime.dateTime().toInstant(ZoneOffset.UTC)
                : captureTime.dateTime().atZone(cameraZone).toInstant();
        return recorded.minus(cameraOffset);
    }

    /**
     * What the camera display should show at the given moment - for comparison with the real camera.
     */
    public static LocalDateTime cameraDisplay(Instant now, ZoneId cameraZone, Duration cameraOffset) {
        return LocalDateTime.ofInstant(now.plus(cameraOffset), cameraZone);
    }
}

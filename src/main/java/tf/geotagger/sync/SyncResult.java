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
import tf.geotagger.model.MediaFile;

/**
 * Outcome of the track sync for one file - one row of the preview table in the sync dialog.
 *
 * @param correctedTime capture time in UTC after applying camera zone and offset, null without capture time
 * @param gap time to the nearest real track point (not the interpolated position), null if there is none
 */
public record SyncResult(MediaFile file, Status status, Instant correctedTime, Double latitude, Double longitude,
        Double elevation, Duration gap) {
    public enum Status {
        MATCHED,
        /** matched, but the nearest track point is more than half of the maximum gap away */
        LARGE_GAP,
        /** no track point within the maximum gap */
        NO_MATCH,
        NO_CAPTURE_TIME
    }

    public boolean hasPosition() {
        return status == Status.MATCHED || status == Status.LARGE_GAP;
    }
}

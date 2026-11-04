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
package tf.geotagger.metadata;

import java.util.List;
import tf.geotagger.model.MediaFile;

/**
 * Outcome of a save run - one entry per changed file.
 *
 * @param files result per changed file, in the order of saving
 */
public record SaveResult(List<FileResult> files) {
    public enum Status {
        SAVED,
        /** file was changed outside GeoTagger since it was read - pending changes are kept */
        SKIPPED_MODIFIED,
        /** ExifTool reported an error - pending changes are kept */
        FAILED
    }

    /**
     * @param file the changed file
     * @param status outcome for this file
     * @param message ExifTool errors for FAILED, warnings for SAVED (may be empty)
     */
    public record FileResult(MediaFile file, Status status, String message) {
    }

    public SaveResult {
        files = List.copyOf(files);
    }

    public List<FileResult> with(Status status) {
        return files.stream().filter(file -> file.status() == status).toList();
    }
}

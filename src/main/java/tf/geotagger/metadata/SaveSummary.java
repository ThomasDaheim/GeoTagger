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
import tf.geotagger.metadata.SaveResult.FileResult;
import tf.geotagger.metadata.SaveResult.Status;

/**
 * What the user sees after saving - see spec, section "Fehlerbehandlung".
 */
public final class SaveSummary {
    private SaveSummary() {
    }

    /**
     * @param result outcome of a save run
     * @return true if a file was skipped or failed - then the user has to see the full summary
     */
    public static boolean hasProblems(SaveResult result) {
        return !result.with(Status.SKIPPED_MODIFIED).isEmpty() || !result.with(Status.FAILED).isEmpty();
    }

    /**
     * @param result outcome of a save run
     * @return summary text - the first line ("n saved") is meant for the status bar
     */
    public static String text(SaveResult result) {
        final StringBuilder text = new StringBuilder();
        text.append(result.with(Status.SAVED).size()).append(" saved");

        final List<FileResult> skipped = result.with(Status.SKIPPED_MODIFIED);
        if (!skipped.isEmpty()) {
            text.append("\n\nChanged outside GeoTagger since loading - not saved, please open the folder again:");
            skipped.forEach(file -> text.append('\n').append(file.file().path().getFileName()));
        }
        final List<FileResult> failed = result.with(Status.FAILED);
        if (!failed.isEmpty()) {
            text.append("\n\nNot saved:");
            failed.forEach(file -> text.append('\n').append(file.file().path().getFileName()).append(": ").append(file.message()));
        }
        return text.toString();
    }
}

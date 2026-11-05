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

import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tf.geotagger.metadata.SaveResult.FileResult;
import tf.geotagger.metadata.SaveResult.Status;
import tf.geotagger.model.MediaFile;
import tf.geotagger.model.MediaType;
import tf.geotagger.model.Snapshot;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SaveSummaryTest {
    private static MediaFile file(String name) {
        return new MediaFile(Path.of("C:/fotos/" + name), MediaType.IMAGE, new Snapshot(null, FileTime.fromMillis(0), Map.of()));
    }

    @Test
    void onlySavedFilesHaveNoProblems() {
        final SaveResult result = new SaveResult(List.of(new FileResult(file("a.jpg"), Status.SAVED, ""),
                new FileResult(file("b.jpg"), Status.SAVED, "")));

        assertFalse(SaveSummary.hasProblems(result));
        assertEquals("2 saved", SaveSummary.text(result));
    }

    @Test
    void skippedAndFailedFilesAreListed() {
        final SaveResult result = new SaveResult(List.of(new FileResult(file("a.jpg"), Status.SAVED, ""),
                new FileResult(file("b.jpg"), Status.SKIPPED_MODIFIED, "changed"),
                new FileResult(file("c.jpg"), Status.FAILED, "Error: Not a valid JPG")));

        assertTrue(SaveSummary.hasProblems(result));
        assertEquals("1 saved\n\n"
                + "Changed outside GeoTagger since loading - not saved, please open the folder again:\nb.jpg\n\n"
                + "Not saved:\nc.jpg: Error: Not a valid JPG",
                SaveSummary.text(result));
    }
}

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

import java.util.List;
import org.junit.jupiter.api.Test;
import tf.geo.service.SrtmFormat;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AltitudeQuestionsTest {
    @Test
    void downloadQuestionNamesTilesAndSize() {
        assertEquals("2 SRTM tiles missing (N46E011, N47E011) - download now (up to 36 MB)?",
                AltitudeQuestions.download(List.of("N46E011", "N47E011"), 1, SrtmFormat.SRTM3));
        assertEquals("1 SRTM tile missing (N41E002) - download now (up to 15 MB)?",
                AltitudeQuestions.download(List.of("N41E002"), 1, SrtmFormat.SRTM1));
    }

    @Test
    void longTileListsAreShortened() {
        assertEquals("7 SRTM tiles missing (N40E001, N40E002, N40E003, N40E004, N40E005, ...) - download now (up to 105 MB)?",
                AltitudeQuestions.download(List.of("N40E001", "N40E002", "N40E003", "N40E004", "N40E005", "N40E006", "N40E007"),
                        7, SrtmFormat.SRTM1));
    }
}

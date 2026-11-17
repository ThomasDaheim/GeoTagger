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
import tf.geo.service.SrtmFormat;

/**
 * Texts of "Find altitude" for the user.
 */
final class AltitudeQuestions {
    // tile names in the question at most
    private static final int SHOWN_TILES = 5;
    // largest downloads seen (17.11.2026): block L32 of viewfinderpanoramas 36 MB, tile N46E011 of ESA 15 MB
    private static final int SRTM3_BLOCK_MB = 36;
    private static final int SRTM1_TILE_MB = 15;

    private AltitudeQuestions() {
    }

    /**
     * @param tiles the missing tiles, e.g. N46E011
     * @param downloads number of files to download for them (SRTM3: blocks with several tiles)
     * @param format format of the download
     * @return e.g. "2 SRTM tiles missing (N46E011, N47E011) - download now (up to 36 MB)?"
     */
    static String download(List<String> tiles, int downloads, SrtmFormat format) {
        final String names = String.join(", ", tiles.subList(0, Math.min(SHOWN_TILES, tiles.size())))
                + (tiles.size() > SHOWN_TILES ? ", ..." : "");
        final int megabytes = downloads * (format == SrtmFormat.SRTM1 ? SRTM1_TILE_MB : SRTM3_BLOCK_MB);
        return tiles.size() + (tiles.size() == 1 ? " SRTM tile" : " SRTM tiles") + " missing (" + names
                + ") - download now (up to " + megabytes + " MB)?";
    }
}

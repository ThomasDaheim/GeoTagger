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
package tf.geotagger.enrich;

/**
 * Text for the user after a "Find place" run.
 */
public final class PlaceSummary {
    private PlaceSummary() {
    }

    /**
     * @param run result of the run
     * @return e.g. "Places found for 3 files, 1 without result, 2 failed" - parts with 0 are left out, except the first
     */
    public static String text(PlaceRun run) {
        final int found = run.places().size();
        final StringBuilder text = new StringBuilder("Places found for ").append(found).append(found == 1 ? " file" : " files");
        if (!run.notFound().isEmpty()) {
            text.append(", ").append(run.notFound().size()).append(" without result");
        }
        if (!run.failed().isEmpty()) {
            text.append(", ").append(run.failed().size()).append(" failed");
        }
        return text.toString();
    }

    /**
     * @param run result of the run
     * @return true if requests failed - the user should learn why
     */
    public static boolean hasProblems(PlaceRun run) {
        return !run.failed().isEmpty();
    }
}

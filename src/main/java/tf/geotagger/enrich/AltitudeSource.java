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
 * Where "Find altitude" looks up the altitudes.
 */
public enum AltitudeSource {
    /** local SRTM tiles, the gaps from openrouteservice */
    SRTM_FIRST("SRTM, gaps from openrouteservice"),
    /** local SRTM tiles only */
    SRTM_ONLY("SRTM only"),
    /** openrouteservice only */
    ORS_ONLY("openrouteservice only");

    private final String label;

    AltitudeSource(String label) {
        this.label = label;
    }

    /**
     * @return text for the preferences
     */
    public String label() {
        return label;
    }

    /**
     * @return true if the local SRTM tiles are read
     */
    public boolean usesSrtm() {
        return this != ORS_ONLY;
    }

    /**
     * @return true if openrouteservice is asked
     */
    public boolean usesOrs() {
        return this != SRTM_ONLY;
    }
}

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

import tf.geo.LatLon;

/**
 * Cell of a raster of 0.001° (about 100 m) - files in the same cell need one request, see spec "Datenfluss".
 *
 * @param latitudeIndex latitude in thousandths of a degree, rounded
 * @param longitudeIndex longitude in thousandths of a degree, rounded
 */
public record GridCell(long latitudeIndex, long longitudeIndex) {
    private static final double CELLS_PER_DEGREE = 1000.0;

    /**
     * @param position a position
     * @return the cell containing it
     */
    public static GridCell of(LatLon position) {
        return new GridCell(Math.round(position.latitude() * CELLS_PER_DEGREE), Math.round(position.longitude() * CELLS_PER_DEGREE));
    }

    /**
     * @return the centre of the cell - what is looked up, so that the cached answer fits all files of the cell
     */
    public LatLon center() {
        return new LatLon(latitudeIndex / CELLS_PER_DEGREE, longitudeIndex / CELLS_PER_DEGREE);
    }
}

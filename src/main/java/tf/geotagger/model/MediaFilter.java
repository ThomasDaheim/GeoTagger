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
package tf.geotagger.model;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Filters of the file list - they follow the workflow: coordinates first, then place and altitude.
 */
public enum MediaFilter implements Predicate<MediaFile> {
    ALL("All") {
        @Override
        public boolean test(MediaFile file) {
            return true;
        }
    },
    WITHOUT_COORDINATES("Without coordinates") {
        @Override
        public boolean test(MediaFile file) {
            return !file.hasCoordinates();
        }
    },
    WITHOUT_PLACE("Without place") {
        @Override
        public boolean test(MediaFile file) {
            return !file.hasPlace();
        }
    },
    WITHOUT_ALTITUDE("Without altitude") {
        @Override
        public boolean test(MediaFile file) {
            return !file.hasAltitude();
        }
    },
    CHANGED("Changed") {
        @Override
        public boolean test(MediaFile file) {
            return file.isChanged();
        }
    };

    private final String label;

    MediaFilter(String label) {
        this.label = label;
    }

    /**
     * @return text for the filter buttons
     */
    public String label() {
        return label;
    }

    /**
     * @param files files to count
     * @return number of matching files per filter
     */
    public static Map<MediaFilter, Integer> counts(Collection<MediaFile> files) {
        final Map<MediaFilter, Integer> counts = new EnumMap<>(MediaFilter.class);
        for (MediaFilter filter : values()) {
            counts.put(filter, (int) files.stream().filter(filter).count());
        }
        return counts;
    }
}

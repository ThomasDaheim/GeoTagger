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
package tf.geotagger.edit;

import java.util.List;
import java.util.Objects;
import tf.geotagger.model.Field;
import tf.geotagger.model.MediaFile;

/**
 * Value of one field across the selected files - what the details show.
 *
 * @param value the common value, null if no file has one or the values differ
 * @param different true if the selected files have different values
 */
public record SelectionValue(Object value, boolean different) {
    /**
     * @param files selected files
     * @param field field to look at
     * @return the common value or "different"
     */
    public static SelectionValue of(List<MediaFile> files, Field field) {
        if (files.isEmpty()) {
            return new SelectionValue(null, false);
        }
        final Object first = files.get(0).value(field);
        for (MediaFile file : files) {
            if (!Objects.equals(first, file.value(field))) {
                return new SelectionValue(null, true);
            }
        }
        return new SelectionValue(first, false);
    }
}

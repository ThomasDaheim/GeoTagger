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

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tf.geotagger.model.Field;
import tf.geotagger.model.MediaFile;
import tf.helper.doundo.AbstractDoUndoAction;

/**
 * Changes fields of several files as one step for undo / redo - used by the details, and later by the map,
 * the enrichment and the track sync. Only pending changes are touched, nothing is written to the files.
 */
public class ChangeFieldsAction extends AbstractDoUndoAction {
    private final String description;
    // null values mean "delete the field"
    private final Map<MediaFile, Map<Field, Object>> newValues;
    // what the files showed when the action was created - undo restores exactly that
    private final Map<MediaFile, Map<Field, Object>> oldValues = new LinkedHashMap<>();

    /**
     * @param description text for the user, e.g. "Set city"
     * @param newValues new values per file - a null value deletes the field
     */
    public ChangeFieldsAction(String description, Map<MediaFile, Map<Field, Object>> newValues) {
        this.description = description;
        this.newValues = copy(newValues);
        this.newValues.forEach((file, values) -> {
            final Map<Field, Object> old = new EnumMap<>(Field.class);
            values.keySet().forEach(field -> old.put(field, file.value(field)));
            oldValues.put(file, old);
        });
    }

    /**
     * @param description text for the user, e.g. "Set city"
     * @param files files to change
     * @param field field to set
     * @param value new value for all files - null deletes the field
     * @return the action, not done yet
     */
    public static ChangeFieldsAction sameValue(String description, List<MediaFile> files, Field field, Object value) {
        final Map<MediaFile, Map<Field, Object>> values = new LinkedHashMap<>();
        for (MediaFile file : files) {
            final Map<Field, Object> fileValues = new EnumMap<>(Field.class);
            fileValues.put(field, value);
            values.put(file, fileValues);
        }
        return new ChangeFieldsAction(description, values);
    }

    @Override
    public boolean doHook() {
        apply(newValues);
        return true;
    }

    @Override
    public boolean undoHook() {
        apply(oldValues);
        return true;
    }

    @Override
    public String getDescription() {
        return description;
    }

    private static void apply(Map<MediaFile, Map<Field, Object>> values) {
        values.forEach((file, fieldValues) -> fieldValues.forEach(file::set));
    }

    private static Map<MediaFile, Map<Field, Object>> copy(Map<MediaFile, Map<Field, Object>> values) {
        final Map<MediaFile, Map<Field, Object>> copy = new LinkedHashMap<>();
        values.forEach((file, fieldValues) -> {
            // EnumMap allows null values - "delete the field"
            final Map<Field, Object> fieldCopy = new EnumMap<>(Field.class);
            fieldCopy.putAll(fieldValues);
            copy.put(file, fieldCopy);
        });
        return copy;
    }
}

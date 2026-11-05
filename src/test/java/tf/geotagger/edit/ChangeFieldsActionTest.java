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

import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tf.geotagger.model.Field;
import tf.geotagger.model.MediaFile;
import tf.geotagger.model.MediaType;
import tf.geotagger.model.Snapshot;
import tf.helper.doundo.DoUndoManager;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChangeFieldsActionTest {
    private static MediaFile file(Map<Field, Object> values) {
        return new MediaFile(Path.of("C:/fotos/x.jpg"), MediaType.IMAGE, new Snapshot(null, FileTime.fromMillis(0), values));
    }

    @Test
    void doSetsAndUndoRestoresTheValues() {
        final MediaFile meran = file(Map.of(Field.CITY, "Meran"));
        final MediaFile empty = file(Map.of());
        final ChangeFieldsAction action = ChangeFieldsAction.sameValue("Set city", List.of(meran, empty), Field.CITY, "Bozen");

        assertTrue(action.doAction());
        assertEquals("Bozen", meran.text(Field.CITY));
        assertEquals("Bozen", empty.text(Field.CITY));

        assertTrue(action.undoAction());
        assertEquals("Meran", meran.text(Field.CITY));
        assertNull(empty.text(Field.CITY));
        assertFalse(meran.isChanged());
        assertFalse(empty.isChanged());
        assertEquals("Set city", action.getDescription());
    }

    @Test
    void undoRestoresEarlierPendingChanges() {
        final MediaFile media = file(Map.of(Field.CITY, "Meran"));
        media.set(Field.CITY, "Bozen");
        final ChangeFieldsAction action = ChangeFieldsAction.sameValue("Set city", List.of(media), Field.CITY, null);

        action.doAction();
        assertNull(media.text(Field.CITY));

        action.undoAction();
        assertEquals("Bozen", media.text(Field.CITY));
        assertTrue(media.isChanged());
    }

    @Test
    void differentValuesPerFile() {
        final MediaFile first = file(Map.of());
        final MediaFile second = file(Map.of());
        final Map<MediaFile, Map<Field, Object>> values = new LinkedHashMap<>();
        final Map<Field, Object> firstValues = new EnumMap<>(Field.class);
        firstValues.put(Field.LATITUDE, 46.4);
        firstValues.put(Field.LONGITUDE, 11.6);
        values.put(first, firstValues);
        final Map<Field, Object> secondValues = new EnumMap<>(Field.class);
        secondValues.put(Field.LATITUDE, 47.0);
        values.put(second, secondValues);

        new ChangeFieldsAction("Set position", values).doAction();

        assertEquals(46.4, first.latitude());
        assertEquals(11.6, first.longitude());
        assertEquals(47.0, second.latitude());
        assertNull(second.longitude());
    }

    @Test
    void worksWithTheDoUndoManagerOfJavaHelper() {
        final String key = "test-" + UUID.randomUUID();
        final MediaFile media = file(Map.of(Field.CITY, "Meran"));
        final ChangeFieldsAction action = ChangeFieldsAction.sameValue("Set city", List.of(media), Field.CITY, "Bozen");
        action.doAction();
        DoUndoManager.getInstance().addDoneAction(action, key);

        assertTrue(DoUndoManager.getInstance().canUndo(key));
        DoUndoManager.getInstance().singleUndo(key);
        assertEquals("Meran", media.text(Field.CITY));

        assertTrue(DoUndoManager.getInstance().canDo(key));
        DoUndoManager.getInstance().singleDo(key);
        assertEquals("Bozen", media.text(Field.CITY));

        DoUndoManager.getInstance().clear(key);
    }
}

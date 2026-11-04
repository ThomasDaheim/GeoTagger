package tf.geotagger.model;

import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MediaFileTest {
    private static MediaFile file(Map<Field, Object> values) {
        final Snapshot snapshot = new Snapshot(new CaptureTime(LocalDateTime.of(2026, 9, 2, 11, 8), false),
                FileTime.fromMillis(0), values);
        return new MediaFile(Path.of("C:/fotos/bild.jpg"), MediaType.IMAGE, snapshot);
    }

    private static Map<Field, Object> values(Object... fieldsAndValues) {
        final Map<Field, Object> values = new EnumMap<>(Field.class);
        for (int i = 0; i < fieldsAndValues.length; i += 2) {
            values.put((Field) fieldsAndValues[i], fieldsAndValues[i + 1]);
        }
        return values;
    }

    @Test
    void showsOriginalValuesWithoutChanges() {
        final MediaFile media = file(values(Field.LATITUDE, 46.4, Field.LONGITUDE, 11.6, Field.CITY, "Meran"));
        assertEquals(46.4, media.latitude());
        assertEquals("Meran", media.text(Field.CITY));
        assertTrue(media.hasCoordinates());
        assertTrue(media.hasPlace());
        assertFalse(media.hasAltitude());
        assertFalse(media.isChanged());
    }

    @Test
    void pendingValueOverridesOriginal() {
        final MediaFile media = file(values(Field.CITY, "Meran"));
        media.set(Field.CITY, "Bozen");
        assertEquals("Bozen", media.text(Field.CITY));
        assertEquals("Meran", media.original().values().get(Field.CITY));
        assertTrue(media.isChanged());
        assertEquals(Map.of(Field.CITY, "Bozen"), media.pendingChanges());
    }

    @Test
    void settingTheOriginalValueAgainRemovesTheChange() {
        final MediaFile media = file(values(Field.CITY, "Meran"));
        media.set(Field.CITY, "Bozen");
        media.set(Field.CITY, "Meran");
        assertFalse(media.isChanged());
    }

    @Test
    void clearingAValueIsAPendingDelete() {
        final MediaFile media = file(values(Field.CITY, "Meran"));
        media.set(Field.CITY, null);
        assertNull(media.text(Field.CITY));
        assertFalse(media.hasPlace());
        assertTrue(media.pendingChanges().containsKey(Field.CITY));
        assertNull(media.pendingChanges().get(Field.CITY));
    }

    @Test
    void blankTextIsTreatedAsEmptyAndLineBreaksAreRemoved() {
        final MediaFile media = file(values());
        media.set(Field.CITY, "   ");
        assertFalse(media.isChanged());
        media.set(Field.SUBLOCATION, " Piazza\r\nDuomo ");
        assertEquals("Piazza Duomo", media.text(Field.SUBLOCATION));
    }

    @Test
    void wrongValueTypeIsRejected() {
        final MediaFile media = file(values());
        assertThrows(IllegalArgumentException.class, () -> media.set(Field.LATITUDE, "46.4"));
        assertThrows(IllegalArgumentException.class, () -> media.set(Field.TIME_OFFSET, "+02:00"));
    }

    @Test
    void replaceOriginalClearsPendingChanges() {
        final MediaFile media = file(values());
        media.set(Field.TIME_OFFSET, ZoneOffset.ofHours(2));
        media.replaceOriginal(new Snapshot(null, FileTime.fromMillis(1), values(Field.TIME_OFFSET, ZoneOffset.ofHours(2))));
        assertFalse(media.isChanged());
        assertEquals(ZoneOffset.ofHours(2), media.timeOffset());
        assertNull(media.captureTime());
    }

    @Test
    void discardChangesRestoresOriginal() {
        final MediaFile media = file(values(Field.ALTITUDE, 1200.0));
        media.set(Field.ALTITUDE, 1300.0);
        media.discardChanges();
        assertEquals(1200.0, media.altitude());
        assertFalse(media.isChanged());
    }

    @Test
    void snapshotDropsNullValuesAndIsImmutable() {
        final Map<Field, Object> values = values(Field.CITY, "Meran");
        values.put(Field.COUNTRY, null);
        final Snapshot snapshot = new Snapshot(null, FileTime.fromMillis(0), values);
        assertFalse(snapshot.values().containsKey(Field.COUNTRY));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.values().put(Field.STATE, "x"));
    }
}

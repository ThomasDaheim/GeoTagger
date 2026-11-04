# GeoTagger Plan 3b: Details, Bild-Info, Undo/Redo, Speichern – Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use anthropic-skills:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** In GeoTagger lassen sich Koordinaten, Höhe, Ortsfelder und Zeitzone der ausgewählten Dateien bearbeiten, auch mehrere auf einmal. Änderungen lassen sich rückgängig machen und speichern. Alle rohen Tags einer Datei sind im Reiter „Image info“ einsehbar. Ungespeicherte Änderungen gehen beim Schließen oder beim Ordnerwechsel nicht verloren.

**Architecture:**
- **Testbare Logik in eigenen Klassen:**
  - `edit.FieldText` zeigt Werte an und prüft Eingaben.
  - `edit.SelectionValue` liefert den gemeinsamen Wert bei Mehrfachauswahl.
  - `edit.ChangeFieldsAction` ist eine Undo/Redo-Aktion auf Basis von JavaHelpers `AbstractDoUndoAction`. Sie ändert pro Datei beliebige Felder und wird in Plan 4 bis 6 auch von Karte, Anreichern und Sync genutzt.
  - `metadata.SaveSummary` erzeugt den Text nach dem Speichern.
- **Oberfläche:** `DetailsPane` und `ImageInfoPane` sind eigene Klassen wie `PreviewPane`. Der Controller wird einmal komplett ersetzt.
- **Kein Ereignismodell in `MediaFile`:** `MediaFile` meldet keine Änderungen. Nach jeder Änderung ruft der Controller `refreshFiles()` auf. Das wendet den Filter neu an, zeichnet die Zeilen neu und aktualisiert Zähler und Buttons, die Auswahl bleibt erhalten.
- **Undo/Redo wie in GPXEditor:** erst `action.doAction()`, dann `DoUndoManager.getInstance().addDoneAction(action, key)`. Der Schlüssel ist pro geöffnetem Ordner. Strg+Z und Strg+Y laufen über `UsefulKeyCodes`.

**Tech Stack:** Java 25, JavaFX 25.0.1, JavaHelper 1.17 (`tf.helper.doundo`, `ShowAlerts`, `UsefulKeyCodes`), ExifToolHelper 0.1 (+ `readAllTags`), JUnit 5.13.0.

Spec: [2026-11-03-geotagger-design.md](../specs/2026-11-03-geotagger-design.md), Abschnitte „Umfang“, „Hauptfenster“, „Datenfluss“ (Bearbeiten, Speichern) und „Fehlerbehandlung“

## Global Constraints

- **Oberflächensprache: Englisch.** **Keine automatisierten UI-Tests:** Die Logik wird mit JUnit getestet, die Oberfläche in Task 6 manuell nach Checkliste.
- Java 25, Gradle 9.2.1 über den Wrapper. Gearbeitet wird direkt auf `master`, in GeoTagger und ExifToolHelper.
- **Lizenz-Header:** Jede `.java`-Datei beginnt mit dem BSD-Header aus GPXEditor (25 Zeilen, z. B. von `GeoTagger\src\main\java\tf\geotagger\model\Field.java`). Die Codeblöcke unten lassen ihn weg.
- **Javadoc:** Ist ein Kommentar vorhanden, dann vollständig (`@param`/`@return`/`@throws`), `<…>` steht in `{@code …}`.
- Compilerwarnungen in `src/main` und `src/test` werden behoben.
- **Git und Ordner** in jeder PowerShell-Sitzung:
  ```powershell
  $git = 'C:\Users\thomas\AppData\Local\GitHubDesktop\app-3.6.7\resources\app\git\cmd\git.exe'
  $app = 'D:\Thomas\Development\NetBeansProjects\GeoTagger'
  $etool = 'D:\Thomas\Development\NetBeansProjects\ExifToolHelper'
  ```
- **ExifTool:** Die Tests finden es über `EXIFTOOL_PATH` oder den PATH. Für einen ausdrücklichen Test gegen die winget-Version vorher setzen: `$env:EXIFTOOL_PATH = 'C:\Users\thomas\AppData\Local\Programs\ExifTool\exiftool.exe'`.
- **Fachliche Regeln aus der Spec:**
  - Reiter „Details“: editierbare Felder. Bei Mehrfachauswahl wird „(different)“ angezeigt, und eine Eingabe gilt für alle ausgewählten Dateien.
  - Reiter „Image info“: alle rohen Tags der ausgewählten Datei, nur lesbar.
  - Änderungen werden gesammelt und erst beim Speichern geschrieben. Der Speichern-Button zeigt die Anzahl der geänderten Dateien.
  - Ein Feld ist ungespeichert geändert: Die Statusspalten zeigen dann ● statt ✓/–.
  - Speichern läuft im Hintergrund mit Fortschritt. Danach gibt es eine Zusammenfassung, wenn Dateien übersprungen wurden (von außen geändert) oder fehlschlugen. Diese behalten ihre Änderungen.
  - Der Undo-Verlauf wird nach dem Speichern geleert.
  - Ungespeicherte Änderungen beim Schließen oder beim Ordnerwechsel: Rückfrage mit Save, Discard oder Cancel.
- **Eingaben:**
  - Breite −90 bis 90, Länge −180 bis 180. Zahlen gehen mit Punkt oder Komma.
  - Der Ländercode wird in Großbuchstaben gespeichert.
  - Die Zeitzone wird als Offset eingegeben (`+02:00`, `-5`, `Z`).
  - Ein leeres Feld löscht den Wert.
  - Bei Mehrfachauswahl mit unterschiedlichen Werten bleibt das Feld leer, und ein leer verlassenes Feld ändert nichts. Für alle löschen geht so nicht. Das ist eine bewusste Grenze, damit ein versehentliches Durchklicken nichts löscht.

## Dateistruktur

```
ExifToolHelper:
  src\main\java\tf\exiftool\ExifTool.java              -- + readAllTags(Path)
  src\test\java\tf\exiftool\ExifToolReadTest.java      -- + 2 tests
GeoTagger:
  src\main\java\tf\geotagger\edit\
    FieldText.java          -- format / parse field values for the details
    SelectionValue.java     -- common value of a field across the selection
    ChangeFieldsAction.java -- do / undo of field changes for several files
  src\main\java\tf\geotagger\metadata\SaveSummary.java -- text after saving, "has problems"
  src\main\java\tf\geotagger\main\
    DetailsPane.java        -- editable fields of the selection
    ImageInfoPane.java      -- all tags of the selected file, read in the background
    GeoTagger.java          -- replaced: details, image info, undo / redo, save, unsaved changes
    GeoTaggerPreferences.java -- + RECENT_RIGHT_DIVIDER_POS
    PreviewPane.java        -- show() ignores a repeated request for the file already shown
  src\main\resources\GeoTagger.fxml -- replaced: toolbar with Save / Undo / Redo, right side with tabs
  src\test\java\tf\geotagger\edit\FieldTextTest.java, SelectionValueTest.java, ChangeFieldsActionTest.java
  src\test\java\tf\geotagger\metadata\SaveSummaryTest.java
```

---

### Task 1: Alle Tags einer Datei lesen (ExifToolHelper)

**Files:**
- Modify: `D:\Thomas\Development\NetBeansProjects\ExifToolHelper\src\main\java\tf\exiftool\ExifTool.java`
- Test: `D:\Thomas\Development\NetBeansProjects\ExifToolHelper\src\test\java\tf\exiftool\ExifToolReadTest.java`

**Interfaces:**
- Produces: `public Map<String, String> readAllTags(Path file)`
  - Rückgabe: Schlüssel im Format `Gruppe1:Tag` (z. B. `XMP-photoshop:City`, `System:FileName`), Werte lesbar wie bei `exiftool -G1` (z. B. `46 deg 30' 0.00" N`).
  - Die Reihenfolge entspricht der Ausgabe von ExifTool. Ohne `SourceFile`. Leer, wenn ExifTool die Datei nicht lesen kann.
  - Artefakt `tf.ExifToolHelper:ExifToolHelper:0.1` in mavenLocal (neu veröffentlicht).

- [ ] **Step 1: Tests ergänzen**

In `ExifToolReadTest.java` kommen nach `missingBinaryTagGivesFalse()` diese Tests dazu:

```java
    @Test
    void readsAllTagsHumanReadable() throws IOException {
        final Path file = jpeg(dir.resolve("info.jpg"));
        exifTool.execute(List.of("-overwrite_original", "-XMP-photoshop:City=Völs", "-GPS:GPSLatitude#=46.5",
                "-GPS:GPSLatitudeRef#=N", file.toString()));

        final Map<String, String> tags = exifTool.readAllTags(file);

        assertEquals("Völs", tags.get("XMP-photoshop:City"));
        assertEquals("info.jpg", tags.get("System:FileName"));
        assertEquals("46 deg 30' 0.00\" N", tags.get("Composite:GPSLatitude"));
        assertFalse(tags.containsKey("SourceFile"));
    }

    @Test
    void allTagsOfMissingFileAreEmpty() {
        assertTrue(exifTool.readAllTags(dir.resolve("missing.jpg")).isEmpty());
    }
```

- [ ] **Step 2: Test laufen lassen, Fehler erwarten**

Run (in `$etool`): `.\gradlew.bat test --tests "tf.exiftool.ExifToolReadTest"`
Expected: FAIL, Kompilierfehler „cannot find symbol: method readAllTags“.

- [ ] **Step 3: Implementierung ergänzen**

In `ExifTool.java` kommt der Import `import java.util.LinkedHashMap;` dazu (nach `import java.util.ArrayList;`). Diese Methode kommt nach `readJson(...)`:

```java
    /**
     * All tags of one file with their human readable values, e.g. for an "image info" view.
     *
     * @param file file to read
     * @return "Group1:Tag" to value as text, in the order ExifTool reports them - empty if ExifTool can't read the file
     */
    public Map<String, String> readAllTags(Path file) {
        // -a: duplicate tags as well, -G1: group names like "ExifIFD" or "XMP-photoshop"
        final String json = execute(List.of("-json", "-a", "-G1", file.toAbsolutePath().toString())).stdoutText().trim();
        if (json.isEmpty()) {
            return Map.of();
        }
        final List<Map<String, Object>> entries;
        try {
            entries = MAPPER.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (JsonProcessingException ex) {
            throw new ExifToolException("Can't parse ExifTool output", ex);
        }
        final Map<String, String> tags = new LinkedHashMap<>();
        if (!entries.isEmpty()) {
            entries.get(0).forEach((key, value) -> {
                if (!"SourceFile".equals(key)) {
                    tags.put(key, String.valueOf(value));
                }
            });
        }
        return tags;
    }
```

- [ ] **Step 4: Bauen, testen, veröffentlichen**

Run (in `$etool`): `.\gradlew.bat build`
Expected: BUILD SUCCESSFUL, 12 Tests grün. Das Artefakt in `~/.m2/repository/tf/ExifToolHelper/ExifToolHelper/0.1` ist neu geschrieben.

- [ ] **Step 5: Commit**

```powershell
& $git -C $etool add src
& $git -C $etool commit -m "Read all tags of a file, human readable"
```

---

### Task 2: Feldwerte anzeigen, prüfen und über die Auswahl zusammenfassen

**Files:**
- Create: `src\main\java\tf\geotagger\edit\FieldText.java`, `SelectionValue.java`
- Test: `src\test\java\tf\geotagger\edit\FieldTextTest.java`, `SelectionValueTest.java`

**Interfaces:**
- Consumes: `Field`, `MediaFile.value(Field)` (Plan 1)
- Produces:
  - `public final class FieldText`
    - `static String format(Field field, Object value)`
    - `static Object parse(Field field, String text)`. Wirft `IllegalArgumentException` mit einer Meldung für den Nutzer. Ein leerer Text ergibt `null`.
  - `public record SelectionValue(Object value, boolean different)` mit `static SelectionValue of(List<MediaFile> files, Field field)`

- [ ] **Step 1: Fehlschlagende Tests schreiben**

`src\test\java\tf\geotagger\edit\FieldTextTest.java`:

```java
package tf.geotagger.edit;

import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import tf.geotagger.model.Field;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FieldTextTest {
    @Test
    void formatsValuesForTheDetails() {
        assertEquals("46.4071", FieldText.format(Field.LATITUDE, 46.4071));
        assertEquals("1200", FieldText.format(Field.ALTITUDE, 1200.0));
        assertEquals("+02:00", FieldText.format(Field.TIME_OFFSET, ZoneOffset.ofHours(2)));
        assertEquals("+00:00", FieldText.format(Field.TIME_OFFSET, ZoneOffset.UTC));
        assertEquals("Völs", FieldText.format(Field.CITY, "Völs"));
        assertEquals("", FieldText.format(Field.CITY, null));
    }

    @Test
    void parsesValidInput() {
        assertEquals(46.5, FieldText.parse(Field.LATITUDE, " 46,5 "));
        assertEquals(-180.0, FieldText.parse(Field.LONGITUDE, "-180"));
        assertEquals(-12.5, FieldText.parse(Field.ALTITUDE, "-12.5"));
        assertEquals("IT", FieldText.parse(Field.COUNTRY_CODE, "it"));
        assertEquals(ZoneOffset.ofHours(2), FieldText.parse(Field.TIME_OFFSET, "+02:00"));
        assertEquals(ZoneOffset.ofHours(-5), FieldText.parse(Field.TIME_OFFSET, "-5"));
        assertEquals(ZoneOffset.UTC, FieldText.parse(Field.TIME_OFFSET, "Z"));
        assertEquals("Seiser Alm", FieldText.parse(Field.SUBLOCATION, " Seiser Alm "));
    }

    @Test
    void blankInputMeansNoValue() {
        assertNull(FieldText.parse(Field.LATITUDE, "  "));
        assertNull(FieldText.parse(Field.CITY, ""));
        assertNull(FieldText.parse(Field.TIME_OFFSET, null));
    }

    @Test
    void rejectsInvalidInputWithMessage() {
        assertEquals("Latitude must be between -90 and 90",
                assertThrows(IllegalArgumentException.class, () -> FieldText.parse(Field.LATITUDE, "91")).getMessage());
        assertEquals("Longitude must be a number",
                assertThrows(IllegalArgumentException.class, () -> FieldText.parse(Field.LONGITUDE, "elf")).getMessage());
        assertThrows(IllegalArgumentException.class, () -> FieldText.parse(Field.ALTITUDE, "NaN"));
        assertThrows(IllegalArgumentException.class, () -> FieldText.parse(Field.TIME_OFFSET, "Europe/Rome"));
    }
}
```

`src\test\java\tf\geotagger\edit\SelectionValueTest.java`:

```java
package tf.geotagger.edit;

import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tf.geotagger.model.Field;
import tf.geotagger.model.MediaFile;
import tf.geotagger.model.MediaType;
import tf.geotagger.model.Snapshot;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SelectionValueTest {
    private static MediaFile file(Map<Field, Object> values) {
        return new MediaFile(Path.of("C:/fotos/x.jpg"), MediaType.IMAGE, new Snapshot(null, FileTime.fromMillis(0), values));
    }

    @Test
    void emptySelectionHasNoValue() {
        assertEquals(new SelectionValue(null, false), SelectionValue.of(List.of(), Field.CITY));
    }

    @Test
    void sameValueIsShown() {
        final List<MediaFile> files = List.of(file(Map.of(Field.CITY, "Meran")), file(Map.of(Field.CITY, "Meran")));

        assertEquals(new SelectionValue("Meran", false), SelectionValue.of(files, Field.CITY));
        assertEquals(new SelectionValue(null, false), SelectionValue.of(files, Field.COUNTRY));
    }

    @Test
    void differentValuesIncludingPendingChanges() {
        final MediaFile changed = file(Map.of(Field.CITY, "Meran"));
        changed.set(Field.CITY, "Bozen");
        final List<MediaFile> files = List.of(file(Map.of(Field.CITY, "Meran")), changed);

        assertEquals(new SelectionValue(null, true), SelectionValue.of(files, Field.CITY));
    }
}
```

- [ ] **Step 2: Tests laufen lassen, Fehler erwarten**

Run: `.\gradlew.bat test --tests "tf.geotagger.edit.*"`
Expected: FAIL, Kompilierfehler „cannot find symbol: class FieldText“.

- [ ] **Step 3: Implementierung schreiben**

`src\main\java\tf\geotagger\edit\FieldText.java`:

```java
package tf.geotagger.edit;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.ZoneOffset;
import java.util.Locale;
import tf.geotagger.model.Field;

/**
 * Text of the field values in the details: formatting for display and checking / parsing the user's input.
 */
public final class FieldText {
    private FieldText() {
    }

    /**
     * @param field field of the value
     * @param value value of the field's type, may be null
     * @return the value as shown in the details - empty for null
     */
    public static String format(Field field, Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Double number) {
            return BigDecimal.valueOf(number).stripTrailingZeros().toPlainString();
        }
        if (value instanceof ZoneOffset offset) {
            // ZoneOffset.UTC.getId() is "Z"
            return ZoneOffset.UTC.equals(offset) ? "+00:00" : offset.getId();
        }
        return value.toString();
    }

    /**
     * @param field field to parse for
     * @param text text as entered - blank means "no value"
     * @return the value of the field's type, null for blank text
     * @throws IllegalArgumentException with a message for the user if the text isn't valid for the field
     */
    public static Object parse(Field field, String text) {
        final String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return switch (field) {
            case LATITUDE -> number(trimmed, "Latitude", 90);
            case LONGITUDE -> number(trimmed, "Longitude", 180);
            case ALTITUDE -> number(trimmed, "Altitude", Double.POSITIVE_INFINITY);
            case COUNTRY_CODE -> trimmed.toUpperCase(Locale.ROOT);
            case TIME_OFFSET -> offset(trimmed);
            default -> trimmed;
        };
    }

    private static Double number(String text, String name, double limit) {
        final double value;
        try {
            // comma as decimal separator is accepted as well
            value = Double.parseDouble(text.replace(',', '.'));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(name + " must be a number");
        }
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be a number");
        }
        if (Math.abs(value) > limit) {
            throw new IllegalArgumentException(name + " must be between -" + (int) limit + " and " + (int) limit);
        }
        return value;
    }

    private static ZoneOffset offset(String text) {
        try {
            return ZoneOffset.of(text);
        } catch (DateTimeException ex) {
            throw new IllegalArgumentException("Time zone must be an offset like +02:00 or -05:00");
        }
    }
}
```

`src\main\java\tf\geotagger\edit\SelectionValue.java`:

```java
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
```

- [ ] **Step 4: Tests laufen lassen**

Run: `.\gradlew.bat test`
Expected: BUILD SUCCESSFUL, 108 Tests grün (101 + 7).

- [ ] **Step 5: Commit**

```powershell
& $git -C $app add src
& $git -C $app commit -m "Format, parse and combine field values for the details"
```

---

### Task 3: Undo/Redo-Aktion für Feldänderungen

**Files:**
- Create: `src\main\java\tf\geotagger\edit\ChangeFieldsAction.java`
- Test: `src\test\java\tf\geotagger\edit\ChangeFieldsActionTest.java`

**Interfaces:**
- Consumes: JavaHelper `tf.helper.doundo.AbstractDoUndoAction` (`doHook()`, `undoHook()`, `getDescription()`), `DoUndoManager`; `MediaFile.value/set` (Plan 1)
- Produces: `public class ChangeFieldsAction extends AbstractDoUndoAction`
  - `ChangeFieldsAction(String description, Map<MediaFile, Map<Field, Object>> newValues)`. Ein `null`-Wert löscht das Feld.
  - `static ChangeFieldsAction sameValue(String description, List<MediaFile> files, Field field, Object value)`
  - Verwendung: `action.doAction(); DoUndoManager.getInstance().addDoneAction(action, key);`
  - Undo stellt genau die Werte wieder her, die die Dateien beim Erzeugen der Aktion zeigten, inklusive vorheriger ausstehender Änderungen.

- [ ] **Step 1: Fehlschlagenden Test schreiben**

`src\test\java\tf\geotagger\edit\ChangeFieldsActionTest.java`:

```java
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
```

- [ ] **Step 2: Test laufen lassen, Fehler erwarten**

Run: `.\gradlew.bat test --tests "tf.geotagger.edit.ChangeFieldsActionTest"`
Expected: FAIL, Kompilierfehler „cannot find symbol: class ChangeFieldsAction“.

- [ ] **Step 3: Implementierung schreiben**

`src\main\java\tf\geotagger\edit\ChangeFieldsAction.java`:

```java
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
```

- [ ] **Step 4: Tests laufen lassen**

Run: `.\gradlew.bat test`
Expected: BUILD SUCCESSFUL, 112 Tests grün.

- [ ] **Step 5: Commit**

```powershell
& $git -C $app add src
& $git -C $app commit -m "Undoable action for field changes of several files"
```

---

### Task 4: Zusammenfassung nach dem Speichern

**Files:**
- Create: `src\main\java\tf\geotagger\metadata\SaveSummary.java`
- Test: `src\test\java\tf\geotagger\metadata\SaveSummaryTest.java`

**Interfaces:**
- Consumes: `SaveResult`, `SaveResult.FileResult`, `SaveResult.Status` (Plan 1)
- Produces: `public final class SaveSummary`
  - `static boolean hasProblems(SaveResult result)`
  - `static String text(SaveResult result)`. Die erste Zeile ist immer `<n> saved` und dient als Statustext.

- [ ] **Step 1: Fehlschlagenden Test schreiben**

`src\test\java\tf\geotagger\metadata\SaveSummaryTest.java`:

```java
package tf.geotagger.metadata;

import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tf.geotagger.metadata.SaveResult.FileResult;
import tf.geotagger.metadata.SaveResult.Status;
import tf.geotagger.model.MediaFile;
import tf.geotagger.model.MediaType;
import tf.geotagger.model.Snapshot;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SaveSummaryTest {
    private static MediaFile file(String name) {
        return new MediaFile(Path.of("C:/fotos/" + name), MediaType.IMAGE, new Snapshot(null, FileTime.fromMillis(0), Map.of()));
    }

    @Test
    void onlySavedFilesHaveNoProblems() {
        final SaveResult result = new SaveResult(List.of(new FileResult(file("a.jpg"), Status.SAVED, ""),
                new FileResult(file("b.jpg"), Status.SAVED, "")));

        assertFalse(SaveSummary.hasProblems(result));
        assertEquals("2 saved", SaveSummary.text(result));
    }

    @Test
    void skippedAndFailedFilesAreListed() {
        final SaveResult result = new SaveResult(List.of(new FileResult(file("a.jpg"), Status.SAVED, ""),
                new FileResult(file("b.jpg"), Status.SKIPPED_MODIFIED, "changed"),
                new FileResult(file("c.jpg"), Status.FAILED, "Error: Not a valid JPG")));

        assertTrue(SaveSummary.hasProblems(result));
        assertEquals("1 saved\n\n"
                + "Changed outside GeoTagger since loading - not saved, please open the folder again:\nb.jpg\n\n"
                + "Not saved:\nc.jpg: Error: Not a valid JPG",
                SaveSummary.text(result));
    }
}
```

- [ ] **Step 2: Test laufen lassen, Fehler erwarten**

Run: `.\gradlew.bat test --tests "tf.geotagger.metadata.SaveSummaryTest"`
Expected: FAIL, Kompilierfehler „cannot find symbol: class SaveSummary“.

- [ ] **Step 3: Implementierung schreiben**

`src\main\java\tf\geotagger\metadata\SaveSummary.java`:

```java
package tf.geotagger.metadata;

import java.util.List;
import tf.geotagger.metadata.SaveResult.FileResult;
import tf.geotagger.metadata.SaveResult.Status;

/**
 * What the user sees after saving - see spec, section "Fehlerbehandlung".
 */
public final class SaveSummary {
    private SaveSummary() {
    }

    /**
     * @param result outcome of a save run
     * @return true if a file was skipped or failed - then the user has to see the full summary
     */
    public static boolean hasProblems(SaveResult result) {
        return !result.with(Status.SKIPPED_MODIFIED).isEmpty() || !result.with(Status.FAILED).isEmpty();
    }

    /**
     * @param result outcome of a save run
     * @return summary text - the first line ("n saved") is meant for the status bar
     */
    public static String text(SaveResult result) {
        final StringBuilder text = new StringBuilder();
        text.append(result.with(Status.SAVED).size()).append(" saved");

        final List<FileResult> skipped = result.with(Status.SKIPPED_MODIFIED);
        if (!skipped.isEmpty()) {
            text.append("\n\nChanged outside GeoTagger since loading - not saved, please open the folder again:");
            skipped.forEach(file -> text.append('\n').append(file.file().path().getFileName()));
        }
        final List<FileResult> failed = result.with(Status.FAILED);
        if (!failed.isEmpty()) {
            text.append("\n\nNot saved:");
            failed.forEach(file -> text.append('\n').append(file.file().path().getFileName()).append(": ").append(file.message()));
        }
        return text.toString();
    }
}
```

- [ ] **Step 4: Tests laufen lassen**

Run: `.\gradlew.bat test`
Expected: BUILD SUCCESSFUL, 114 Tests grün.

- [ ] **Step 5: Commit**

```powershell
& $git -C $app add src
& $git -C $app commit -m "Summary text after saving"
```

---

### Task 5: Bereiche „Details“ und „Image info“

**Files:**
- Create: `src\main\java\tf\geotagger\main\DetailsPane.java`, `ImageInfoPane.java`

**Interfaces:**
- Consumes:
  - `FieldText`, `SelectionValue` (Task 2)
  - `ExifTool.readAllTags` (Task 1)
- Produces:
  - `public class DetailsPane extends GridPane`
    - `void setFiles(List<MediaFile>)`
    - `void setOnEdit(BiConsumer<Field, Object>)`. Wird mit dem Feld und dem neuen Wert aufgerufen, `null` heißt löschen.
    - `static String label(Field)`
  - `public class ImageInfoPane extends BorderPane`
    - `void setExifTool(ExifTool)`
    - `void show(Path)` (`null` = nichts)
    - `void reload()`
    - `void dispose()`

- [ ] **Step 1: `DetailsPane` schreiben**

`src\main\java\tf\geotagger\main\DetailsPane.java`:

```java
package tf.geotagger.main;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import tf.geotagger.edit.FieldText;
import tf.geotagger.edit.SelectionValue;
import tf.geotagger.model.Field;
import tf.geotagger.model.MediaFile;

/**
 * Editable fields of the selected files - see spec, section "Hauptfenster", tab "Details".
 * A change is reported when the user presses Enter or leaves the field; invalid input is marked red and not reported.
 */
public class DetailsPane extends GridPane {
    private static final Map<Field, String> LABELS = new EnumMap<>(Map.of(
            Field.LATITUDE, "Latitude",
            Field.LONGITUDE, "Longitude",
            Field.ALTITUDE, "Altitude (m)",
            Field.COUNTRY_CODE, "Country code",
            Field.COUNTRY, "Country",
            Field.STATE, "State / province",
            Field.CITY, "City",
            Field.SUBLOCATION, "Sublocation",
            Field.TIME_OFFSET, "Time zone (UTC offset)"));
    private static final String ERROR_STYLE = "-fx-border-color: red;";

    private final Map<Field, TextField> textFields = new EnumMap<>(Field.class);
    // text shown for the current selection - leaving a field with this text changes nothing
    private final Map<Field, String> shownTexts = new EnumMap<>(Field.class);
    private BiConsumer<Field, Object> onEdit = (field, value) -> {};

    public DetailsPane() {
        setHgap(8);
        setVgap(6);
        setPadding(new Insets(8));
        final ColumnConstraints labels = new ColumnConstraints();
        final ColumnConstraints fields = new ColumnConstraints();
        fields.setHgrow(Priority.ALWAYS);
        getColumnConstraints().addAll(labels, fields);

        int row = 0;
        for (Field field : Field.values()) {
            final TextField text = new TextField();
            text.setOnAction(event -> commit(field));
            text.focusedProperty().addListener((observable, wasFocused, focused) -> {
                if (!focused) {
                    commit(field);
                }
            });
            textFields.put(field, text);
            add(new Label(LABELS.get(field)), 0, row);
            add(text, 1, row);
            row++;
        }
        setFiles(List.of());
    }

    /**
     * @param field a field
     * @return its label in the details
     */
    public static String label(Field field) {
        return LABELS.get(field);
    }

    /**
     * @param onEdit called with the field and its new value (null = delete) when the user changed a field
     */
    public void setOnEdit(BiConsumer<Field, Object> onEdit) {
        this.onEdit = onEdit;
    }

    /**
     * Shows the values of the selected files - fields with different values stay empty and show "(different)".
     *
     * @param files selected files - none disables all fields
     */
    public void setFiles(List<MediaFile> files) {
        for (Field field : Field.values()) {
            final SelectionValue selection = SelectionValue.of(files, field);
            final String shown = FieldText.format(field, selection.value());
            shownTexts.put(field, shown);
            final TextField text = textFields.get(field);
            text.setText(shown);
            text.setPromptText(selection.different() ? "(different)" : "");
            clearError(text);
            text.setDisable(files.isEmpty());
        }
    }

    private void commit(Field field) {
        final TextField text = textFields.get(field);
        if (text.getText().equals(shownTexts.get(field))) {
            clearError(text);
            return;
        }
        try {
            final Object value = FieldText.parse(field, text.getText());
            clearError(text);
            // Enter and leaving the field would report the same change twice
            shownTexts.put(field, text.getText());
            onEdit.accept(field, value);
        } catch (IllegalArgumentException ex) {
            text.setStyle(ERROR_STYLE);
            text.setTooltip(new Tooltip(ex.getMessage()));
        }
    }

    private static void clearError(TextField text) {
        text.setStyle("");
        text.setTooltip(null);
    }
}
```

- [ ] **Step 2: `ImageInfoPane` schreiben**

`src\main\java\tf\geotagger\main\ImageInfoPane.java`:

```java
package tf.geotagger.main;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import tf.exiftool.ExifTool;

/**
 * All tags of the selected file as ExifTool reports them - read only, see spec, tab "Bild-Info (ExifTool)".
 */
public class ImageInfoPane extends BorderPane {
    private final TableView<Map.Entry<String, String>> table = new TableView<>();
    // tags are read with ExifTool - one file at a time
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        final Thread thread = new Thread(runnable, "GeoTagger-info");
        thread.setDaemon(true);
        return thread;
    });

    private ExifTool exifTool;
    // file requested last - answers for older requests are dropped
    private Path current;

    public ImageInfoPane() {
        final TableColumn<Map.Entry<String, String>, String> tagColumn = new TableColumn<>("Tag");
        tagColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getKey()));
        tagColumn.setPrefWidth(240);
        final TableColumn<Map.Entry<String, String>, String> valueColumn = new TableColumn<>("Value");
        valueColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getValue()));
        valueColumn.setPrefWidth(320);
        table.getColumns().addAll(List.of(tagColumn, valueColumn));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        setCenter(table);
        show(null);
    }

    /**
     * @param exifTool used to read the tags - null while ExifTool isn't usable
     */
    public void setExifTool(ExifTool exifTool) {
        this.exifTool = exifTool;
    }

    /**
     * Shows all tags of a file - read in the background. Asking again for the file already shown does nothing.
     *
     * @param file file to show, null for none
     */
    public void show(Path file) {
        if (file != null && file.equals(current)) {
            return;
        }
        current = file;
        table.getItems().clear();
        if (file == null) {
            table.setPlaceholder(new Label("No file selected"));
            return;
        }
        if (exifTool == null) {
            table.setPlaceholder(new Label("ExifTool not usable"));
            return;
        }
        table.setPlaceholder(new Label("Reading " + file.getFileName() + " ..."));
        final ExifTool requestExifTool = exifTool;
        executor.submit(() -> {
            Map<String, String> tags;
            try {
                tags = requestExifTool.readAllTags(file);
            } catch (RuntimeException ex) {
                // ExifTool problems - then there is just nothing to show
                tags = Map.of();
            }
            final List<Map.Entry<String, String>> rows = List.copyOf(tags.entrySet());
            Platform.runLater(() -> {
                if (file.equals(current)) {
                    table.getItems().setAll(rows);
                    table.setPlaceholder(new Label("No tags found"));
                }
            });
        });
    }

    /**
     * Reads the shown file again - e.g. after saving.
     */
    public void reload() {
        final Path file = current;
        current = null;
        show(file);
    }

    /**
     * Stops the background thread - called when GeoTagger ends.
     */
    public void dispose() {
        executor.shutdownNow();
    }
}
```

- [ ] **Step 3: Kompilieren**

Run: `.\gradlew.bat build`
Expected: BUILD SUCCESSFUL, 114 Tests grün, keine Compilerwarnungen. Die beiden Klassen sind noch nicht eingebunden, das folgt in Task 6.

- [ ] **Step 4: Commit**

```powershell
& $git -C $app add src
& $git -C $app commit -m "Details and image info panes"
```

---

### Task 6: Hauptfenster: Bearbeiten, Undo/Redo, Speichern, Rückfragen

**Files:**
- Modify (komplett ersetzen): `src\main\resources\GeoTagger.fxml`, `src\main\java\tf\geotagger\main\GeoTagger.java`
- Modify: `src\main\java\tf\geotagger\main\GeoTaggerPreferences.java`, `src\main\java\tf\geotagger\main\PreviewPane.java`

**Interfaces:**
- Consumes: alle vorherigen Tasks, außerdem `MetadataWriter`, `SaveResult` (Plan 1), JavaHelper `DoUndoManager`, `ShowAlerts`, `UsefulKeyCodes`
- Produces: die bedienbare Anwendung mit Bearbeiten und Speichern

- [ ] **Step 1: Preferences und `PreviewPane` anpassen**

In `GeoTaggerPreferences.java` nach der Zeile

```java
    RECENT_CENTRAL_DIVIDER_POS("recentCentralDividerPos", Double.toString(0.5), Double.class),
```

einfügen:

```java
    // map vs. details / image info
    RECENT_RIGHT_DIVIDER_POS("recentRightDividerPos", Double.toString(0.5), Double.class),
```

In `PreviewPane.java` in `show(MediaFile, List<MediaFile>)` ersetzen:

```java
        final Path path = file.path();
        current = path;
```

durch:

```java
        final Path path = file.path();
        if (path.equals(current)) {
            // already shown or loading - e.g. when the selection is restored after a change
            return;
        }
        current = path;
```

- [ ] **Step 2: FXML ersetzen**

`src\main\resources\GeoTagger.fxml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>

<?import javafx.geometry.Insets?>
<?import javafx.scene.control.Button?>
<?import javafx.scene.control.CheckBox?>
<?import javafx.scene.control.Label?>
<?import javafx.scene.control.ProgressBar?>
<?import javafx.scene.control.ScrollPane?>
<?import javafx.scene.control.Separator?>
<?import javafx.scene.control.SplitMenuButton?>
<?import javafx.scene.control.SplitPane?>
<?import javafx.scene.control.Tab?>
<?import javafx.scene.control.TabPane?>
<?import javafx.scene.control.TableColumn?>
<?import javafx.scene.control.TableView?>
<?import javafx.scene.control.ToolBar?>
<?import javafx.scene.layout.BorderPane?>
<?import javafx.scene.layout.HBox?>
<?import javafx.scene.layout.StackPane?>
<?import javafx.scene.layout.VBox?>
<?import tf.geotagger.main.DetailsPane?>
<?import tf.geotagger.main.ImageInfoPane?>
<?import tf.geotagger.main.PreviewPane?>

<BorderPane xmlns="http://javafx.com/javafx" xmlns:fx="http://javafx.com/fxml" fx:controller="tf.geotagger.main.GeoTagger">
    <top>
        <ToolBar>
            <SplitMenuButton fx:id="openFolderButton" text="Open folder..." onAction="#openFolder" disable="true"/>
            <CheckBox fx:id="includeSubfoldersCheck" text="Include subfolders"/>
            <Separator orientation="VERTICAL"/>
            <Button fx:id="saveButton" text="Save" onAction="#save" disable="true"/>
            <Button fx:id="undoButton" text="Undo" onAction="#undo" disable="true"/>
            <Button fx:id="redoButton" text="Redo" onAction="#redo" disable="true"/>
            <Separator orientation="VERTICAL"/>
            <Button fx:id="preferencesButton" text="Preferences..." onAction="#showPreferences"/>
        </ToolBar>
    </top>
    <center>
        <SplitPane fx:id="centralSplit">
            <SplitPane fx:id="leftSplit" orientation="VERTICAL">
                <VBox>
                    <HBox fx:id="filterBox" spacing="4">
                        <padding>
                            <Insets top="4" right="4" bottom="4" left="4"/>
                        </padding>
                    </HBox>
                    <TableView fx:id="mediaTable" VBox.vgrow="ALWAYS">
                        <columns>
                            <TableColumn fx:id="nameColumn" text="Name" prefWidth="260"/>
                            <TableColumn fx:id="captureTimeColumn" text="Capture time" prefWidth="150"/>
                            <TableColumn fx:id="placeColumn" text="Place" prefWidth="140"/>
                            <TableColumn fx:id="coordinatesStatusColumn" text="GPS" prefWidth="45"/>
                            <TableColumn fx:id="placeStatusColumn" text="Loc." prefWidth="45"/>
                            <TableColumn fx:id="altitudeStatusColumn" text="Alt." prefWidth="45"/>
                        </columns>
                        <placeholder>
                            <Label text="Open a folder to see its photos and videos"/>
                        </placeholder>
                    </TableView>
                </VBox>
                <PreviewPane fx:id="previewPane"/>
            </SplitPane>
            <SplitPane fx:id="rightSplit" orientation="VERTICAL">
                <StackPane>
                    <Label text="The map follows in the next step" wrapText="true"/>
                </StackPane>
                <TabPane fx:id="infoTabs" tabClosingPolicy="UNAVAILABLE">
                    <Tab text="Details">
                        <ScrollPane fitToWidth="true">
                            <DetailsPane fx:id="detailsPane"/>
                        </ScrollPane>
                    </Tab>
                    <Tab fx:id="imageInfoTab" text="Image info">
                        <ImageInfoPane fx:id="imageInfoPane"/>
                    </Tab>
                </TabPane>
            </SplitPane>
        </SplitPane>
    </center>
    <bottom>
        <HBox spacing="8" alignment="CENTER_LEFT">
            <padding>
                <Insets top="4" right="8" bottom="4" left="8"/>
            </padding>
            <Label fx:id="statusLabel" maxWidth="Infinity" HBox.hgrow="ALWAYS"/>
            <ProgressBar fx:id="progressBar" prefWidth="200" visible="false"/>
        </HBox>
    </bottom>
</BorderPane>
```

- [ ] **Step 3: Controller ersetzen**

`src\main\java\tf\geotagger\main\GeoTagger.java`:

```java
package tf.geotagger.main;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Predicate;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.MultipleSelectionModel;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SplitMenuButton;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import tf.exiftool.ExifTool;
import tf.exiftool.ExifToolException;
import tf.geotagger.edit.ChangeFieldsAction;
import tf.geotagger.files.FolderScanner;
import tf.geotagger.metadata.ExifToolLocator;
import tf.geotagger.metadata.MetadataReader;
import tf.geotagger.metadata.MetadataWriter;
import tf.geotagger.metadata.SaveResult;
import tf.geotagger.metadata.SaveSummary;
import tf.geotagger.model.CaptureTime;
import tf.geotagger.model.Field;
import tf.geotagger.model.MediaFile;
import tf.geotagger.model.MediaFilter;
import tf.geotagger.preview.PreviewProvider;
import tf.helper.doundo.DoUndoManager;
import tf.helper.javafx.ShowAlerts;
import tf.helper.javafx.TableViewPreferences;
import tf.helper.javafx.UsefulKeyCodes;

/**
 * Controller of the main window (GeoTagger.fxml): ExifTool start, folder loading, file list with filters, preview,
 * details / image info, undo / redo and saving.
 */
public class GeoTagger implements Initializable {
    private static final String TABLE_PREFERENCES = "mediaTable";
    private static final DateTimeFormatter CAPTURE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String YES = "\u2713";
    private static final String NO = "\u2013";
    // field changed, not saved yet
    private static final String CHANGED = "\u25CF";
    private static final Field[] PLACE_FIELDS = Field.PLACE_FIELDS.toArray(new Field[0]);
    // extracted previews of RAW / HEIC / videos - emptied when GeoTagger ends
    private static final Path PREVIEW_CACHE = Path.of(System.getProperty("java.io.tmpdir"), "GeoTagger", "preview");

    @FXML
    private SplitMenuButton openFolderButton;
    @FXML
    private CheckBox includeSubfoldersCheck;
    @FXML
    private Button saveButton;
    @FXML
    private Button undoButton;
    @FXML
    private Button redoButton;
    @FXML
    private Button preferencesButton;
    @FXML
    private SplitPane centralSplit;
    @FXML
    private SplitPane leftSplit;
    @FXML
    private SplitPane rightSplit;
    @FXML
    private HBox filterBox;
    @FXML
    private TableView<MediaFile> mediaTable;
    @FXML
    private TableColumn<MediaFile, String> nameColumn;
    @FXML
    private TableColumn<MediaFile, String> captureTimeColumn;
    @FXML
    private TableColumn<MediaFile, String> placeColumn;
    @FXML
    private TableColumn<MediaFile, String> coordinatesStatusColumn;
    @FXML
    private TableColumn<MediaFile, String> placeStatusColumn;
    @FXML
    private TableColumn<MediaFile, String> altitudeStatusColumn;
    @FXML
    private Label statusLabel;
    @FXML
    private ProgressBar progressBar;
    @FXML
    private PreviewPane previewPane;
    @FXML
    private Tab imageInfoTab;
    @FXML
    private DetailsPane detailsPane;
    @FXML
    private ImageInfoPane imageInfoPane;

    private final ObservableList<MediaFile> files = FXCollections.observableArrayList();
    private final FilteredList<MediaFile> filteredFiles = new FilteredList<>(files);
    private final ToggleGroup filterGroup = new ToggleGroup();
    private final Map<MediaFilter, ToggleButton> filterButtons = new EnumMap<>(MediaFilter.class);
    // one worker thread for loading and saving - ExifTool handles one command at a time anyway
    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        final Thread thread = new Thread(runnable, "GeoTagger-worker");
        thread.setDaemon(true);
        return thread;
    });

    private Stage stage;
    // folder the files were loaded from - names are shown relative to it
    private Path folder;
    private boolean busy = false;
    private ExifTool exifTool;
    private MetadataReader reader;
    private MetadataWriter writer;
    private PreviewProvider previewProvider;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        initTable();
        initFilters();
        initPreview();
        initDetails();
        updateRecentFolders();
        DoUndoManager.getInstance().changeCountProperty().addListener((observable, oldValue, newValue) -> updateButtons());
    }

    /**
     * Called once the stage is shown: restores the layout, starts ExifTool and guards against losing changes.
     *
     * @param stage the main window
     */
    public void lateInitialize(Stage stage) {
        this.stage = stage;
        final Boolean includeSubfolders = GeoTaggerPreferences.INCLUDE_SUBFOLDERS.getAsType();
        includeSubfoldersCheck.setSelected(includeSubfolders);
        final Double centralDivider = GeoTaggerPreferences.RECENT_CENTRAL_DIVIDER_POS.getAsType();
        centralSplit.setDividerPositions(centralDivider);
        final Double leftDivider = GeoTaggerPreferences.RECENT_LEFT_DIVIDER_POS.getAsType();
        leftSplit.setDividerPositions(leftDivider);
        final Double rightDivider = GeoTaggerPreferences.RECENT_RIGHT_DIVIDER_POS.getAsType();
        rightSplit.setDividerPositions(rightDivider);
        TableViewPreferences.loadTableViewPreferences(mediaTable, TABLE_PREFERENCES, GeoTaggerPreferences.INSTANCE);

        // like GPXEditor - text fields handle ctrl+z themselves, the rest of the window gets undo / redo
        stage.getScene().addEventHandler(KeyEvent.KEY_PRESSED, event -> {
            if (UsefulKeyCodes.CNTRL_Z.match(event)) {
                undo();
            }
            if (UsefulKeyCodes.CNTRL_Y.match(event)) {
                redo();
            }
        });
        stage.setOnCloseRequest(event -> {
            if (busy) {
                // loading or saving - closing now could lose changes
                event.consume();
            } else if (hasUnsavedChanges()) {
                event.consume();
                confirmUnsavedChanges(stage::close);
            }
        });
        initExifTool();
    }

    /**
     * Saves the layout and stops background work - called when GeoTagger ends.
     */
    public void stop() {
        GeoTaggerPreferences.RECENT_CENTRAL_DIVIDER_POS.put(centralSplit.getDividerPositions()[0]);
        GeoTaggerPreferences.RECENT_LEFT_DIVIDER_POS.put(leftSplit.getDividerPositions()[0]);
        GeoTaggerPreferences.RECENT_RIGHT_DIVIDER_POS.put(rightSplit.getDividerPositions()[0]);
        GeoTaggerPreferences.INCLUDE_SUBFOLDERS.put(includeSubfoldersCheck.isSelected());
        TableViewPreferences.saveTableViewPreferences(mediaTable, TABLE_PREFERENCES, GeoTaggerPreferences.INSTANCE);
        worker.shutdownNow();
        previewPane.dispose();
        imageInfoPane.dispose();
        closeExifTool();
        if (previewProvider != null) {
            previewProvider.clearCache();
        }
    }

    private void initTable() {
        final SortedList<MediaFile> sortedFiles = new SortedList<>(filteredFiles);
        sortedFiles.comparatorProperty().bind(mediaTable.comparatorProperty());
        mediaTable.setItems(sortedFiles);
        mediaTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        nameColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(displayName(cell.getValue())));
        captureTimeColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(captureTime(cell.getValue())));
        placeColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(place(cell.getValue())));
        coordinatesStatusColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                status(cell.getValue(), cell.getValue().hasCoordinates(), Field.LATITUDE, Field.LONGITUDE)));
        placeStatusColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                status(cell.getValue(), cell.getValue().hasPlace(), PLACE_FIELDS)));
        altitudeStatusColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                status(cell.getValue(), cell.getValue().hasAltitude(), Field.ALTITUDE)));
    }

    private void initFilters() {
        for (MediaFilter filter : MediaFilter.values()) {
            final ToggleButton button = new ToggleButton(filter.label());
            button.setToggleGroup(filterGroup);
            button.setUserData(filter);
            filterButtons.put(filter, button);
            filterBox.getChildren().add(button);
        }
        filterGroup.selectedToggleProperty().addListener((observable, oldToggle, newToggle) -> {
            if (newToggle == null) {
                // one filter is always active
                oldToggle.setSelected(true);
                return;
            }
            filteredFiles.setPredicate((MediaFilter) newToggle.getUserData());
        });
        filterButtons.get(MediaFilter.ALL).setSelected(true);
        updateFilterCounts();
    }

    private void updateFilterCounts() {
        MediaFilter.counts(files).forEach((filter, count) -> filterButtons.get(filter).setText(filter.label() + " (" + count + ")"));
    }

    private void initPreview() {
        mediaTable.getSelectionModel().selectedItemProperty().addListener((observable, oldFile, newFile) -> showPreview(newFile));
        previewPane.getFirstButton().setOnAction(event -> select(0));
        previewPane.getPreviousButton().setOnAction(event -> select(mediaTable.getSelectionModel().getSelectedIndex() - 1));
        previewPane.getNextButton().setOnAction(event -> select(mediaTable.getSelectionModel().getSelectedIndex() + 1));
        previewPane.getLastButton().setOnAction(event -> select(mediaTable.getItems().size() - 1));
    }

    private void showPreview(MediaFile file) {
        if (file == null) {
            previewPane.clear();
            return;
        }
        final List<MediaFile> items = mediaTable.getItems();
        final int index = items.indexOf(file);
        final List<MediaFile> neighbours = new ArrayList<>();
        if (index > 0) {
            neighbours.add(items.get(index - 1));
        }
        if (index >= 0 && index + 1 < items.size()) {
            neighbours.add(items.get(index + 1));
        }
        previewPane.show(file, neighbours);
    }

    private void initDetails() {
        mediaTable.getSelectionModel().getSelectedItems().addListener((ListChangeListener<MediaFile>) change -> selectionChanged());
        imageInfoTab.setOnSelectionChanged(event -> showImageInfo());
        detailsPane.setOnEdit((field, value) -> perform(ChangeFieldsAction.sameValue(
                "Set " + DetailsPane.label(field).toLowerCase(Locale.ROOT), selectedFiles(), field, value)));
    }

    private void selectionChanged() {
        detailsPane.setFiles(selectedFiles());
        showImageInfo();
    }

    private void showImageInfo() {
        // reading all tags takes a moment - only while the tab is visible
        if (imageInfoTab.isSelected()) {
            final MediaFile file = mediaTable.getSelectionModel().getSelectedItem();
            imageInfoPane.show(file == null ? null : file.path());
        }
    }

    private List<MediaFile> selectedFiles() {
        return mediaTable.getSelectionModel().getSelectedItems().stream().filter(Objects::nonNull).toList();
    }

    /**
     * Executes a change as one undo / redo step and shows its result.
     *
     * @param action change of pending values - not done yet
     */
    private void perform(ChangeFieldsAction action) {
        if (folder == null) {
            return;
        }
        action.doAction();
        DoUndoManager.getInstance().addDoneAction(action, undoKey());
        refreshFiles();
    }

    @FXML
    private void undo() {
        if (!busy && folder != null && DoUndoManager.getInstance().canUndo(undoKey())) {
            DoUndoManager.getInstance().singleUndo(undoKey());
            refreshFiles();
        }
    }

    @FXML
    private void redo() {
        if (!busy && folder != null && DoUndoManager.getInstance().canDo(undoKey())) {
            DoUndoManager.getInstance().singleDo(undoKey());
            refreshFiles();
        }
    }

    private String undoKey() {
        return "GeoTagger:" + folder;
    }

    // MediaFile has no change events: after each change re-apply the filter, redraw the rows, update counts and
    // buttons - and keep the selection
    private void refreshFiles() {
        final MultipleSelectionModel<MediaFile> selection = mediaTable.getSelectionModel();
        final List<MediaFile> selected = selectedFiles();
        final MediaFile focused = selection.getSelectedItem();

        final Predicate<? super MediaFile> predicate = filteredFiles.getPredicate();
        filteredFiles.setPredicate(null);
        filteredFiles.setPredicate(predicate);

        final List<MediaFile> items = mediaTable.getItems();
        selection.clearSelection();
        for (MediaFile file : selected) {
            if (file != focused && items.contains(file)) {
                selection.select(file);
            }
        }
        // select the focused file last - it stays the one shown in preview and image info
        if (focused != null && items.contains(focused)) {
            selection.select(focused);
        }

        mediaTable.refresh();
        updateFilterCounts();
        updateButtons();
        detailsPane.setFiles(selectedFiles());
    }

    private boolean hasUnsavedChanges() {
        return files.stream().anyMatch(MediaFile::isChanged);
    }

    private void updateButtons() {
        final long changed = files.stream().filter(MediaFile::isChanged).count();
        saveButton.setText(changed == 0 ? "Save" : "Save (" + changed + ")");
        saveButton.setDisable(busy || changed == 0);
        undoButton.setDisable(busy || folder == null || !DoUndoManager.getInstance().canUndo(undoKey()));
        redoButton.setDisable(busy || folder == null || !DoUndoManager.getInstance().canDo(undoKey()));
    }

    private void initExifTool() {
        if (GeoTaggerPreferences.EXIFTOOL_PATH.getAsString().isBlank()) {
            ExifToolLocator.findOnPath().ifPresent(found -> GeoTaggerPreferences.EXIFTOOL_PATH.put(found.toString()));
        }
        if (!applyExifTool()) {
            ShowAlerts.getInstance().showAlert(Alert.AlertType.WARNING, GeoTaggerManager.TITLE, "ExifTool not found",
                    "GeoTagger reads and writes metadata with ExifTool. Please select the ExifTool executable.");
            showPreferences();
        }
    }

    /**
     * (Re)starts ExifTool with the executable from the preferences.
     *
     * @return true if ExifTool is usable - otherwise no folder can be opened
     */
    private boolean applyExifTool() {
        closeExifTool();
        final String configured = GeoTaggerPreferences.EXIFTOOL_PATH.getAsString();
        try {
            final Path executable = Path.of(configured);
            final String version = ExifToolLocator.version(executable);
            exifTool = new ExifTool(executable);
            reader = new MetadataReader(exifTool);
            writer = new MetadataWriter(exifTool, reader);
            previewProvider = new PreviewProvider(exifTool, PREVIEW_CACHE);
            previewPane.setPreviewProvider(previewProvider);
            imageInfoPane.setExifTool(exifTool);
            openFolderButton.setDisable(false);
            statusLabel.setText("ExifTool " + version + " - " + executable);
            return true;
        } catch (ExifToolException | InvalidPathException ex) {
            previewPane.setPreviewProvider(null);
            imageInfoPane.setExifTool(null);
            openFolderButton.setDisable(true);
            statusLabel.setText("ExifTool not usable - please check the preferences");
            return false;
        }
    }

    private void closeExifTool() {
        if (exifTool != null) {
            exifTool.close();
            exifTool = null;
            reader = null;
            writer = null;
        }
    }

    @FXML
    private void showPreferences() {
        if (PreferenceEditor.getInstance().editPreferences()) {
            applyExifTool();
        }
    }

    @FXML
    private void openFolder() {
        confirmUnsavedChanges(() -> {
            final DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("Open folder");
            final File recent = new File(GeoTaggerPreferences.RECENT_FOLDER.getAsString());
            if (recent.isDirectory()) {
                chooser.setInitialDirectory(recent);
            }
            final File selected = chooser.showDialog(stage);
            if (selected != null) {
                loadAndRemember(selected.toPath());
            }
        });
    }

    private void loadAndRemember(Path newFolder) {
        GeoTaggerPreferences.RECENT_FOLDER.put(newFolder.toString());
        GeoTaggerPreferences.INCLUDE_SUBFOLDERS.put(includeSubfoldersCheck.isSelected());
        loadFolder(newFolder, includeSubfoldersCheck.isSelected());
    }

    // the drop down of "Open folder..." lists the recently opened folders, most recent first
    private void updateRecentFolders() {
        openFolderButton.getItems().clear();
        for (String recent : GeoTaggerPreferences.getRecentFolders().getRecentFiles()) {
            final MenuItem item = new MenuItem(recent);
            // the text is a path - no mnemonics
            item.setMnemonicParsing(false);
            item.setOnAction(event -> confirmUnsavedChanges(() -> loadAndRemember(Path.of(recent))));
            openFolderButton.getItems().add(item);
        }
        if (openFolderButton.getItems().isEmpty()) {
            final MenuItem none = new MenuItem("No recent folders");
            none.setDisable(true);
            openFolderButton.getItems().add(none);
        }
    }

    private void loadFolder(Path newFolder, boolean includeSubfolders) {
        final MetadataReader folderReader = reader;
        final Task<List<MediaFile>> task = new Task<>() {
            @Override
            protected List<MediaFile> call() throws IOException {
                updateMessage("Searching " + newFolder + " ...");
                final List<Path> mediaFiles = FolderScanner.scan(newFolder, includeSubfolders).mediaFiles();
                final int total = mediaFiles.size();
                updateProgress(0, total);
                return folderReader.read(mediaFiles, done -> {
                    updateProgress(done, total);
                    updateMessage("Reading metadata: " + done + " / " + total);
                });
            }
        };
        task.setOnSucceeded(event -> {
            finishTask();
            // the undo history belongs to the files of a folder
            DoUndoManager.getInstance().clear(undoKey());
            folder = newFolder;
            DoUndoManager.getInstance().clear(undoKey());
            files.setAll(task.getValue());
            refreshFiles();
            stage.setTitle(GeoTaggerManager.TITLE + " - " + newFolder);
            statusLabel.setText(files.size() + " files in " + newFolder);
            GeoTaggerPreferences.getRecentFolders().addRecentFile(newFolder.toString());
            updateRecentFolders();
            select(0);
        });
        task.setOnFailed(event -> {
            finishTask();
            statusLabel.setText("Loading " + newFolder + " failed");
            ShowAlerts.getInstance().showAlert(Alert.AlertType.ERROR, GeoTaggerManager.TITLE, "Can't load " + newFolder,
                    String.valueOf(task.getException().getMessage()));
        });
        runTask(task);
    }

    @FXML
    private void save() {
        saveChanges(null);
    }

    /**
     * Writes all pending changes in the background.
     *
     * @param afterSuccess what to do once everything is saved (e.g. close the window), may be null - not called if a
     * file couldn't be saved
     */
    private void saveChanges(Runnable afterSuccess) {
        final List<MediaFile> changed = files.stream().filter(MediaFile::isChanged).toList();
        if (changed.isEmpty() || writer == null) {
            if (afterSuccess != null) {
                afterSuccess.run();
            }
            return;
        }
        final MetadataWriter fileWriter = writer;
        final Task<SaveResult> task = new Task<>() {
            @Override
            protected SaveResult call() {
                final int total = changed.size();
                updateMessage("Saving " + total + " files ...");
                updateProgress(0, total);
                return fileWriter.save(changed, done -> {
                    updateProgress(done, total);
                    updateMessage("Saving: " + done + " / " + total);
                });
            }
        };
        task.setOnSucceeded(event -> {
            finishTask();
            final SaveResult result = task.getValue();
            // the saved values are the new originals - an undo of older steps would only confuse now
            DoUndoManager.getInstance().clear(undoKey());
            refreshFiles();
            imageInfoPane.reload();
            final String summary = SaveSummary.text(result);
            statusLabel.setText(summary.lines().findFirst().orElse(""));
            if (SaveSummary.hasProblems(result)) {
                ShowAlerts.getInstance().showAlert(Alert.AlertType.WARNING, GeoTaggerManager.TITLE,
                        "Not all files were saved", summary);
            } else if (afterSuccess != null) {
                afterSuccess.run();
            }
        });
        task.setOnFailed(event -> {
            finishTask();
            refreshFiles();
            statusLabel.setText("Saving failed");
            ShowAlerts.getInstance().showAlert(Alert.AlertType.ERROR, GeoTaggerManager.TITLE, "Saving failed",
                    String.valueOf(task.getException().getMessage()));
        });
        runTask(task);
    }

    /**
     * Asks what to do with unsaved changes before they would get lost - see spec, section "Fehlerbehandlung".
     *
     * @param proceed what to do afterwards (e.g. open another folder) - not called on "Cancel" or if saving failed
     */
    private void confirmUnsavedChanges(Runnable proceed) {
        final long changed = files.stream().filter(MediaFile::isChanged).count();
        if (changed == 0) {
            proceed.run();
            return;
        }
        final ButtonType saveChanges = new ButtonType("Save", ButtonBar.ButtonData.YES);
        final ButtonType discard = new ButtonType("Discard", ButtonBar.ButtonData.NO);
        final Optional<ButtonType> answer = ShowAlerts.getInstance().showAlert(Alert.AlertType.CONFIRMATION,
                GeoTaggerManager.TITLE, changed + " files have unsaved changes", "Save them before continuing?",
                saveChanges, discard, ButtonType.CANCEL);
        if (answer.isEmpty() || answer.get() == ButtonType.CANCEL) {
            return;
        }
        if (answer.get() == saveChanges) {
            saveChanges(proceed);
        } else {
            proceed.run();
        }
    }

    private void runTask(Task<?> task) {
        statusLabel.textProperty().bind(task.messageProperty());
        progressBar.progressProperty().bind(task.progressProperty());
        progressBar.setVisible(true);
        setBusy(true);
        worker.submit(task);
    }

    private void finishTask() {
        statusLabel.textProperty().unbind();
        progressBar.progressProperty().unbind();
        progressBar.setVisible(false);
        setBusy(false);
    }

    private void setBusy(boolean busy) {
        this.busy = busy;
        openFolderButton.setDisable(busy);
        includeSubfoldersCheck.setDisable(busy);
        preferencesButton.setDisable(busy);
        // the files are changed in the background while saving - no edits meanwhile
        mediaTable.setDisable(busy);
        detailsPane.setDisable(busy);
        updateButtons();
    }

    private void select(int index) {
        if (index >= 0 && index < mediaTable.getItems().size()) {
            mediaTable.getSelectionModel().clearAndSelect(index);
            mediaTable.scrollTo(index);
        }
    }

    private String displayName(MediaFile file) {
        // with sub folders the path below the opened folder tells the files apart
        return folder == null ? file.path().getFileName().toString() : folder.relativize(file.path()).toString();
    }

    private static String captureTime(MediaFile file) {
        final CaptureTime time = file.captureTime();
        if (time == null) {
            return "";
        }
        return CAPTURE_TIME.format(time.dateTime()) + (time.utc() ? " UTC" : "");
    }

    private static String place(MediaFile file) {
        final String city = file.text(Field.CITY);
        return city != null ? city : Objects.requireNonNullElse(file.text(Field.COUNTRY), "");
    }

    private static String status(MediaFile file, boolean present, Field... fields) {
        final Map<Field, Object> pending = file.pendingChanges();
        for (Field field : fields) {
            if (pending.containsKey(field)) {
                return CHANGED;
            }
        }
        return present ? YES : NO;
    }
}
```

- [ ] **Step 4: Bauen und testen**

Run: `.\gradlew.bat build`
Expected: BUILD SUCCESSFUL, 114 Tests grün, keine Compilerwarnungen.

- [ ] **Step 5: Manuell prüfen**

Mit **Kopien** von Fotos arbeiten, nicht mit den Originalen. Zum Beispiel `src\test\resources\samples` in einen Testordner kopieren. Dann `.\gradlew.bat run` und den Testordner öffnen.

Prüfen:
1. **Layout:** Rechts oben steht der Platzhalter für die Karte, darunter die Reiter „Details“ und „Image info“. Die Teilungen bleiben nach einem Neustart erhalten.
2. **Details, eine Datei:**
   - Die Felder zeigen die Werte der Datei.
   - City ändern und Enter drücken: In der Liste wird „Loc.“ zu ●, „Save (1)“ wird aktiv, die Spalte „Place“ zeigt den neuen Ort.
   - Latitude `91` eingeben: Das Feld wird rot, der Tooltip nennt den Grund, nichts wird geändert.
   - `46,5` wird als `46.5` übernommen.
3. **Details, mehrere Dateien:**
   - Mehrere Dateien mit unterschiedlichem Ort auswählen: City ist leer mit „(different)“.
   - Einen Wert eingeben: Alle ausgewählten Dateien bekommen ihn, „Save (n)“ zählt sie, die Auswahl bleibt erhalten.
4. **Filter:** Im Filter „Without place“ einer Datei einen Ort geben. Sie verschwindet aus der Liste, die Zahlen der Filter stimmen.
5. **Undo/Redo:**
   - „Undo“ bzw. Strg+Z außerhalb eines Textfelds stellt den vorherigen Wert wieder her, „Redo“ bzw. Strg+Y wiederholt ihn.
   - Nach mehreren Undos ist ● wieder ✓/– und „Save“ inaktiv, wenn alles zurückgenommen ist.
6. **Image info:**
   - Den Reiter wählen: Die Tags der ausgewählten Datei erscheinen, z. B. `System:FileName`, `XMP-photoshop:City`.
   - Beim Blättern folgen die Tags der Auswahl. Ist der Reiter nicht sichtbar, wird nichts gelesen.
7. **Speichern:**
   - „Save (n)“: Fortschritt in der Statusleiste, danach „n saved“. ● wird zu ✓, „Undo“ ist inaktiv, „Image info“ zeigt die geschriebenen Werte.
   - `*_original` liegt neben den Dateien.
8. **Von außen geändert:** Eine Datei ändern, z. B. mit `exiftool -XMP-photoshop:City=X datei.jpg`, dann in GeoTagger ein anderes Feld derselben Datei ändern und speichern. Es kommt die Meldung „Not all files were saved“ mit dem Abschnitt „Changed outside GeoTagger …“, und die Datei behält ihr ●.
9. **Ordnerwechsel mit Änderungen:**
   - „Open folder...“ oder ein Eintrag aus der Liste fragt „n files have unsaved changes“. „Cancel“ bleibt im Ordner, „Discard“ wechselt ohne Speichern, „Save“ speichert und wechselt danach.
10. **Schließen mit Änderungen:** Dieselbe Rückfrage. „Save“ speichert und schließt, „Cancel“ lässt das Fenster offen.

- [ ] **Step 6: Commit**

```powershell
& $git -C $app add src
& $git -C $app commit -m "Edit details with undo / redo, image info, save with summary, ask before losing changes"
```

---

## Abschluss von Plan 3b

- **ExifToolHelper:** 12 Tests grün.
- **GeoTagger:** 114 Tests grün, keine Compilerwarnungen. Die Javadoc-Prüfung mit dem Doclint-Init-Skript findet nichts.
- Die Checkliste aus Task 6 ist abgehakt.
- Danach Plan 4 (Karte in GeoHelper und Einbindung) auf Basis des entstandenen Codes schreiben.

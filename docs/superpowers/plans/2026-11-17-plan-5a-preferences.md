# GeoTagger Plan 5a: Preferences über JavaHelper – Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Der gemeinsame Teil der Preferences wandert nach JavaHelper:
- ein Speicher über einen `java.util.prefs`-Knoten;
- ein Interface mit Default-Methoden für typisierte Einträge.

`GeoTaggerPreferences` braucht danach nur noch die Liste der Einträge. GPXEditor stellt später auf dasselbe um. Das kommt jetzt, bevor Plan 5b und weitere GeoSetter-Einstellungen noch mehr Einträge im alten Muster hinzufügen.

**Architecture:**
- **Bisher:** `GPXEditorPreferences` und `GeoTaggerPreferences` sind Enums mit denselben zehn Methoden: `getAsString`, `getAsType`, `put(T)`, `getDefaultAsType` sowie `get`, `put`, `clear`, `remove`, `exportPreferences`, `importPreferences` über einen `Preferences`-Knoten. JavaHelper hat nur das Interface `IPreferencesStore`.
- **Neu in JavaHelper** (`tf.helper.general`, Version bleibt 1.18):
  - `NodePreferencesStore implements IPreferencesStore`;
  - `ITypedPreference` mit Default-Methoden.
- **GeoTagger:**
  - Das Enum implementiert nur noch `ITypedPreference`.
  - Den Speicher liefert `GeoTaggerPreferences.store()`. Der Pseudo-Eintrag `INSTANCE` entfällt.
  - Auswahl-Einstellungen werden als Enum typisiert, für den Anfang `PLACE_SERVICE`.

**Tech Stack:** JavaHelper (Gradle, JUnit 4 wie die vorhandenen Tests), GeoTagger wie Plan 5c.

**Spec:** [2026-11-03-geotagger-design.md](../specs/2026-11-03-geotagger-design.md), „Architektur / Repositories“ (JavaHelper: Preferences). Plan 5b baut auf diesem Plan auf.

## Global Constraints

- **JavaHelper:**
  - Repo `D:\Thomas\Development\NetBeansProjects\JavaHelper` (`$jh`), Branch `NoRestForTheWicked`, direkt dort arbeiten.
  - Version **1.18 bleibt** (nur der Nutzer erhöht).
  - `gradlew build` testet und veröffentlicht nach mavenLocal, Dauer etwa 7 Minuten.
  - Die lokal geänderte `gradle.properties` des Nutzers und sein noch nicht gepushter Commit bleiben unberührt. Committet werden nur die Dateien dieses Plans; der Push ist Sache des Nutzers.
- **Verhalten bleibt gleich:**
  - Gespeicherte Werte und Schlüssel bleiben gleich; ein vorhandenes `placeService = GEONAMES` wird als Enum gelesen.
  - Ein ungültiger Wert ergibt wie bisher den Default.
  - Der Test-Knoten `tf/geotagger/test` bleibt.
- **Neu:** `put` eines Enums speichert `name()`, nicht `toString()`. Damit sind Enums mit eigenem `toString` (Anzeigenamen) sicher; bei Enums ohne eigenes `toString` ist das Ergebnis gleich.
- **GPXEditor** wird in diesem Plan nicht geändert. Die Umstellung kommt auf seine Änderungsliste (Task 3).
- Alles andere aus Plan 5 / 5c gilt weiter: Lizenz-Header, vollständiges Javadoc, keine Compilerwarnungen, UI-Tests gezielt und die gesamte Suite am Ende.

## Review Focus

1. **Alte gespeicherte Werte** lesen sich unverändert. Das gilt für Zahlen, Booleans und den Ortsdienst als String „GEONAMES“ bzw. „MAPBOX“. → `TestTypedPreference.storedNamesAreReadAsEnum` (Task 1), `PlaceServiceFactoryTest` (Task 2)
2. **Export/Import** über den neuen Speicher funktioniert wie zuvor; ein Import setzt die Werte im Knoten. → `TestNodePreferencesStore.exportAndImport` (Task 1)
3. **Tests und echte Einstellungen bleiben getrennt:** `clear()` in den Tests trifft nur `tf/geotagger/test`. → bestehende Absicherung in `UiTestSupport.preparePreferences` und `PlaceServiceFactoryTest` (Task 2)
4. **Tabellen- und Fenstereinstellungen** (`TableViewPreferences`, `RecentFiles`) laufen über `store()` weiter. → `GeoTaggerUiTest.oldColumnSettingsAreIgnored`, gesamte UI-Suite (Task 2)

---

### Task 1: JavaHelper – Speicher und typisierte Einträge

**Files:**
- Create: `$jh\src\main\java\tf\helper\general\NodePreferencesStore.java`, `$jh\src\main\java\tf\helper\general\ITypedPreference.java`
- Test: `$jh\src\test\java\tf\helper\general\TestNodePreferencesStore.java`, `$jh\src\test\java\tf\helper\general\TestTypedPreference.java`

**Interfaces (Produces):**
- `public class NodePreferencesStore implements IPreferencesStore`:
  - Konstruktor `(Preferences node)`, `Preferences node()`;
  - `get`, `put`, `clear`, `remove`, `exportPreferences` (`exportSubtree`), `importPreferences` (`Preferences.importPreferences`).
  - Fehlerbehandlung wie bisher in beiden Apps: `SecurityException` bei `get` ergibt den Default, `BackingStoreException` und `IOException` werden geloggt.
- `public interface ITypedPreference`:
  - zu implementieren: `String getKey()`, `String getDefaultValue()`, `Class<?> getValueClass()`, `IPreferencesStore getStore()`;
  - Default-Methoden:
    - `String getAsString()`;
    - `<T> T getAsType()`: `GeneralParser.parse` mit Rückfall auf `getDefaultAsType()` bei jeder Ausnahme;
    - `<T> T getDefaultAsType()`;
    - `<T> void put(T value)`: Enums als `name()`, sonst `toString()`.

- [ ] **Step 1: Fehlschlagende Tests** (JUnit 4)
  - `TestNodePreferencesStore` mit dem Knoten `Preferences.userRoot().node("tf/javahelper/test/" + UUID)`, nach jedem Test `removeNode()`:
    - `putGetRemoveClear`
    - `exportAndImport`: exportieren, `clear`, importieren → Werte wieder da
  - `TestTypedPreference`, ein Test-Enum über einen Map-Speicher:
    - `typedValues`: Double, Boolean, Integer, String
    - `invalidValueGivesTheDefault`
    - `enumIsStoredByName`: Enum mit eigenem `toString` → gespeichert wird `name()`
    - `storedNamesAreReadAsEnum`
- [ ] **Step 2: Fehlschlag prüfen:** `.\gradlew.bat test --tests tf.helper.general.TestTypedPreference` (in `$jh`) → Kompilierfehler.
- [ ] **Step 3: Implementieren.**
- [ ] **Step 4:** `.\gradlew.bat build` (in `$jh`) → grün, veröffentlicht nach mavenLocal (1.18).
- [ ] **Step 5: Commit** (nur diese vier Dateien): `"Preferences: NodePreferencesStore and ITypedPreference - the common part of the applications' preference enums"`

---

### Task 2: GeoTagger auf JavaHelper umstellen

**Files:**
- Modify:
  - `$app\src\main\java\tf\geotagger\main\GeoTaggerPreferences.java`
  - alle Nutzer von `GeoTaggerPreferences.INSTANCE`: `GeoTagger.java` (`TableViewPreferences`), `PlaceServiceFactory.java` (`service()` über `getAsType()`), `PreferenceEditor.java`
  - Tests: `UiTestSupport.java`, `PlaceServiceFactoryTest.java` (`clear()` über `store()`)

**Interfaces (Produces):**
- `public enum GeoTaggerPreferences implements ITypedPreference`:
  - die Einträge wie bisher ohne `INSTANCE`; `PLACE_SERVICE` mit Typ `PlaceService.class` und Default `PlaceService.GEONAMES.name()`;
  - `static NodePreferencesStore store()`: Knoten wie bisher, im Test `tf/geotagger/test`;
  - `static RecentFiles getRecentFolders()` über `store()`.
- `PlaceServiceFactory.service()` = `GeoTaggerPreferences.PLACE_SERVICE.getAsType()`; unbekannte Werte geben über den Rückfall GeoNames.

- [ ] **Step 1:** Die bestehenden Tests sind der Maßstab (reine Umstellung ohne neues Verhalten). Neu ist nur `PlaceServiceFactoryTest.storedServiceNameIsReadAsEnum`: der String „MAPBOX“ direkt im Speicher → `Mapbox`.
- [ ] **Step 2: Fehlschlag prüfen:** `.\gradlew.bat test --tests tf.geotagger.main.PlaceServiceFactoryTest` → Kompilierfehler (`store()` fehlt).
- [ ] **Step 3: Umstellen.** Das Enum schrumpft auf Einträge, Konstruktor, die vier Getter, `store()` und `getRecentFolders()`.
- [ ] **Step 4:** `.\gradlew.bat build` → 165 Tests grün, keine Warnungen; dann `.\gradlew.bat uiTest` (gesamt, Hände weg) → 66 UI-Tests grün.
- [ ] **Step 5: Commit** `"Preferences on JavaHelper's NodePreferencesStore / ITypedPreference, place service as enum"`

---

### Task 3: Änderungsliste GPXEditor, Plan 5b anpassen

- [ ] **Step 1: GPXEditor-Änderungsliste** (Notiz für die spätere Umstellung): `GPXEditorPreferences` auf `NodePreferencesStore` + `ITypedPreference` umstellen. Dabei Folgendes beachten:
  - `INSTANCE` wird durch einen statischen `store()` ersetzt.
  - Nutzer von `GPXEditorPreferences.INSTANCE` sind z. B. `RecentFiles`, `TableViewPreferences` und die Map-Layer-Einstellungen.
  - Enums mit eigenem `toString` speichern nun `name()`.
- [ ] **Step 2: Plan 5b anpassen:** Die neuen Einträge `ALTITUDE_SOURCE`, `SRTM_DOWNLOAD_FORMAT` und `SRTM_INTERPOLATION` werden typisierte Enums (`AltitudeSource.class`, `SrtmFormat.class`, `SrtmInterpolation.class`).
- [ ] **Step 3: Commit** `"Plan 5b: preferences as typed enums"`

---

## Abschluss von Plan 5a

- JavaHelper-Tests grün, 1.18 in mavenLocal; GeoTagger 165 Tests und 66 UI-Tests grün.
- Abschluss-Review durch einen frischen Reviewer (klein); Push von JavaHelper und GeoTagger durch den Nutzer.
- Danach Plan 5b.

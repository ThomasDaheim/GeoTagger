# GeoTagger Plan 4c: Layout wie GeoSetter – Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Das Hauptfenster bekommt GeoSetters platzsparendes Layout.
- Die Werte stehen als Spalten in der Dateitabelle; bearbeitet wird in einem Dialog.
- Karte und Image info sind Reiter rechts oben.
- Rechts unten liegt die GPX-Sektion, zweigeteilt in Tracks und Punkte.
- Die Schrift ist kleiner.

**Architecture:**
- **Dateitabelle:**
  - Neue Spalten: Koordinaten (Grad/Min/Sek), Höhe, Ländercode, Land, Region, Ort, Sublocation, Zeitzone.
  - Die Statusspalten entfallen. Ungespeicherte Werte markiert die Zelle mit der Style-Klasse `changed` (orange).
  - Spalten lassen sich über den Tabellen-Menüknopf ein- und ausblenden.
- **`DetailsPane`** wandert in einen modalen **`EditDialog`** (Strg+E, Doppelklick auf eine Zeile, Button „Edit…“).
  - Der Dialog sammelt die Eingaben; OK übernimmt sie als **ein** Undo-Schritt.
  - Solange ein Feld ungültig ist, ist OK gesperrt.
- **Rechts:**
  - Oben ein `TabPane` mit „Map“ und „Image info“.
  - Unten die GPX-Sektion: links `TrackListPane` (Häkchen = auf der Karte, Auswahl = Punkte anzeigen), rechts `TrackPointsPane` mit den Punkten der ausgewählten Tracks.
- **Schrift:** Ein eigenes Stylesheet `GeoTagger.css` nach JMetro setzt 11 px.

**Tech Stack:** wie Plan 4b (Java 25, JavaFX 25.0.1, JMetro 11.6.16, TestFX 4.0.18).

**Spec:** [2026-11-03-geotagger-design.md](../specs/2026-11-03-geotagger-design.md), Abschnitt „Hauptfenster“ (wird in Task 5 an dieses Layout angepasst). Vorgänger: [Plan 4b](2026-11-06-plan-4b-karte-feinschliff.md).

## Global Constraints

- Alles aus den Global Constraints von Plan 4 und 4b gilt weiter: Englisch, `master`, Lizenz-Header, vollständiges Javadoc, keine Compilerwarnungen, `$git`/`$app`, kein Drag & Drop über den Roboter.
- **Entscheidungen des Nutzers (07.11.2026):**
  - Bearbeiten über einen **Dialog** (wie GeoSetter); Karte, Rechtsklick und Drag & Drop bleiben für Koordinaten.
  - Die **Statusspalten entfallen**; geänderte Werte werden in der Zelle markiert.
  - **Koordinaten in Grad/Min/Sek:** `N46°40'48.05"; E11°5'28.61"` (Sekunden mit 2 Nachkommastellen, Punkt als Dezimaltrenner, wie GeoSetter).
  - **Schrift 11 px.**
  - Werkzeugleisten je Bereich mit Icons folgen später.
- **Tabellen-Einstellungen:** Sie werden unter dem neuen Präfix `mediaTable2` gespeichert. `TableViewPreferences` (JavaHelper) setzt Breiten und Sichtbarkeit **nach Position**, auch wenn die gespeicherte Spaltenreihenfolge nicht mehr passt. Unter dem alten Präfix bekämen die neuen Spalten die Breiten der alten.
- **Statusleiste:** `"<n> files (<m> with coordinates) - <k> selected"`, wie GeoSetter.

## Review Focus

1. **Gespeicherte Tabellen-Einstellungen aus Plan 4/4b:** Alte Spaltenbreiten dürfen nicht auf neue Spalten fallen. → neues Präfix, Test `GeoTaggerUiTest.oldColumnSettingsAreIgnored` (Task 1)
2. **Dialog mit Mehrfachauswahl und unterschiedlichen Werten:** Ein leeres Feld mit „(different)“ ändert nichts. Cancel ändert nie etwas. → `GeoTaggerEditUiTest.multiSelectionChangesAllFiles`, `cancelChangesNothing` (Task 2)
3. **Ungültige Eingabe im Dialog:** OK gesperrt, Fehler sofort sichtbar, Enter übernimmt nicht. → `invalidLatitudeBlocksOk` (Task 2)
4. **Punkte-Tabelle bei mehreren ausgewählten Tracks und bei Punkten ohne Zeit oder Höhe:** leere Zelle statt Fehler; die Distanz zählt je Track ab 0. → `TrackPointsTest`, `GeoTaggerTrackUiTest.selectedTracksShowTheirPoints` (Task 3)
5. **Image info als Reiter neben der Karte:** wird nur gelesen, wenn der Reiter sichtbar ist. Die Karte bleibt beim Reiterwechsel erhalten (WebView nicht neu laden). → `GeoTaggerUiTest.imageInfoTabKeepsTheMap` (Task 3)

---

### Task 1: Werte-Spalten in der Dateitabelle

**Files:**
- Modify: `$app\src\main\java\tf\geotagger\edit\FieldText.java`, `GeoTagger.java`, `GeoTagger.fxml`
- Create: `$app\src\main\resources\GeoTagger.css` (Style-Klasse `changed`; die Schriftgröße folgt in Task 4)
- Modify: `GeoTaggerManager.java` (Stylesheet nach JMetro hinzufügen)
- Test: `FieldTextTest.java`, `GeoTaggerUiTest.java`, `GeoTaggerEditUiTest.java`, `GeoTaggerMapUiTest.java`

**Interfaces:**
- Produces:
  - `static String FieldText.formatCoordinates(Double latitude, Double longitude)`: `""` if one is null, otherwise `N46°40'48.05"; E11°5'28.61"`.
  - Table columns with ids:
    - `nameColumn`, `captureTimeColumn`, `coordinatesColumn`, `altitudeColumn` (`"512.4 m"`);
    - `countryCodeColumn`, `countryColumn`, `stateColumn`, `cityColumn`, `sublocationColumn`;
    - `timeOffsetColumn` (`"+02:00"`).
  - Cells with a pending change of their field(s) carry the style class `changed`. For the coordinates that is LATITUDE or LONGITUDE.
  - `mediaTable.setTableMenuButtonVisible(true)`
  - `TABLE_PREFERENCES = "mediaTable2"`

- [ ] **Step 1: Fehlschlagende Tests**

```java
// FieldTextTest
@Test
void formatsCoordinatesAsDegreesMinutesSeconds() {
    assertEquals("N46°40'48.05\"; E11°5'28.61\"", FieldText.formatCoordinates(46.680014, 11.091281));
    assertEquals("S33°52'30.00\"; W151°12'0.00\"", FieldText.formatCoordinates(-33.875, -151.2));
    assertEquals("", FieldText.formatCoordinates(46.5, null));
}
```

`GeoTaggerUiTest`:
- `loadingFolderFillsListAndCounts` prüft statt ✓ und –:
  - `coordinatesColumn` liefert für `a.jpg` `"N46°30'0.00\"; E11°30'0.00\""`, für `b.jpg` `""`;
  - `cityColumn` liefert für `a.jpg` `"Meran"`;
  - die Statusleiste zeigt `"3 files (1 with coordinates) - 1 selected"`.
- `statusColumnsShowTheirHeaders` wird zu `valueColumnsShowTheirHeaders`, mit den Überschriften `Coordinates`, `Altitude`, `City`, `Time zone` (wie bisher über `.label .text`).
- Neu `oldColumnSettingsAreIgnored`:
  - In `@Start` werden vor `UiTestSupport.start` unter dem alten Präfix `mediaTable` Breiten 45 für die Spalten 0–5 gespeichert (`GeoTaggerPreferences.INSTANCE.put("mediaTable-ColumnWidth-3", "45")` usw., den Schlüssel wie in `TableViewPreferences` gebildet).
  - Danach muss `coordinatesColumn` breiter als 100 sein.

`GeoTaggerEditUiTest` und `GeoTaggerMapUiTest`:
- `placeStatus(...) == "●"` wird ersetzt durch: Die Zelle der Spalte `cityColumn` für die Datei hat die Style-Klasse `changed`. Dafür gibt es eine Hilfsmethode `isMarkedChanged(robot, file, "cityColumn")`; sie sucht die `TableCell` mit der passenden `TableRow.getItem()` und `TableColumn.getId()`.
- `"✓"` nach dem Speichern wird zu: Die Zelle ist nicht mehr `changed`.

- [ ] **Step 2: Tests laufen lassen, Fehler erwarten**

Run: `.\gradlew.bat test --tests tf.geotagger.edit.FieldTextTest`
Expected: FAIL, Kompilierfehler „cannot find symbol: method formatCoordinates“.

- [ ] **Step 3: Implementieren**

- **`formatCoordinates`:** Hemisphäre aus dem Vorzeichen; Grad ganzzahlig; Minuten ganzzahlig; Sekunden mit `String.format(Locale.ROOT, "%.2f")`. Ergeben gerundete Sekunden 60.00, gibt es einen Übertrag auf die Minuten.
- **FXML:** Statt `placeColumn` und der drei Statusspalten kommen die neuen Spalten (prefWidth: Koordinaten 190, Höhe 65, Ländercode 45, Land / Region / Ort 110, Sublocation 120, Zeitzone 60).
- **Controller:**
  - Eine `cellFactory` je Werte-Spalte setzt bzw. entfernt in `updateItem` die Style-Klasse `changed` nach `file.pendingChanges()`.
  - `status(...)`, `place(...)` und `CHANGED` entfallen.
  - Die Statusleiste wird in `refreshFiles()` und `selectionChanged()` aktualisiert, außer während eines Lade- oder Speichervorgangs: Dann gehört die Statusleiste dem Fortschritt.
- **`GeoTagger.css`:** `.table-cell.changed { -fx-text-fill: #e07000; -fx-font-weight: bold; }`
- **`GeoTaggerManager`:** `scene.getStylesheets().add(GeoTaggerManager.class.getResource("/GeoTagger.css").toExternalForm())`, **nach** `new JMetro(...).setScene(...)`.

- [ ] **Step 4: Tests laufen lassen**

Run: `.\gradlew.bat build`, dann `.\gradlew.bat uiTest`
Expected: 129 Tests grün, 37 UI-Tests grün (36 + `oldColumnSettingsAreIgnored`).

- [ ] **Step 5: Commit**

```powershell
& $git -C $app add src
& $git -C $app commit -m "File table shows the values (coordinates as degrees / minutes / seconds), changed cells marked, no status columns"
```

---

### Task 2: Bearbeiten im Dialog

**Files:**
- Create: `$app\src\main\java\tf\geotagger\main\EditDialog.java`
- Modify: `DetailsPane.java`, `GeoTagger.java`, `GeoTagger.fxml` (Details-Reiter entfällt, Button `editButton` „Edit…“ in der Werkzeugleiste)
- Test: `GeoTaggerEditUiTest.java`, `GeoTaggerMapUiTest.java`

**Interfaces:**
- Produces:
  - **`DetailsPane`:**
    - `void setFiles(List<MediaFile>)` bleibt.
    - `Map<Field, Object> changes()`: die Felder, deren Text vom gezeigten abweicht und gültig ist; ein leeres Feld mit „(different)“ ist keine Änderung.
    - `BooleanProperty validProperty()`: false, solange ein Feld ungültig ist.
    - Die Prüfung läuft bei jeder Texteingabe. Rote Umrandung und Fehler-Tooltip erscheinen wie bisher sofort.
    - `setOnEdit` und die Logik „ungültige Eingabe bleibt bei Refresh“ entfallen.
  - **`final class EditDialog`:**
    - `static Optional<Map<Field, Object>> show(Window owner, List<MediaFile> files)`: modal; leer bei Cancel / Esc oder ohne Änderung.
    - Titel `"Edit " + n + (n == 1 ? " file" : " files")`.
    - Buttons mit den IDs `editOk` und `editCancel`. `editOk` ist der Default-Button (Enter) und gesperrt, wenn `validProperty()` false ist.
    - Die Felder behalten ihre IDs `#details-<FIELD>`.
  - **Controller:**
    - `editSelection()` öffnet den Dialog: per Strg+E (Accelerator), Doppelklick auf eine Zeile (Row-Factory) und `editButton`.
    - Das Ergebnis wird als **eine** `ChangeFieldsAction("Edit", …)` für alle ausgewählten Dateien übernommen.

- [ ] **Step 1: Tests umstellen**

In `GeoTaggerEditUiTest` und `GeoTaggerMapUiTest` ersetzt der Helfer `edit(FxRobot robot, Map<Field, String> values)` die Helfer `enter(...)` und `enterAndTab(...)`:
1. `robot.push(KeyCode.CONTROL, KeyCode.E)`;
2. warten, bis `#editOk` sichtbar ist;
3. für jedes Feld wie bisher Text ersetzen (Klick, `selectAll`, `write`);
4. `robot.clickOn("#editOk")`.

Bestehende Tests mit gleicher Aussage bleiben, mit `edit` statt `enter`:
- `editingCityMarksTheFileAsChanged`, `undoAndRedo`, `undoAndRedoWithKeysOutsideTextFields`, `multiSelectionChangesAllFiles`;
- `saveWritesTheFile`, `ctrlSSaves`, `editingKeepsThePreview`;
- `undoOfAPositionLeavesNoDifferentValues` (prüft jetzt die Tabellenzellen);
- `editingCoordinatesMovesTheMarker`.

Umgebaut:
- `invalidInputIsShownRightAwayAtTheLeft` und `fieldsAreOnlyAsWideAsNeeded`: im offenen Dialog, danach `#editCancel`.
- `invalidLatitudeIsRejected` → `invalidLatitudeBlocksOk`: `91` eingeben; dann ist `#editOk` deaktiviert, das Feld rot, Enter schließt den Dialog nicht; nach `#editCancel` ist die Datei unverändert.
- `invalidInputStaysWhenAnotherFieldIsEdited` entfällt: Im Dialog gibt es keinen Refresh zwischen zwei Feldern.

Neu:
- `cancelChangesNothing`: City „Bozen“ eingeben, `#editCancel` → Datei unverändert, Undo deaktiviert.
- `doubleClickOpensTheDialog`: Doppelklick auf die Zeile `a.jpg` → Dialog offen mit City „Meran“, dann `#editCancel`.
- `oneUndoStepForSeveralFields`: City und Country in einem Dialog → ein Undo setzt beide zurück.

- [ ] **Step 2: Tests laufen lassen, Fehler erwarten**

Run: `.\gradlew.bat uiTest --tests tf.geotagger.main.GeoTaggerEditUiTest`
Expected: FAIL; ohne Dialog findet `#editOk` nichts.

- [ ] **Step 3: Implementieren**

- **`EditDialog`:** ein `Stage` mit `initModality(WINDOW_MODAL)` und `initOwner(owner)`. Inhalt: `DetailsPane` und eine `ButtonBar`. Das Stylesheet des Hauptfensters (JMetro + `GeoTagger.css`) wird übernommen, damit Schrift und Look gleich sind.
- **Strg+E:** `KeyCodeCombination(KeyCode.E, KeyCombination.SHORTCUT_DOWN)` in den Accelerators, wie Strg+S. Ohne Auswahl passiert nichts.
- **Doppelklick:** in der `rowFactory` der Tabelle, nur bei `getClickCount() == 2` auf eine nicht-leere Zeile.

- [ ] **Step 4: Tests laufen lassen**

Run: `.\gradlew.bat build`, dann `.\gradlew.bat uiTest`
Expected: 129 Tests grün, 39 UI-Tests grün (37 − 1 + 3).

- [ ] **Step 5: Commit**

```powershell
& $git -C $app add src
& $git -C $app commit -m "Edit in a dialog (ctrl+e, double click, Edit...) - one undo step per dialog"
```

---

### Task 3: Rechts – Reiter Karte / Image info, GPX-Sektion mit Punkten

**Files:**
- Create: `$app\src\main\java\tf\geotagger\track\GpxPoint.java`, `TrackPoints.java`, `$app\src\main\java\tf\geotagger\main\TrackPointsPane.java`
- Modify: `GpxLine.java`, `GpxReader.java`, `TrackListPane.java`, `MapPane.java`, `GeoTagger.java`, `GeoTagger.fxml`, `GeoTaggerPreferences.java`
- Test: `GpxReaderTest.java`, `$app\src\test\java\tf\geotagger\track\TrackPointsTest.java`, `GeoTaggerTrackUiTest.java`, `GeoTaggerUiTest.java`

**Interfaces:**
- Produces:
  - `public record GpxPoint(LatLon position, Instant time, Double elevation)`: `time` und `elevation` dürfen null sein.
  - `GpxLine(List<GpxPoint> points, String color)` mit `List<LatLon> positions()`. Die Karte nutzt `positions()`.
  - `final class TrackPoints`:
    - `record Row(int number, Instant time, LatLon position, double distanceKm, Double elevation)`;
    - `static List<Row> of(List<GpxTrack> tracks)`: Zeilen aller Linien der Tracks in Reihenfolge; die Distanz läuft je Track ab 0, über die Segmente hinweg (Haversine, Erdradius 6371.0088 km); die Nummer läuft je Track ab 1.
  - `TrackPointsPane extends VBox`:
    - `TableView<TrackPoints.Row>` mit der ID `trackPoints`; Spalten `No.`, `Time` (lokale Zeitzone des Rechners, `HH:mm:ss`), `Coordinates` (`FieldText.formatCoordinates`), `Distance` (`"0.12 km"`), `Elevation` (`"512.4 m"`);
    - darunter `"<n> points"`;
    - `void showTracks(List<GpxTrack>)`;
    - `setOnPointSelected(Consumer<LatLon>)`.
  - `TrackListPane`:
    - Mehrfachauswahl in der Liste;
    - `setOnSelectionChanged(Consumer<List<GpxTrack>>)` meldet die **geladenen** ausgewählten Tracks; ein Track, der fertig lädt und ausgewählt ist, wird gemeldet.
    - Unter der Liste steht `"<n> GPX files (<m> loaded)"`.
  - **Layout im FXML:**
    - `rightSplit` (vertikal) enthält `TabPane infoTabs` (Reiter `mapTab` „Map“ mit `MapPane`, `imageInfoTab` „Image info“) und `SplitPane trackSplit` (horizontal: `TrackListPane`, `TrackPointsPane`).
  - **Preferences:**
    - `RECENT_RIGHT_DIVIDER_POS` mit Standard 0.65;
    - `RECENT_RIGHT_LOWER_DIVIDER_POS` wird durch `RECENT_TRACK_DIVIDER_POS("recentTrackDividerPos", "0.45")` ersetzt (Tracks | Punkte).
  - **Controller:** Ein ausgewählter Punkt zentriert die Karte darauf (`MapPane.panTo(LatLon)`, neu, package-private).

- [ ] **Step 1: Fehlschlagende Tests**

```java
// TrackPointsTest - tracks built with GpxTrack.loaded(new GpxContent(lines, List.of()))
@Test
void distanceRunsPerTrackOverAllSegments() {
    // track A: segment 1 (46.0/11.0 -> 46.01/11.0), segment 2 (46.02/11.0); track B: one point 47.0/12.0
    final List<TrackPoints.Row> rows = TrackPoints.of(List.of(a, b));
    assertEquals(List.of(1, 2, 3, 1), rows.stream().map(TrackPoints.Row::number).toList());
    assertEquals(0.0, rows.get(0).distanceKm(), 1e-9);
    assertEquals(1.112, rows.get(1).distanceKm(), 0.001);
    assertEquals(2.224, rows.get(2).distanceKm(), 0.001);
    assertEquals(0.0, rows.get(3).distanceKm(), 1e-9);
}

@Test
void pointsWithoutTimeOrElevationAreKept() {
    final List<TrackPoints.Row> rows = TrackPoints.of(List.of(trackWithPoint(new GpxPoint(new LatLon(46, 11), null, null))));
    assertNull(rows.get(0).time());
    assertNull(rows.get(0).elevation());
}

// GpxReaderTest
@Test
void readsPointTimesAndElevation() throws IOException {
    final Path gpx = gpx("points.gpx", "<gpx><trk><trkseg>"
            + "<trkpt lat=\"46.0\" lon=\"11.0\"><ele>512.4</ele><time>2026-11-04T10:00:00Z</time></trkpt>"
            + "<trkpt lat=\"46.1\" lon=\"11.1\"/></trkseg></trk></gpx>");
    final List<GpxPoint> points = GpxReader.readLines(gpx).get(0).points();
    assertEquals(new GpxPoint(new LatLon(46.0, 11.0), Instant.parse("2026-11-04T10:00:00Z"), 512.4), points.get(0));
    assertEquals(new GpxPoint(new LatLon(46.1, 11.1), null, null), points.get(1));
}
```

`GeoTaggerTrackUiTest`:
- `selectedTracksShowTheirPoints`: `tour.gpx` anhaken, warten bis `LOADED`, in der Liste auswählen. Dann hat `#trackPoints` 3 Zeilen, und das Label zeigt `"3 points"`.
- `selectingAPointCentersTheMap`: Zeile 3 auswählen; `myMap.getCenter()` ist 46.52 / 11.52 (Toleranz 1e-6).

`GeoTaggerUiTest`:
- `imageInfoTabKeepsTheMap`: Ordner laden, Reiter „Image info“ wählen. Image info zeigt Tags von `a.jpg`; zurück zu „Map“ ist `geoMarkerCount()` unverändert, ohne erneutes `waitUntilMapReady`.

- [ ] **Step 2: Tests laufen lassen, Fehler erwarten**

Run: `.\gradlew.bat test --tests tf.geotagger.track.TrackPointsTest`
Expected: FAIL, Kompilierfehler „cannot find symbol: class TrackPoints“.

- [ ] **Step 3: Implementieren**

Siehe Interfaces.
- Die Distanz rechnet `TrackPoints` selbst (kleine Haversine-Methode). In GeoHelper kommt sie erst mit Plan 5, falls die Geo-Dienste sie brauchen.
- Die bisherigen Track-Tests (`checkingATrackShowsItOnTheMap`, `trackColourComesFromTheFile`, `unreadableGpxIsMarked`) bleiben.

- [ ] **Step 4: Tests laufen lassen**

Run: `.\gradlew.bat build`, dann `.\gradlew.bat uiTest`
Expected: 132 Tests grün (129 + 2 `TrackPointsTest` + `readsPointTimesAndElevation`), 42 UI-Tests grün (39 + 3).

- [ ] **Step 5: Commit**

```powershell
& $git -C $app add src
& $git -C $app commit -m "Right side like GeoSetter: map / image info as tabs, GPX section with track list and points"
```

---

### Task 4: Kleinere Schrift

**Files:**
- Modify: `$app\src\main\resources\GeoTagger.css`, `EditDialog.java` (falls die Schrift dort nicht schon über das Stylesheet greift)
- Test: `GeoTaggerUiTest.java`

- [ ] **Step 1: Fehlschlagender Test**

```java
@Test
void fontIs11Pixels(FxRobot robot) {
    assertEquals(11.0, robot.lookup("#statusLabel").queryAs(Label.class).getFont().getSize(), 0.01);
    assertEquals(11.0, robot.lookup("#mediaTable .column-header .label").queryAs(Label.class).getFont().getSize(), 0.01);
}
```

- [ ] **Step 2: Test laufen lassen, Fehler erwarten**

Run: `.\gradlew.bat uiTest --tests tf.geotagger.main.GeoTaggerUiTest.fontIs11Pixels`
Expected: FAIL (heute die JMetro-Größe).

- [ ] **Step 3: `.root { -fx-font-size: 11px; }` in `GeoTagger.css`**

Setzt JMetro einzelne Controls fest in px (statt em), bleibt der Test für sie rot. Dann überschreibt `GeoTagger.css` genau diese Selektoren mit 11 px, etwa `.label`, `.table-view`, `.button`, `.text-field`, `.tab-pane` oder `.list-view`. Die Liste der überschriebenen Selektoren kommt als Ruling ins Ledger.

- [ ] **Step 4: Tests laufen lassen**

Run: `.\gradlew.bat uiTest`
Expected: 43 UI-Tests grün. Die Rahmenauswahl-Tests und die Tests mit festen Klickpunkten passen weiter; sie rechnen mit Bildschirmpunkten aus der Karte.

- [ ] **Step 5: Commit**

```powershell
& $git -C $app add src
& $git -C $app commit -m "Font 11 px like GeoSetter"
```

---

### Task 5: Spec, README, manuelle Prüfung

- [ ] **Step 1: Spec „Hauptfenster“ anpassen**

- **Links oben:** Statusfilter als Chips mit Anzahl. Darunter die Dateiliste mit den Spalten Name, Aufnahmezeit, Koordinaten (Grad/Min/Sek), Höhe, Ländercode, Land, Region, Ort, Sublocation, Zeitzone. Spalten lassen sich ausblenden, geänderte Werte sind markiert. Die Tabelle ist nicht editierbar.
- **Bearbeiten:** Dialog über Strg+E, Doppelklick oder „Edit…“ für die Auswahl. Bei Mehrfachauswahl zeigen unterschiedliche Werte „(different)“. OK ist ein Undo-Schritt.
- **Rechts oben:** Reiter „Karte“ und „Bild-Info (ExifTool)“.
- **Rechts unten:** GPX-Sektion. Links die Track-Liste mit Häkchen zum Laden und Einblenden sowie „+ GPX“. Rechts die Punkte der ausgewählten Tracks: Nr., Zeit, Koordinaten, Distanz, Höhe.
- **Statusleiste:** „n files (m with coordinates) - k selected“.
- **Schrift:** 11 px.

Der Absatz „Reiter ‚Details‘“ im Abschnitt „Umfang“ wird zu „Dialog ‚Bearbeiten‘“.

- [ ] **Step 2: README**

Den Status anpassen: Bearbeiten im Dialog, Werte-Spalten, GPX-Punkte.

- [ ] **Step 3: Manuell prüfen** (mit `USA_2024` und `Marlinger_Waalweg_20230319`)

1. **Tabelle:** Die Werte stehen in den Spalten, Koordinaten wie bei GeoSetter. Geänderte Zellen sind orange. Spalten lassen sich über den Menüknopf rechts im Kopf aus- und einblenden. Die Breiten bleiben nach einem Neustart.
2. **Dialog:** Strg+E, Doppelklick und „Edit…“ öffnen ihn. Mehrfachauswahl zeigt „(different)“. Eine ungültige Breite sperrt OK. Esc bricht ab. Ein Undo nimmt den ganzen Dialog zurück.
3. **Rechts:** Die Reiter Map / Image info funktionieren; die Karte lädt beim Zurückwechseln nicht neu. Die GPX-Sektion hat genug Platz.
4. **Punkte:** Einen Track auswählen zeigt seine Punkte mit Zeit, Koordinaten, Distanz und Höhe. Ein Klick auf einen Punkt zentriert die Karte.
5. **Schrift:** überall kleiner, auch im Dialog. Nichts wird abgeschnitten (Filter-Chips, Werkzeugleiste).

- [ ] **Step 4: Commit**

```powershell
& $git -C $app add README.md docs
& $git -C $app commit -m "Spec and README: layout like GeoSetter"
```

---

## Abschluss von Plan 4c

- `gradlew build`: 132 Tests grün; `gradlew uiTest`: 43 UI-Tests grün; keine Compilerwarnungen.
- Die Checkliste aus Task 5 ist abgehakt.
- Danach Plan 5 (Anreichern). Dessen Aktionen „Ort ermitteln“ und „Höhe ermitteln“ kommen in die Werkzeugleiste. Die Icons je Bereich sind ein eigener späterer Schritt.

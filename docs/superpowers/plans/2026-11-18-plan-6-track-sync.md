# GeoTagger Plan 6: Track-Sync-Dialog – Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ein Knopf „Track sync…“ öffnet den Dialog aus der Spec. Er ordnet Dateien über ihre Aufnahmezeit Positionen aus GPX-Tracks zu, zeigt eine Vorschau und übernimmt das Ergebnis als **einen** Undo-Schritt.

**Architecture:**
- **Logik in `sync`:** Seit Plan 2 vorhanden: `TrackSync.match`, `SyncParameters`, `CameraClock`, `TrackTimeline`. Neu dazu:
  - `TrackSync.changes` statt `apply`: Die Ergebnisse werden zu einer Änderungsliste für `ChangeFieldsAction`, die Dateien bleiben unberührt, bis die Aktion läuft.
  - `TrackSync.scope` bestimmt die Dateien.
  - `SyncSummary` liefert die Texte.
  - `TrackSource` ist die Track-Quelle.
- **Zeitzone der Kamera:** Fehlt `OffsetTimeOriginal`, kommt die Vorbelegung aus der Zeitzone der Maker Notes (`Canon:TimeZone`). Die Herstellerzone wird in `CaptureTime` mitgeführt.
- **UI in `main`:**
  - `SyncDialog`: ein modales Fenster wie `PlaceChoiceDialog`, gebaut in Code.
  - Die Vorschau wird bei jeder Änderung synchron neu berechnet; `match` sucht binär und braucht für 2 000 Dateien wenige Millisekunden.
  - GPX-Datei bzw. -Ordner werden im Hintergrund gelesen und für die Sitzung gemerkt.
  - `SyncSettings` speichert die zuletzt benutzten Parameter.

**Tech Stack:** wie bisher (Java 25, JavaFX 25.0.1, JMetro, JavaHelper 1.18 `ITypedPreference`, TestFX).

**Spec:** [2026-11-03-geotagger-design.md](../specs/2026-11-03-geotagger-design.md), Abschnitt [„Track-Sync-Dialog“](../specs/2026-11-03-geotagger-design.md) (Eingaben, Vorschau, Übernehmen, Algorithmus) und die Regel der Werkzeugleiste unter „Hauptfenster“. Vorgänger: Plan 2 (Sync-Logik), [Plan 5b](2026-11-17-plan-5b-hoehe.md).

## Global Constraints

- Alle Global Constraints der Pläne 5–5b gelten weiter:
  - Lizenzkopf in jeder `.java`-Datei, byte-gleich;
  - Javadoc vollständig mit `@param`, `@return` und `@throws`;
  - Compiler-Warnungen auch in Tests beheben;
  - kein Netz in Tests;
  - UI-Tests gezielt per `--tests`, die gesamte Suite vor und nach dem Abschluss-Review; nie `build` zusammen mit `--tests`;
  - `$git` dynamisch;
  - Arbeit direkt auf `master`, Pushes macht der Nutzer.
- **Entscheidungen des Nutzers vom 18.11.2026:**
  - Die Quelle „alle GPX-Dateien in einem Ordner“ durchsucht **auch Unterordner**.
  - Ohne Index: Üblich sind 1–2 GPX-Dateien zu höchstens 30 MB. Gelesen wird im Hintergrund, das Ergebnis bleibt für die Sitzung gemerkt.
  - **Vorbelegung der Kamera-Zeitzone:** am häufigsten `OffsetTimeOriginal` der Dateien, sonst die häufigste Herstellerzone (`Canon:TimeZone`), sonst die Systemzeitzone.
  - **Gemerkt** wird alles außer der Zeitzone: Track-Quelle, GPX-Datei, GPX-Ordner, Offset, maximale Differenz, Zuweisung, die drei Häkchen und „Vorhandene überschreiben“. Die Zeitzone wird jedes Mal aus den Dateien vorbelegt.
- **Entscheidung dieses Plans (bitte beim Review prüfen):** „Dateien“ steht auf „Auswahl“, wenn der Nutzer selbst Dateien ausgewählt hat, sonst auf „Ohne Koordinaten“. Das passt zur Regel der Werkzeugleiste vom 15.11.2026; die Spec nennt bisher „Ohne Koordinaten“ als Standard. „Ohne Koordinaten“ meint die gelisteten (gefilterten) Dateien.
- **Canon:TimeZone** liest ExifTool mit `-n` in Minuten. Die Sommerzeit ist darin schon enthalten: Das Muster `IMG_6046.CR2` hat `TimeZone` 120, `DaylightSavings` 60 und die Stadt Paris, also +02:00.
- Videos bekommen keine Zeitzone (wie bisher in `TrackSync`); bei QuickTime-UTC-Zeiten zählt nur der Offset.

## Review Focus

1. **Ungültige Eingaben** (Zeitzone „Europe/Berlinn“, Offset-Felder leer oder Buchstaben, maximale Differenz 0): Es gibt keine Exception. Das Feld wird rot, „Apply“ ist gesperrt und die Zusammenfassung sagt warum. → `GeoTaggerSyncUiTest.invalidZoneBlocksApply` (Task 4)
2. **GPX ohne Zeitstempel oder unlesbar:** Der Dialog meldet „no points with time“ bzw. den Lesefehler je Datei, „Apply“ ist gesperrt, nichts stürzt ab. → `GeoTaggerSyncUiTest.unreadableGpxIsReported` (Task 5)
3. **Dateien ohne Aufnahmezeit** zählen in der Zusammenfassung als „without capture time“ und bleiben unverändert. → `SyncSummaryTest.countsEveryStatus` (Task 2), `TrackSyncChangesTest.onlyResultsWithPositionChange` (Task 2)
4. **Auswahl mit vorhandenen Koordinaten, „Vorhandene überschreiben“ aus:** Diese Dateien bleiben aus der Vorschau, und die Zusammenfassung nennt die Anzahl („2 skipped - they have coordinates“). → `TrackSyncScopeTest.selectionWithoutOverwriteSkipsFilesWithCoordinates` (Task 2), `GeoTaggerSyncUiTest.selectionWithoutOverwriteKeepsCoordinates` (Task 4)
5. **Undo stellt alles wieder her:** Ein Undo nimmt Position, Höhe und Zeitzone aller Dateien des Laufs zurück. → `GeoTaggerSyncUiTest.applyIsOneUndoStep` (Task 4)

---

### Task 1: Herstellerzone lesen und als Vorbelegung nutzen

**Files:**
- Modify: `src/main/java/tf/geotagger/model/CaptureTime.java`, `src/main/java/tf/geotagger/metadata/ReadTag.java`, `src/main/java/tf/geotagger/metadata/MetadataParser.java`, `src/main/java/tf/geotagger/sync/SyncParameters.java`
- Test: `MetadataParserTest`, `SyncParametersTest`, den Muster-Test in `MetadataReaderTest` (übersprungen ohne `samples`)

**Interfaces (Produces):**
- `public record CaptureTime(LocalDateTime dateTime, boolean utc, ZoneOffset makerZone)`. Dazu der Konstruktor `CaptureTime(LocalDateTime, boolean)` mit `makerZone = null`, damit bestehender Code und Tests bleiben.
- `ReadTag.CANON_TIME_ZONE("Canon:TimeZone")`.
- `SyncParameters.suggestedCameraZone(List<MediaFile> files, ZoneId fallback)` mit der neuen Reihenfolge (siehe Global Constraints).

- [ ] **Step 1: Fehlschlagende Tests:**
  - `MetadataParserTest.canonTimeZoneInMinutes`: Eintrag mit `"ExifIFD:DateTimeOriginal": "2026:11:04 11:44:03"` und `"Canon:TimeZone": 120` → `captureTime().makerZone()` = `ZoneOffset.ofHours(2)`.
  - `MetadataParserTest.implausibleCanonTimeZoneIsIgnored`: `5000` (mehr als ±18 h) bzw. `"abc"` → `null`.
  - `SyncParametersTest.makerZoneWhenNoFileHasOffsetTimeOriginal`: zwei Dateien mit Herstellerzone +02:00, eine mit +01:00 → +02:00.
  - `SyncParametersTest.offsetTimeOriginalWinsOverMakerZone`: eine Datei mit `TIME_OFFSET` +01:00, zwei mit Herstellerzone +02:00 → +01:00.
  - `MetadataReaderTest`, Mustertest: `IMG_6046.CR2` → `makerZone` +02:00.
- [ ] **Step 2:** `.\gradlew.bat test --tests 'tf.geotagger.metadata.*' --tests 'tf.geotagger.sync.*'` → Kompilierfehler bzw. rot.
- [ ] **Step 3: Umsetzen:**
  - Minuten werden zu `ZoneOffset.ofTotalSeconds(min * 60)`, nur für ganze Zahlen mit |min| ≤ 18·60, sonst `null`.
  - `CaptureTime` bekommt die Zone auch ohne `DateTimeOriginal`, also bei Videos mit QuickTime-Zeit.
- [ ] **Step 4:** `.\gradlew.bat build` → grün, alle bisherigen 184 Tests plus die neuen.
- [ ] **Step 5: Commit** `"Sync: camera zone from the maker notes (Canon:TimeZone) when no OffsetTimeOriginal"`

---

### Task 2: Sync-Logik für den Dialog

**Files:**
- Modify: `sync/TrackSync.java` (`apply` wird durch `changes` ersetzt), `track/GpxReader.java`, `track/TrackTimeline.java`
- Create: `sync/FileScope.java`, `sync/TrackSource.java`, `sync/SyncSummary.java`
- Test: `TrackSyncApplyTest` wird umbenannt in `TrackSyncChangesTest`; neu `TrackSyncScopeTest`, `SyncSummaryTest`; Ergänzungen in `GpxReaderTest` und `TrackTimelineTest`

**Interfaces (Produces):**
- `public static Map<MediaFile, Map<Field, Object>> TrackSync.changes(List<SyncResult> results, SyncParameters parameters)`:
  - nur Ergebnisse mit Position;
  - dieselben Regeln wie das bisherige `apply`: Höhe nur mit `takeElevation` und bekannter Höhe; `TIME_OFFSET` nur für Bilder mit `writeTimeZone`;
  - die Dateien selbst bleiben unverändert.
- `public enum FileScope { WITHOUT_COORDINATES, SELECTION }`.
- `public static List<MediaFile> TrackSync.scope(List<MediaFile> listed, List<MediaFile> selected, FileScope scope, boolean overwrite)`:
  - `WITHOUT_COORDINATES`: die gelisteten Dateien ohne Koordinaten; `overwrite` zählt hier nicht.
  - `SELECTION`: `eligible(selected, overwrite)`.
- `public enum TrackSource { LOADED_TRACKS("Loaded tracks"), GPX_FILE("GPX file"), GPX_FOLDER("All GPX files in a folder"); String label(); }`
- `SyncSummary`:
  - `static String text(List<SyncResult> results, int skipped)`, z. B. `"12 matched, 3 with large gap, 5 without match, 1 without capture time - 2 skipped (they have coordinates)"`. Teile mit 0 entfallen, außer dem ersten. Ohne Ergebnisse: `"No files to sync"`.
  - `static String gap(Duration gap)`: `"45 s"`, `"12 min 5 s"`, `"2 h 3 min"`, `"3 d 4 h"`; `null` ergibt `""`.
  - `static String applied(int count)`: `"Track sync: positions for 12 files"` bzw. `"… for 1 file"`.
- `public static List<Path> GpxReader.findGpxFiles(Path dir, boolean subfolders)`, sortiert. Die bisherige Variante mit einem Parameter ruft `false` auf.
- `public static LoadResult TrackTimeline.load(List<Path> gpxFiles, BiConsumer<Integer, Integer> progress)`: `progress` meldet (gelesen, gesamt) nach jeder Datei. Eine Datei ohne Punkte mit Zeit landet in `failures` mit `"no points with time"`.

- [ ] **Step 1: Fehlschlagende Tests:**
  - `TrackSyncChangesTest`: die Fälle des bisherigen `TrackSyncApplyTest`, jetzt auf der zurückgegebenen Map, plus `filesAreNotChanged` und `onlyResultsWithPositionChange` (`NO_MATCH`, `NO_CAPTURE_TIME` fehlen in der Map).
  - `TrackSyncScopeTest`: `withoutCoordinatesTakesListedFilesWithoutCoordinates`, `selectionWithoutOverwriteSkipsFilesWithCoordinates`, `selectionWithOverwriteTakesAll`.
  - `SyncSummaryTest`: `countsEveryStatus`, `zeroPartsAreLeftOut`, `gapTexts` (mit den Werten oben), `singularFile`.
  - `GpxReaderTest.findsGpxFilesInSubfolders`: `a.gpx`, `2025/b.GPX` und `x.txt` → mit `true` beide GPX-Dateien, mit `false` nur `a.gpx`.
  - `TrackTimelineTest.loadReportsProgressAndFilesWithoutTimes`: zwei Dateien, eine ohne `<time>` → `progress` (1,2) und (2,2); die zweite steht in `failures` mit `"no points with time"`.
- [ ] **Step 2:** `.\gradlew.bat test --tests 'tf.geotagger.sync.*' --tests 'tf.geotagger.track.*'` → rot.
- [ ] **Step 3: Umsetzen.** `apply` wird entfernt, weil der einzige Nutzer sein Test war.
- [ ] **Step 4:** `.\gradlew.bat build` → grün.
- [ ] **Step 5: Commit** `"Sync: changes for one undo step, file scope, summary texts, GPX in subfolders"`

---

### Task 3: Gemerkte Parameter

**Files:**
- Modify: `main/GeoTaggerPreferences.java`
- Create: `main/SyncSettings.java`
- Test: `SyncSettingsTest`

**Interfaces (Produces):**
- **Preferences:**
  - `SYNC_TRACK_SOURCE` (`TrackSource`, `LOADED_TRACKS`)
  - `SYNC_GPX_FILE` (`String`, `""`)
  - `SYNC_GPX_FOLDER` (`String`, `""`)
  - `SYNC_OFFSET_SECONDS` (`Long`, `0`)
  - `SYNC_MAX_GAP_MINUTES` (`Long`, `30`)
  - `SYNC_INTERPOLATE` (`Boolean`, `true`)
  - `SYNC_ONLY_PAST` (`Boolean`, `false`)
  - `SYNC_TAKE_ALTITUDE` (`Boolean`, `true`)
  - `SYNC_WRITE_TIME_ZONE` (`Boolean`, `false`)
  - `SYNC_OVERWRITE` (`Boolean`, `false`)
- `record SyncSettings(TrackSource source, String gpxFile, String gpxFolder, Duration offset, Duration maxGap, boolean interpolate, boolean onlyPast, boolean takeAltitude, boolean writeTimeZone, boolean overwrite)` (package-private):
  - `static SyncSettings load()`: Eine maximale Differenz ≤ 0 aus den Preferences ergibt 30 min.
  - `void store()`.
  - `SyncParameters parameters(ZoneId zone)`.

- [ ] **Step 1: Fehlschlagende Tests:**
  - `SyncSettingsTest.defaultsAreTheSpecDefaults`: `LOADED_TRACKS`, Offset 0, 30 min, interpolieren, nicht nur Vergangenheit, Höhe an, Zeitzone aus, überschreiben aus.
  - `SyncSettingsTest.storedValuesComeBack`: Offset −1 d 2 h 3 min 4 s und alle Häkchen umgedreht.
  - `SyncSettingsTest.parametersUseTheGivenZone`.
  - Mit dem Test-Knoten wie in `AltitudeServiceFactoryTest` (Assumption und `store().clear()`).
- [ ] **Step 2–4:** rot → umsetzen → `.\gradlew.bat build` grün.
- [ ] **Step 5: Commit** `"Sync: last used parameters in the preferences (all but the time zone)"`

---

### Task 4: Dialog mit Vorschau und Übernahme aus den geladenen Tracks

**Files:**
- Create: `main/SyncDialog.java`
- Modify: `GeoTagger.fxml` (Knopf `trackSyncButton` „Track sync…“ nach „Find altitude“, Tooltip „Positions from GPX tracks by the capture time“), `main/GeoTagger.java`, `GeoTagger.css`
- Test: `src/test/java/tf/geotagger/main/GeoTaggerSyncUiTest.java` (neu)

**Interfaces:**
- **Consumes:** Task 1–3.
- **Produces:**
  - `record SyncInput(List<MediaFile> listed, List<MediaFile> selected, boolean selectionByUser, List<TrackPoint> loadedPoints, int loadedTracks, Map<Path, TrackTimeline.LoadResult> cache)` (package-private).
  - `static Optional<SyncOutcome> SyncDialog.show(Window owner, SyncInput input)`. Dazu `record SyncOutcome(List<SyncResult> results, SyncParameters parameters)`.
- **Layout** (oben die Eingaben in einem `GridPane`, darunter die Vorschau, unten Zusammenfassung und Knöpfe; die IDs sind für die Tests):
  - **Track-Quelle:**
    - Radios `#syncSourceLoaded` „Loaded tracks (n)“ (gesperrt bei n = 0), `#syncSourceFile`, `#syncSourceFolder`;
    - `#syncGpxFile` bzw. `#syncGpxFolder` als `TextField` mit „Browse…“ (`FileChooser` bzw. `DirectoryChooser`). In Task 4 nur angelegt, das Laden kommt in Task 5.
  - **Dateien:** Radios `#syncFilesWithout` „Without coordinates“ und `#syncFilesSelection` „Selection (n)“ (gesperrt ohne eigene Auswahl), dazu `#syncOverwrite` „Overwrite existing“ (nur bei „Selection“ aktiv).
  - **Kamera-Zeitzone:** `#syncZone` als editierbare `ComboBox<String>` mit allen `ZoneId.getAvailableZoneIds()` (sortiert), vorbelegt mit `suggestedCameraZone(scope, ZoneId.systemDefault())`. Ungültiger Text: Stilklasse `invalid` (rot) wie im Edit-Dialog.
  - **Offset:** `#syncOffsetSign` (`+`/`-`), `Spinner<Integer>` `#syncOffsetDays` (0–999), `#syncOffsetHours` (0–23), `#syncOffsetMinutes` (0–59), `#syncOffsetSeconds` (0–59), alle editierbar.
  - **Kamera-Uhr:** `#syncCameraClock` „Camera display should show: 2026-11-18 14:03:22“. Eine `Timeline` aktualisiert sie jede Sekunde über `CameraClock.cameraDisplay`; sie stoppt beim Schließen.
  - **Maximale Differenz:** `#syncMaxGap` `Spinner<Integer>` in Minuten (1–1440).
  - **Zuweisung:** Radios `#syncInterpolate` „Interpolate between track points“ und `#syncNearest` „Nearest track point“.
  - **Häkchen:** `#syncOnlyPast` „Ignore track points in the future“, `#syncTakeAltitude` „Take altitude from track“, `#syncWriteZone` „Write time zone to the file“.
  - **Vorschau** `#syncPreview` (`TableView<SyncResult>`):
    - Spalten: File (Name relativ zum Ordner), Camera time, Corrected time (UTC), Position (DMS wie die Hauptliste), Gap (`SyncSummary.gap`).
    - Zeilen-Stilklassen `sync-matched` (grün), `sync-large-gap` (orange), `sync-no-match` (grau, auch ohne Aufnahmezeit).
  - **Zusammenfassung** `#syncSummary` mit `SyncSummary.text`.
  - **Knöpfe:** `#syncApply` „Apply“ (gesperrt ohne Treffer oder bei ungültiger Eingabe), `#syncCancel` „Cancel“, Esc = Cancel.
- **Verhalten:**
  - Jede Änderung einer Eingabe rechnet `TrackSync.match(TrackSync.scope(...), timeline, parameters)` neu.
  - „Apply“ speichert `SyncSettings` und liefert das Ergebnis. „Cancel“ speichert nichts.
- **Controller:**
  - `trackSyncButton` ist gesperrt bei `busy`, ohne Ordner oder ohne gelistete Dateien.
  - `trackSync()` öffnet den Dialog mit:
    - den gelisteten Dateien,
    - der eigenen Auswahl (`selectionByUser`),
    - den Punkten aller geladenen Tracks (`trackListPane.loadedTracks()` → `GpxTrack.points()`),
    - dem Sitzungs-Cache (Feld im Controller).
  - Das Ergebnis wird **eine** `ChangeFieldsAction("Track sync", TrackSync.changes(...))`; die Statusleiste zeigt `SyncSummary.applied(n)`.

- [ ] **Step 1: Fehlschlagende Tests** in `GeoTaggerSyncUiTest`:
  - **Testdaten:**
    - `tour.gpx` in `@TempDir` mit den Punkten 09:00Z (46.0/11.0, 1000 m) und 09:10Z (46.1/11.2, 1100 m);
    - Bilder per ExifTool: `a.jpg` mit `DateTimeOriginal` 2026:09:02 11:05:00, `b.jpg` 18:00:00, `c.jpg` mit Koordinaten 41.9/12.5 und 11:05:00.
    - Den Track lädt der Test wie `GeoTaggerTrackUiTest` (`shownProperty().set(true)`, warten bis `LOADED`), die Zone setzt er im Dialog auf `Europe/Berlin`.
  - `syncAssignsPositionsFromLoadedTrack`:
    - **Vorschau:** `a.jpg` ist `sync-matched`; die Position ist interpoliert 46.05/11.1; Gap „5 min“.
    - `b.jpg` ist `sync-no-match`; die Zusammenfassung lautet „1 matched, 1 without match“.
    - „Apply“ → `a.jpg` 46.05/11.1/1050 m, `b.jpg` unverändert; Status „Track sync: positions for 1 file“.
  - `applyIsOneUndoStep`: mit „Write time zone“ an → ein Undo nimmt Position, Höhe und `TIME_OFFSET` von `a.jpg` zurück; Undo ist danach gesperrt.
  - `offsetChangesThePreviewLive`: Offset +7 h → `a.jpg` wird `sync-no-match`; zurück auf 0 → wieder `sync-matched`.
  - `selectionWithoutOverwriteKeepsCoordinates`:
    - Auswahl `a.jpg` und `c.jpg` → „Selection“ ist vorgewählt.
    - Ohne „Overwrite“ fehlt `c.jpg` in der Vorschau, die Zusammenfassung nennt „1 skipped“.
    - Mit „Overwrite“ bekommt `c.jpg` nach „Apply“ 46.05/11.1.
  - `invalidZoneBlocksApply`: Zone „Europe/Berlinn“ → `#syncApply` gesperrt, `#syncZone` mit Stilklasse `invalid`.
  - `cancelChangesNothing`: „Cancel“ → keine Datei geändert, Undo gesperrt.
  - `trackSyncIsDisabledWithoutFolder`.
- [ ] **Step 2:** `.\gradlew.bat uiTest --tests tf.geotagger.main.GeoTaggerSyncUiTest` → rot.
- [ ] **Step 3:** Umsetzen.
- [ ] **Step 4:** `.\gradlew.bat build`, danach dieselbe UI-Klasse grün; bei Tests, die gleich grün sind, eine Mutationsprobe.
- [ ] **Step 5: Commit** `"Track sync dialog: preview from the loaded tracks, live camera clock, one undo step"`

---

### Task 5: GPX-Datei und GPX-Ordner als Quelle, gemerkte Parameter im Dialog

**Files:**
- Modify: `main/SyncDialog.java`
- Test: `GeoTaggerSyncUiTest` (Ergänzung)

**Interfaces:**
- **Consumes:** `TrackTimeline.load(files, progress)`, `GpxReader.findGpxFiles(dir, true)`, `SyncSettings`, `SyncInput.cache`.
- **Verhalten:**
  - Wechsel auf „GPX file“ bzw. „GPX folder“, Enter im Pfadfeld, Verlassen des Feldes oder „Browse…“ starten das Lesen.
  - Gelesen wird in einem Hintergrund-Task auf einem eigenen Daemon-Thread des Dialogs, mit dem Text „Reading GPX files: 3 / 12“ in `#syncSummary`.
  - Währenddessen ist „Apply“ gesperrt. Ein Task, dessen Pfad inzwischen nicht mehr gilt, verwirft sein Ergebnis.
  - **Cache:** Das Ergebnis landet in `SyncInput.cache` unter dem Datei- bzw. Ordnerpfad. Ein zweites Öffnen in derselben Sitzung liest nicht neu.
  - **Fehler:** Unter der Zusammenfassung erscheint je Datei `"broken.gpx: <Grund>"` (höchstens 5, dann `"…"`). Ohne einen einzigen Punkt bleibt „Apply“ gesperrt.
  - **Vorbelegung:** Beim Öffnen setzt der Dialog alle Eingaben aus `SyncSettings.load()`, außer der Zeitzone. Ist die gemerkte Quelle „Loaded tracks“, aber keiner geladen, wird „GPX file“ gewählt, sonst „GPX folder“, je nachdem, welcher Pfad gemerkt ist.

- [ ] **Step 1: Fehlschlagende Tests:**
  - `gpxFileSource`: Der Pfad von `tour.gpx` wird per `robot.interact` gesetzt und mit `fireEvent(new ActionEvent())` bestätigt; der Track wird nicht vorher geladen → `a.jpg` ist `sync-matched`.
  - `gpxFolderSourceSearchesSubfolders`: `tour.gpx` liegt in `gps/2026/` → Ordner `gps` → Treffer.
  - `unreadableGpxIsReported`: `broken.gpx` („no xml“) als Datei → die Meldung enthält `broken.gpx`, Apply gesperrt.
  - `parametersAreRemembered`: Offset +1 min, „Nearest“, „Ignore future“ → Apply; erneut öffnen → dieselben Werte, die Zone aber wieder vorbelegt.
- [ ] **Step 2–4:** rot → umsetzen → `.\gradlew.bat build`, danach `.\gradlew.bat uiTest --tests tf.geotagger.main.GeoTaggerSyncUiTest` grün.
- [ ] **Step 5: Commit** `"Track sync: GPX file or folder (with subfolders) read in the background, last parameters remembered"`

---

### Task 6: Spec, README, Suite, manuelle Prüfung

- [ ] **Step 1: Spec** [„Track-Sync-Dialog“](../specs/2026-11-03-geotagger-design.md):
  - Quelle Ordner mit Unterordnern, Lesen im Hintergrund, Sitzungs-Cache.
  - Vorbelegung der Zone mit Herstellerzone.
  - Gemerkte Parameter.
  - „Dateien“ je nach eigener Auswahl, falls der Nutzer die Entscheidung oben bestätigt.
  - Unter „Werkzeugleiste“ „Track-Sync“ in „Track sync…“ umbenennen.
- [ ] **Step 2: README:** „Track sync“ unter Status ergänzen, „Planned next“ anpassen.
- [ ] **Step 3:** `.\gradlew.bat build uiTest` → alle Unit- und UI-Tests grün (Hände weg von Maus und Tastatur).
- [ ] **Step 4: Commit** `"Spec and README: track sync dialog"`
- [ ] **Step 5: Manuell prüfen (Nutzer):**
  1. Ordner mit DSLR-Fotos ohne Koordinaten und passender GPX-Datei öffnen, „Track sync…“ → die Zone ist aus `Canon:TimeZone` vorbelegt.
  2. Die Kamera-Uhr im Dialog mit dem Display der Kamera vergleichen, den Offset einstellen, bis beide übereinstimmen → Vorschau grün.
  3. Quelle GPX-Ordner (Archiv mit Unterordnern) → Fortschritt, danach Treffer; zweites Öffnen ohne erneutes Lesen.
  4. „Apply“ → Positionen auf der Karte am Track; ein Undo nimmt alles zurück.
  5. „Write time zone“ an, speichern → `OffsetTimeOriginal` im Bild-Info-Reiter.
  6. Dialog erneut öffnen → die Parameter sind gemerkt, die Zone ist neu vorbelegt.

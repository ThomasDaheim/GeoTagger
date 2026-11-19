# GeoTagger Plan 7: Filter, SRTM1, „Nearest for all“, Feinschliff – Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Die Punkte aus dem Vergleich mit GeoSetter vom 19.11.2026 als ein Paket:
- kombinierbare Filter statt fester Listen;
- „Nearest for all“ in „Choose place“;
- SRTM1 als Standard (SRTM3 außerhalb der ESA-Abdeckung) und Upgrade vorhandener SRTM3-Kacheln auf Rückfrage;
- kleine Korrekturen im Sync-Dialog und bei den Meldungen von openrouteservice.

**Architecture:**
- **GeoHelper `tf.geo.service`:**
  - `OpenRouteServiceElevationProvider` gibt den Fehlertext des Dienstes weiter; Quota bei 429 wird als Quota erkannt.
  - `SrtmFormat`, `SrtmTile`, `SrtmElevationProvider` und `SrtmDownloader` kennen die SRTM1-Abdeckung (60° N bis 56° S), das Upgrade SRTM3 → SRTM1 und ersetzen eine SRTM1-Kachel nie durch SRTM3.
- **GeoTagger:**
  - `model.FileFilter` (neu) ersetzt `MediaFilter`: fünf Gruppen, die mit UND verknüpft werden.
  - Die Filterleiste zeigt Chips, die per Klick weiterschalten.
  - „Find altitude“ fragt auch nach Upgrades.
  - `PlaceChoiceDialog` bekommt einen dritten Knopf.

**Tech Stack:** wie bisher.

**Spec:** [2026-11-03-geotagger-design.md](../specs/2026-11-03-geotagger-design.md): „Hauptfenster“ (Statusfilter), „Datenfluss 3. Anreichern“ (Ort mit Auswahl, Höhe), „Track-Sync-Dialog“. Vorgänger: [Plan 5b](2026-11-17-plan-5b-hoehe.md), [Plan 5c](2026-11-16-plan-5c-ortsauswahl.md), [Plan 6](2026-11-18-plan-6-track-sync.md).

## Global Constraints

- Alle Global Constraints der Pläne 5–6 gelten weiter:
  - Lizenzkopf in jeder `.java`-Datei;
  - Javadoc vollständig;
  - keine Compiler-Warnungen, auch in Tests;
  - kein Netz in Tests;
  - UI-Tests gezielt per `--tests`, die gesamte Suite vor und nach dem Abschluss-Review; nie `build` zusammen mit `--tests`;
  - Arbeit direkt auf `master`, Pushes macht der Nutzer.
- **GeoHelper** ist zuerst dran (Task 1–2), danach `gradlew build` dort, das nach mavenLocal installiert; erst dann die GeoTagger-Tasks.
- **Entscheidungen des Nutzers vom 19.11.2026:**
  - **Filter** wie im Filtermenü von GeoSetter: kombinierbar und schnell ein- und auszuschalten, statt fester Listen.
  - **„Nearest for all“:** nimmt für diese und alle folgenden Gruppen des Laufs den nächsten Ort, ohne Rückfrage; die Einstellung „Umkreis“ bleibt unverändert.
  - **SRTM1-Upgrade auf Rückfrage:** Ist SRTM1 eingestellt und liegt für eine Kachel nur SRTM3 im Ordner, gehört sie in die Rückfrage vor dem Lauf. Die SRTM1-Datei ersetzt die SRTM3-Datei im Ordner (GPXEditor liest beide Größen).
  - **Standard-Download SRTM1**, außerhalb von 60° N bis 56° S automatisch SRTM3 von viewfinderpanoramas.
- **Gilt weiter (Entscheidung vom 05.11.2026):** Eine bearbeitete Datei verlässt den aktiven Filter sofort, es gibt keine „klebenden“ Zeilen.
- **Messwerte vom 19.11.2026** (Grundlage der Entscheidungen):
  - Am Comer See liegt SRTM3 bilinear 30–60 m unter GeoSetter, SRTM1 bilinear innerhalb von etwa 10 m. Die Ursache sind 90-m-Zellen mit bis zu 120 m Höhenunterschied zwischen den Ecken. Die Kacheln sind nicht verschoben (beste Übereinstimmung ohne Versatz).
  - Downloads: SRTM1 bis 15 MB je Kachel (ESA), SRTM3 bis 36 MB je Block.

## Review Focus

1. **Nie schlechter:** Ein SRTM3-Block enthält viele Kacheln. Wird er geladen, darf er eine vorhandene SRTM1-Kachel nicht überschreiben. → `SrtmDownloaderTest.srtm3BlockKeepsExistingSrtm1Tiles` (Task 2)
2. **Upgrade schlägt fehl** (404, offline): Die SRTM3-Kachel bleibt nutzbar, der Lauf rechnet mit ihr, und in der Sitzung wird nicht erneut nach dem Upgrade gefragt. → `GeoTaggerAltitudeUiTest.failedUpgradeKeepsSrtm3AndIsNotAskedAgain` (Task 3)
3. **Kachel außerhalb der ESA-Abdeckung** (z. B. N63, Island) mit SRTM1 eingestellt: Download als SRTM3-Block, und die SRTM3-Kachel gilt dort nicht als „upgradebar“. → `SrtmElevationProviderTest.noUpgradeOutsideSrtm1Coverage`, `SrtmDownloaderTest.srtm1OutsideCoverageFallsBackToSrtm3` (Task 2)
4. **Filter ergeben eine leere Liste:** keine Exception, die Aktionen sind gesperrt, die Chips zählen weiter, „Reset“ zeigt wieder alles. → `GeoTaggerFilterUiTest.emptyCombinationDisablesActions` (Task 5)
5. **Gespeicherte Filter aus einer älteren Version** (z. B. der Schlüssel des alten `MediaFilter` oder ein unbekannter Wert): Es gilt „any“ und nichts stürzt ab. → `FilterSettingsTest.unknownStoredValueIsAny` (Task 5)

---

### Task 1: GeoHelper – Meldungen von openrouteservice

**Files:**
- Modify: `GeoHelper\src\main\java\tf\geo\service\OpenRouteServiceElevationProvider.java`
- Test: `OpenRouteServiceElevationProviderTest`

**Interfaces (Produces):**
- Fehlermeldungen enthalten den Text des Dienstes, wenn die Antwort einen hat:
  - `error.message` bzw. `error` als Text, z. B. `"openrouteservice API key rejected (HTTP 403): Access to this API has been disallowed"`;
  - ohne Text bleibt die bisherige Meldung.
- 429 mit „Quota“ im Text (ohne Rücksicht auf Groß- und Kleinschreibung) → `QUOTA_EXCEEDED`; 429 ohne diesen Text bleibt `RETRYABLE`.

- [ ] **Step 1: Fehlschlagende Tests:**
  - `serviceTextIsInTheMessage`: 403 mit `{"error": "Access to this API has been disallowed"}` → die Meldung enthält diesen Text; 400 mit `{"error": {"code": 2003, "message": "Parameter 'geometry' has incorrect value"}}` → enthält `"Parameter 'geometry'"`.
  - `quotaAnsweredWith429IsQuota`: 429 mit `{"error": "Rate limit exceeded - daily quota"}` → `QUOTA_EXCEEDED`; 429 mit `{}` → `RETRYABLE`.
- [ ] **Step 2–4:** rot → umsetzen → `.\gradlew.bat build` in GeoHelper grün.
- [ ] **Step 5: Commit** `"openrouteservice: the service's own error text, quota answered with 429 is quota"`

---

### Task 2: GeoHelper – SRTM1 mit SRTM3 außerhalb, Upgrade ohne Rückschritt

**Files:**
- Modify: `SrtmFormat.java`, `SrtmTile.java`, `SrtmElevationProvider.java`, `SrtmDownloader.java`, README
- Test: `SrtmTileTest`, `SrtmElevationProviderTest`, `SrtmDownloaderTest`

**Interfaces (Produces):**
- `SrtmFormat`:
  - `static boolean srtm1Covers(String tile)`: wahr für die Breiten −56 bis 59 der Südwestecke, also N59 ja, N60 nein, S56 ja, S57 nein.
  - `SRTM1.label()` = `"SRTM1 - 30 m, ESA (SRTM3 outside 60° N - 56° S)"`.
- `SrtmTile.format(Path file): Optional<SrtmFormat>`: nach Dateigröße, wie `isTile`, ohne die Datei zu lesen.
- `SrtmElevationProvider.upgradableTiles(List<LatLon> positions, SrtmFormat wanted): List<String>`:
  - Kacheln, die als SRTM3 vorliegen, wenn `wanted == SRTM1` und `srtm1Covers`;
  - sortiert, je einmal; mit `SRTM3` leer.
- `SrtmDownloader`:
  - `sources(tiles, SRTM1)`: je Kachel die ESA-Adresse, außerhalb der Abdeckung die des SRTM3-Blocks.
  - **Entpacken schreibt eine Kachel nur,** wenn sie fehlt, kaputt ist oder als SRTM3 vorliegt und die neue SRTM1 ist. Eine SRTM1-Kachel wird **nie** durch SRTM3 ersetzt.
  - `Result.missing`: Kacheln, die danach nicht im gewünschten Format da sind; bei SRTM1 außerhalb der Abdeckung gilt SRTM3 als gewünscht.
  - `Result.present`: die übrigen.

- [ ] **Step 1: Fehlschlagende Tests:**
  - `SrtmTileTest.formatBySize`: 1201² → `SRTM3`, 3601² → `SRTM1`, 3 Byte → leer.
  - `SrtmElevationProviderTest.upgradableAreSrtm3TilesWhenSrtm1IsWanted`: N46E011 als SRTM3, N45E011 als SRTM1, N44E011 fehlt → `upgradableTiles(..., SRTM1)` = `[N46E011]`; mit `SRTM3` leer.
  - `SrtmElevationProviderTest.noUpgradeOutsideSrtm1Coverage`: N63E019 als SRTM3 → leer.
  - `SrtmDownloaderTest.srtm1OutsideCoverageFallsBackToSrtm3`: `sources([N46E011, N63E019], SRTM1)` → ESA-Adresse für N46E011, `…/dem3/<Block>.zip` für N63E019.
  - `SrtmDownloaderTest.srtm1ReplacesSrtm3`: N46E011 liegt als SRTM3 da, die ESA-Zip enthält SRTM1 → danach SRTM1-Größe, `present` = `[N46E011]`.
  - `SrtmDownloaderTest.srtm3BlockKeepsExistingSrtm1Tiles`: N47E011 liegt als SRTM1 da, ein SRTM3-Block enthält N46E011 und N47E011 → N47E011 bleibt SRTM1.
  - `SrtmDownloaderTest.failedUpgradeKeepsSrtm3`: ESA antwortet mit IOException → die SRTM3-Datei ist unverändert, `missing` = `[N46E011]`, `problems` nennt den Grund.
- [ ] **Step 2–4:** rot → umsetzen → `.\gradlew.bat build` in GeoHelper grün (installiert nach mavenLocal).
- [ ] **Step 5: Commit** `"SRTM: SRTM1 with SRTM3 outside 60° N - 56° S, upgrade SRTM3 tiles, never downgrade"`

---

### Task 3: GeoTagger – „Find altitude“ mit SRTM1-Standard und Upgrade

**Files:**
- Modify:
  - `GeoTaggerPreferences.java`: `SRTM_DOWNLOAD_FORMAT` mit Standard `SRTM1`;
  - `PreferenceEditor.java`: Reihenfolge der Auswahl SRTM1 vor SRTM3;
  - `AltitudeQuestions.java`, `GeoTagger.java` (`findAltitudes`, `downloadTiles`).
- Test: `AltitudeQuestionsTest`, `GeoTaggerAltitudeUiTest`

**Interfaces (Produces):**
- `AltitudeQuestions.download(List<String> missing, List<String> upgrades, int srtm1Downloads, int srtm3Downloads): String`. Größe nach oben: 15 MB je SRTM1-Download, 36 MB je SRTM3-Block. Beispiele:
  - `"2 SRTM tiles missing (N46E011, N47E011) - download now (up to 30 MB)?"`
  - `"3 SRTM tiles only as SRTM3 (N45E009, N45E010, N46E009) - download SRTM1, better in the mountains (up to 45 MB)?"`
  - beides: `"1 SRTM tile missing (N46E011), 2 only as SRTM3 (N45E009, N45E010) - download now (up to 45 MB)?"`
  - Kachelnamen wie bisher höchstens 5, dann `", ..."`.
- **`findAltitudes`:**
  - Gefragt wird nach `missingTiles` plus `upgradableTiles(positions, format)`, ohne die in dieser Sitzung nicht gelieferten (`unavailableTiles`, Schlüssel `format|tile`).
  - Nach dem Download kommen alle Kacheln aus `Result.missing` in `unavailableTiles`.
  - „Nein“ rechnet mit den vorhandenen Kacheln, also auch mit SRTM3.

- [ ] **Step 1: Fehlschlagende Tests:**
  - `AltitudeQuestionsTest`: die drei Beispiele oben.
  - `GeoTaggerAltitudeUiTest.srtm3TileIsUpgradedAfterConfirmation`:
    - SRTM3-Kachel N46E011 mit 1000 m im Ordner, Format SRTM1 eingestellt;
    - der Fake-Download liefert eine SRTM1-Kachel mit 1200 m;
    - erwartet: die Rückfrage nennt die Kachel als „only as SRTM3“ und `a.jpg` bekommt 1200 m.
  - `GeoTaggerAltitudeUiTest.failedUpgradeKeepsSrtm3AndIsNotAskedAgain`:
    - der Fake-Download wirft eine IOException;
    - erwartet: `a.jpg` bekommt 1000 m aus SRTM3, die Warnung nennt den Grund, und ein zweiter Lauf fragt nicht mehr.
  - `GeoTaggerAltitudeUiTest.srtm3FormatAsksNoUpgrade`: Format SRTM3 → keine Rückfrage, 1000 m.
  - Die bestehenden Tests setzen das Format ausdrücklich, wo sie SRTM3 erwarten.
- [ ] **Step 2–4:** rot → umsetzen → `.\gradlew.bat build`, danach `.\gradlew.bat uiTest --tests tf.geotagger.main.GeoTaggerAltitudeUiTest` grün.
- [ ] **Step 5: Commit** `"Find altitude: SRTM1 by default, SRTM3 tiles upgraded after a question"`

---

### Task 4: „Nearest for all“ in „Choose place“

**Files:**
- Modify: `PlaceChoiceDialog.java`, `GeoTagger.java` (`choosePlaces`)
- Test: `GeoTaggerPlaceUiTest`

**Interfaces (Produces):**
- `record Choice(Place place, Scope scope)` mit `enum Scope { THIS, FOR_ALL, NEAREST_FOR_ALL }`. Bisher `forAll()`, jetzt `scope() == FOR_ALL`.
- Knopf `#placeChoiceNearestForAll` „Nearest for all“: sichtbar wie „Use for all“, also nur wenn weitere Gruppen folgen.
- `NEAREST_FOR_ALL`:
  - Diese und alle folgenden Gruppen bekommen ohne weitere Fenster ihre beste Antwort (`run.places()`, der erste Kandidat).
  - Sie zählen **nicht** als „gewählt“: Die Sublocation-Regel von `PlaceValues` gilt wie ohne Dialog.

- [ ] **Step 1: Fehlschlagender Test** `nearestForAllAnswersTheRestWithoutDialogs`:
  - zwei Gruppen (Fake mit BARCELONA und GRACIA wie in `cancelKeepsTheseFiles`);
  - im ersten Dialog `#placeChoiceNearestForAll`;
  - erwartet: kein zweiter Dialog; `a.jpg` mit Eixample, `b.jpg` mit Gràcia (jeweils der erste Kandidat); ein Undo-Schritt.
- [ ] **Step 2–4:** rot → umsetzen → `.\gradlew.bat build`, danach `.\gradlew.bat uiTest --tests tf.geotagger.main.GeoTaggerPlaceUiTest` grün.
- [ ] **Step 5: Commit** `"Choose place: Nearest for all - the best answer for the rest of the run"`

---

### Task 5: Kombinierbare Filter

**Files:**
- Create: `model/FileFilter.java`, `main/FilterSettings.java`
- Delete: `model/MediaFilter.java`, `MediaFilterTest.java`; der Test wird ersetzt durch `FileFilterTest`.
- Modify: `GeoTagger.java` (`initFilters`, `updateFilterCounts`), `GeoTaggerPreferences.java`, `GeoTagger.css`, `GeoTaggerUiTest` (Filter-IDs)
- Test: `FileFilterTest`, `FilterSettingsTest`, `GeoTaggerFilterUiTest` (neu)

**Interfaces (Produces):**
- **In `FileFilter`:**
  - `enum Presence { ANY, WITHOUT, WITH }` für Koordinaten, Ort und Höhe;
  - `enum Change { ANY, CHANGED, UNCHANGED }`;
  - `enum Kind { ANY, IMAGES, RAW, VIDEOS }`:
    - RAW nach Endung: CR2, CR3, NEF, NRW, ARW, SR2, DNG, ORF, RW2, RAF, PEF, SRW;
    - IMAGES = Bilder ohne RAW;
    - VIDEOS = `MediaType.VIDEO`.
- `public record FileFilter(Presence coordinates, Presence place, Presence altitude, Change status, Kind kind) implements Predicate<MediaFile>`:
  - `test` verknüpft alle Gruppen mit UND;
  - `static FileFilter all()`;
  - `static Map<String, Integer> counts(Collection<MediaFile> files)`: je Gruppe und Wert ungleich ANY die Anzahl, für sich allein gezählt, Schlüssel z. B. `"coordinates.WITH"`.
- **Leiste:** fünf Knöpfe mit den IDs `#filter-coordinates`, `#filter-place`, `#filter-altitude`, `#filter-status`, `#filter-kind`, dazu `#filterReset` „Reset“.
  - Ein Klick schaltet weiter: ANY → WITHOUT → WITH → ANY. Status: ANY → CHANGED → UNCHANGED → ANY. Art: ANY → IMAGES → RAW → VIDEOS → ANY.
  - **Texte:**
    - bei ANY nur der Name: „Coordinates“, „Place“, „Altitude“, „Status“, „File type“;
    - sonst mit Anzahl: „Without coordinates (773)“, „With place (1140)“, „Changed (3)“, „RAW (625)“.
  - Ein Knopf, der nicht auf ANY steht, ist hervorgehoben (Stilklasse `filter-active`).
  - Tooltip: „Click to switch: any / without / with“.
- **Preferences:** `FILTER_COORDINATES`, `FILTER_PLACE`, `FILTER_ALTITUDE`, `FILTER_STATUS`, `FILTER_KIND` als Enums mit Standard ANY.
  - `FilterSettings.load()` und `store(FileFilter)` sind package-private.
  - Gespeichert wird bei jeder Änderung; beim Start wird der Stand wiederhergestellt.
- **Verhalten:** Eine geänderte Datei verlässt den Filter sofort, wie bisher. Die Aktionen ohne Auswahl wirken auf die gefilterte Liste, auch dies wie bisher.

- [ ] **Step 1: Fehlschlagende Tests:**
  - `FileFilterTest`:
    - `groupsAreCombinedWithAnd` (mit Koordinaten UND ohne Ort);
    - `kindByExtension` (`x.CR2` RAW, `x.jpg` IMAGES, `x.mov` VIDEOS);
    - `countsPerGroupAndValue`.
  - `FilterSettingsTest`:
    - `storedFilterComesBack`;
    - `unknownStoredValueIsAny` (z. B. `"WITHOUT_COORDINATES"` aus dem alten Schlüssel bzw. `"x"`).
  - `GeoTaggerFilterUiTest` (Bilder wie in `GeoTaggerPlaceUiTest`: a mit Koordinaten ohne Ort, b mit Koordinaten und Ort, c ohne Koordinaten):
    - `clickingSwitchesThroughTheValues`: „Coordinates“ → „Without coordinates (1)“ → „With coordinates (2)“ → „Coordinates“; Liste 3 → 1 → 2 → 3.
    - `groupsCombine`: mit Koordinaten + ohne Ort → nur a.
    - `emptyCombinationDisablesActions`: ohne Koordinaten + mit Ort → Liste leer; „Find place“, „Find altitude“ und „Edit…“ gesperrt; „Reset“ → 3.
    - `filterIsRememberedAfterRestart`: Filter setzen, `manager.stop()`, neu starten → derselbe Filter.
  - `GeoTaggerUiTest`: die Stellen mit `#filter-ALL` / `#filter-WITHOUT_COORDINATES` auf die neuen IDs und Texte umstellen.
- [ ] **Step 2–4:** rot → umsetzen → `.\gradlew.bat build`, danach `.\gradlew.bat uiTest --tests tf.geotagger.main.GeoTaggerFilterUiTest --tests tf.geotagger.main.GeoTaggerUiTest` grün.
- [ ] **Step 5: Commit** `"Filters: combinable groups like GeoSetter - coordinates, place, altitude, status, file type"`

---

### Task 6: Sync-Dialog – kleine Korrekturen

**Files:**
- Modify: `SyncDialog.java`, `GeoTagger.java` (`openSyncDialog`), `SyncSettings.java`, `SyncSettingsTest`
- Test: `GeoTaggerSyncUiTest`

**Interfaces (Produces):**
- Nach „Cancel“ zeigt die Statusleiste wieder den normalen Status (`updateStatus()`), nicht „Reading the camera time zone ...“.
- Leere maximale Differenz ist ungültig: Das Feld wird rot, „Apply“ gesperrt. Leere Offset-Felder zählen weiter als 0.
- `SyncSettings.parameters(ZoneId)` entfällt (nur Tests nutzten es); `SyncSettingsTest.parametersUseTheGivenZone` entfällt mit.

- [ ] **Step 1: Fehlschlagende Tests:**
  - `cancelRestoresTheStatus`: öffnen, „Cancel“ → `#statusLabel` beginnt mit der Dateianzahl, nicht mit „Reading“.
  - `emptyMaxGapBlocksApply`: `#syncMaxGap` leer → Stilklasse `invalid`, Apply gesperrt.
- [ ] **Step 2–4:** rot → umsetzen → `.\gradlew.bat build`, danach `.\gradlew.bat uiTest --tests tf.geotagger.main.GeoTaggerSyncUiTest` grün.
- [ ] **Step 5: Commit** `"Track sync: status after Cancel, empty max. time difference is invalid"`

---

### Task 7: Spec, README, Suite, manuelle Prüfung

- [ ] **Step 1: Spec:**
  - „Hauptfenster“: Statusfilter → kombinierbare Filter (Gruppen, Werte, Weiterschalten, Reset, gemerkt).
  - „Datenfluss 3. Anreichern“: „Nearest for all“; SRTM1-Standard mit SRTM3 außerhalb; Upgrade auf Rückfrage; nie schlechter; Messwerte vom Comer See.
  - „Einstellungen“: Download-Format SRTM1 als Standard.
  - „Fehlerbehandlung“: Text des Dienstes bei openrouteservice.
- [ ] **Step 2: README:**
  - GeoTagger: Filter, „Nearest for all“, SRTM1;
  - GeoHelper: Abdeckung und Upgrade.
- [ ] **Step 3:** `.\gradlew.bat build uiTest` in GeoTagger → alles grün (Hände weg von Maus und Tastatur).
- [ ] **Step 4: Commit** `"Spec and README: combinable filters, nearest for all, SRTM1"`
- [ ] **Step 5: Manuell prüfen (Nutzer):**
  1. **Filter:** Testordner öffnen, „With coordinates“ + „Without place“ → genau die Dateien für „Find place“. Neustart → derselbe Filter. „Reset“ zeigt alles.
  2. **„Find place“** mit Auswahl im Testordner GeoTagger: im ersten „Choose place“ „Nearest for all“ → keine weiteren Fenster.
  3. **Einstellungen:** Das Download-Format steht nach dem Speichern früherer Einstellungen noch auf SRTM3, also einmal auf SRTM1 umstellen.
  4. **„Find altitude“** für die Fotos vom Comer See (Montepiatto, Brunate): Rückfrage „only as SRTM3“ mit N45E009, Download, danach Höhen nahe GeoSetter (z. B. `PXL_20250920_115017052.jpg` etwa 983 m statt 917 m).
  5. **Ohne ORS-Schlüssel bzw. mit falschem Schlüssel:** Die Meldung nennt den Text des Dienstes.
  6. **Sync-Dialog:** öffnen, „Cancel“ → Statusleiste normal; maximale Differenz leeren → rot.

# GeoTagger Plan 5b: Höhe ermitteln – Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ein Knopf „Find altitude“ ergänzt die Höhe aus den Koordinaten:
- zuerst aus lokalen SRTM-Dateien (`.hgt`); fehlende Kacheln lädt GeoTagger nach Rückfrage herunter;
- für Lücken aus openrouteservice (ORS).

**Architecture:**
- **GeoHelper `tf.geo.service`:**
  - `ElevationProvider` mit `SrtmElevationProvider` (liest SRTM1/SRTM3-Kacheln, nächster Punkt oder bilinear, kleiner Kachel-Cache) und `OpenRouteServiceElevationProvider` (POST an ORS, Blöcke bis 1000 Punkte).
  - `SrtmDownloader` lädt fehlende Kacheln: SRTM1 von ESA, SRTM3 als Blöcke von viewfinderpanoramas.
  - `HttpTransport` bekommt `post` (ORS kennt für Punktlisten nur POST).
- **GeoTagger `enrich`:**
  - `AltitudeEnricher` fragt je Datei die genaue Position ab, kein 100-m-Raster: Höhen ändern sich auf 100 m in den Bergen um Dutzende Meter.
  - Erst SRTM, dann ORS für den Rest, je nach Einstellung; Wiederholung und Abbruch wie bei „Find place“.
- **UI:**
  - Knopf „Find altitude“ mit Rückfrage für fehlende Kacheln, Hintergrund-Download mit Fortschritt, **ein** Undo-Schritt, Zusammenfassung.
  - Neue Einstellungen für Quelle, SRTM-Ordner, Download-Format, Interpolation, ORS-Key und „Check“.

**Tech Stack:** wie Plan 5; Zip über `java.util.zip`, Kacheln über `FileChannel`/`ByteBuffer` (ohne das FFM von GPXEditor).

**Vorbedingung:** [Plan 5a](2026-11-17-plan-5a-preferences.md) ist umgesetzt; Preferences laufen über JavaHelpers `ITypedPreference` / `NodePreferencesStore`.

**Spec:** [2026-11-03-geotagger-design.md](../specs/2026-11-03-geotagger-design.md), „Umfang“ (Höhe per openrouteservice oder SRTM) und „Datenfluss 3. Anreichern“. „Höhe aus dem Track“ gehört zum Sync-Dialog (Plan 6). Vorgänger: [Plan 5](2026-11-15-plan-5-ort-ermitteln.md), [Plan 5c](2026-11-16-plan-5c-ortsauswahl.md). Code-Vorlage: GPXEditor `tf.gpx.edit.elevation` (`SRTMDataHelper`, `SRTMData`, `SRTMDataReader`, `SRTMDownloader`, `OpenElevationService`), kopiert und von GPXEditor gelöst.

## Global Constraints

- Alle Global Constraints von Plan 5 und 5c gelten weiter, unter anderem:
  - kein Netz in automatischen Tests;
  - UI-Tests gezielt per `--tests`, die gesamte Suite vor und nach dem Abschluss-Review; nie `build` zusammen mit `--tests`;
  - `$git` dynamisch.
- **Testdaten:** Kacheln werden im Test erzeugt (`@TempDir`, synthetische Werte), Zips ebenso. Es kommen keine `.hgt`-Dateien ins Repo.
- **Entscheidungen des Nutzers (17.11.2026):**
  - **Quelle** als Einstellung `ALTITUDE_SOURCE`: `SRTM_FIRST` (Standard; Lücken über ORS), `SRTM_ONLY`, `ORS_ONLY`.
  - **Fehlende Kacheln:** Vor dem Lauf fragt GeoTagger nach („n Kacheln fehlen: … Herunterladen?“). „Ja“ lädt in den SRTM-Ordner, „Nein“ macht ohne sie weiter.
  - **Download-Format** als Einstellung `SRTM_DOWNLOAD_FORMAT`, Standard `SRTM3`:
    - SRTM3 (90 m) als Block-Zip von `https://viewfinderpanoramas.org/dem3/<Block>.zip` mit Lückenfüllung, auch nördlich von 60°;
    - SRTM1 (30 m) je Kachel von `https://step.esa.int/auxdata/dem/SRTMGL1/<Kachel>.SRTMGL1.hgt.zip`, nur 60° N bis 56° S. Am 17.11.2026 geprüft: N46E011 = 16 MB gezippt.
  - **Interpolation** als Einstellung `SRTM_INTERPOLATION` wie in GPXEditor: `NEAREST` oder `BILINEAR`, Standard `BILINEAR`. GPXEditors zweite Option „AVERAGE_NEIGHBOURS“ gewichtet 4 Punkte nach Abstand; bilinear ist die genaue Form davon.
- **Auswahl wie bei „Find place“:** Mit eigener Auswahl werden die ausgewählten Dateien mit Koordinaten überschrieben, ohne Auswahl die gelisteten mit Koordinaten und ohne Höhe abgefragt. Die automatische Auswahl nach dem Laden zählt nicht. Der Knopf ist gesperrt, wenn es nichts abzufragen gibt.
- **Höhe** wird auf 0,1 m gerundet gespeichert.
- **ORS-Grenzen** (kostenloser Schlüssel): 40 Anfragen pro Minute. Deshalb 1,5 s Pause zwischen Anfragen, Blöcke bis 1000 Punkte, gleiche Positionen nur einmal.
- **Speicher:** Höchstens 8 Kacheln bleiben im Speicher (SRTM1 ≈ 26 MB je Kachel); die älteste fliegt raus.

## Review Focus

1. **Kachelgrenzen und negative Koordinaten** (Süd, West, genau 0°, Punkt auf dem Rand zwischen zwei Kacheln): richtige Kachel, richtige Zeile und Spalte. → `SrtmTileTest`, `SrtmElevationProviderTest.edgesOfTheTile` (Task 1)
2. **Leere Werte in der Kachel** (−32768, „void“): kein Ergebnis bzw. bei bilinear nur die gültigen Nachbarn, nie −32768 m als Höhe. → `SrtmElevationProviderTest.voidValuesGiveNoAltitude` (Task 1)
3. **Zip mit gefährlichen Pfaden** (`../`) oder ohne `.hgt`: Es wird nichts außerhalb des SRTM-Ordners geschrieben, und es gibt eine klare Meldung. → `SrtmDownloaderTest.zipSlipIsRefused` (Task 2)
4. **ORS-Kontingent oder -Schlüssel falsch mitten im Lauf:** SRTM-Ergebnisse werden übernommen, der Rest wird als fehlgeschlagen gemeldet, und die Meldung des Dienstes erscheint. → `AltitudeEnricherTest.orsQuotaKeepsTheSrtmAltitudes` (Task 4)
5. **Abgebrochener oder fehlgeschlagener Download:** keine halben `.hgt`-Dateien; der Lauf geht mit den vorhandenen Kacheln und ORS weiter. → `SrtmDownloaderTest.failedDownloadLeavesNoFile` (Task 2)

---

### Task 1: GeoHelper – SRTM-Kacheln lesen

**Files:**
- Create in `$geo\src\main\java\tf\geo\service\`: `ElevationProvider.java`, `SrtmTile.java`, `SrtmInterpolation.java`, `SrtmElevationProvider.java`
- Test: `SrtmTileTest.java`, `SrtmElevationProviderTest.java`, `SrtmTestFiles.java` (Testhelfer: schreibt eine SRTM1/SRTM3-Kachel mit `value(row, col)`)

**Interfaces (Produces):**
- `public interface ElevationProvider`:
  - `List<OptionalDouble> elevations(List<LatLon> positions) throws GeoServiceException`: gleiche Reihenfolge wie die Eingabe, leer ohne Wert;
  - `String name()`.
- `public final class SrtmTile`:
  - `static String name(LatLon)`: Kachel der südwestlichen Ecke, z. B. `N46E011`, `S34W071`. Es zählt `Math.floor` des Werts; 0,5° Nord liegt also in `N00`, −0,5° in `S01`. Damit ist GPXEditors Sonderfall „0 zählt als S/W“ behoben.
  - `static Optional<SrtmTile> read(Path file)`: Typ aus der Dateigröße, SRTM1 = 3601² × 2 Bytes, SRTM3 = 1201² × 2 Bytes; sonst leer.
  - `OptionalDouble elevation(LatLon, SrtmInterpolation)`.
  - Format: big-endian `short`, Zeilen von Nord nach Süd, Spalten von West nach Ost, −32768 = leer.
- `public enum SrtmInterpolation { NEAREST, BILINEAR }`:
  - `BILINEAR` aus den 4 umgebenden Gitterpunkten.
  - Ist einer davon leer, gelten nur die gültigen, gewichtet. Sind alle leer, gibt es keinen Wert.
- `public final class SrtmElevationProvider implements ElevationProvider`:
  - Konstruktor `(Path folder, SrtmInterpolation interpolation)`; Dateiname `<Kachel>.hgt` direkt im Ordner, Groß-/Kleinschreibung egal;
  - LRU-Cache mit 8 Kacheln;
  - `List<String> missingTiles(List<LatLon>)`: Kacheln ohne lesbare Datei, sortiert, ohne Duplikate;
  - `name()` = `"SRTM"`.
  - Fehlt eine Kachel, ist der Wert leer; das ist kein Fehler.

- [ ] **Step 1: Fehlschlagende Tests**
  - `SrtmTileTest.namesFollowTheSouthWestCorner`:
    - (46.67, 11.16) → `N46E011`
    - (−33.9, −70.6) → `S34W071`
    - (0.5, −0.5) → `N00W001`
    - (−0.5, 0.5) → `S01E000`
    - (46.0, 11.0) → `N46E011`
  - `SrtmElevationProviderTest` mit SRTM3-Kachel `N46E011`, Wert = `1000 + row - col`:
    - `nearestTakesTheClosestGridPoint`
    - `bilinearWeightsTheFourNeighbours`: Punkt genau zwischen 4 Gitterpunkten = Mittelwert
    - `edgesOfTheTile`: Nordwest-Ecke = Zeile 0 / Spalte 0, Südost-Ecke = Zeile 1200 / Spalte 1200
    - `voidValuesGiveNoAltitude`
    - `srtm1IsReadAsWell`: 3601er-Kachel
    - `missingTileGivesNoAltitudeAndIsListed`
    - `wrongFileSizeIsIgnored`
- [ ] **Step 2–4:** `.\gradlew.bat test --tests tf.geo.service.*` (in `$geo`) rot → implementieren → `.\gradlew.bat build` → 59 Tests grün.
- [ ] **Step 5: Commit** `"Services: SRTM tiles (SRTM1 / SRTM3), nearest or bilinear"`

---

### Task 2: GeoHelper – SRTM herunterladen

**Files:**
- Create: `SrtmFormat.java`, `SrtmDownloader.java`, `FileDownload.java` (Interface), `JdkFileDownload.java`
- Test: `SrtmDownloaderTest.java`

**Interfaces (Produces):**
- `public enum SrtmFormat`:
  - `SRTM1`: ESA-URL je Kachel;
  - `SRTM3`: viewfinderpanoramas-Block.
- `public interface FileDownload { void download(URI uri, Path target) throws IOException; }`
  - `JdkFileDownload` nutzt `java.net.http`: Connect-Timeout 10 s, Request-Timeout 5 min; Redirects folgen; Status ≠ 200 → `IOException`.
- `public final class SrtmDownloader`:
  - Konstruktor `(FileDownload download)`;
  - `static String srtm3Block(LatLon)`: GPXEditors `getSRTM3NameForCoordinates` samt Sonderfällen übernehmen (`SRTMDownloader.java:199-305`, dazu die Erwartungen aus `TestSRTM.java:313-332`, z. B. `A31`, `L10`, `SA19`, `GL-North`);
  - `List<URI> sources(List<String> tiles, SrtmFormat)`: SRTM3-Blöcke ohne Duplikate;
  - `List<String> download(List<String> tiles, SrtmFormat format, Path folder, BiConsumer<Integer, Integer> progress) throws IOException`: gibt die danach vorhandenen Kacheln zurück.
- **Ablauf je Quelle:**
  1. Ins Temp-Verzeichnis laden.
  2. Die `.hgt`-Einträge entpacken: flach in den Ordner, Namen in Großbuchstaben, ESA-Namen `N46E011.SRTMGL1.hgt` → `N46E011.hgt`.
  3. Zip-Slip-Schutz: Einträge mit `..` oder absolutem Pfad werden abgelehnt.
  4. Jede Datei erst unter `*.part` schreiben und am Ende umbenennen.
  5. Vorhandene Dateien bleiben.

- [ ] **Step 1: Fehlschlagende Tests**
  - `srtm3BlocksLikeGpxEditor`: die Fälle aus `TestSRTM.java:313-332`.
  - `srtm1SourceIsTheEsaTile`: `N46E011` → `https://step.esa.int/auxdata/dem/SRTMGL1/N46E011.SRTMGL1.hgt.zip`.
  - `extractsTheHgtFiles`: Fake-Download liefert ein im Test gebautes Zip mit `L32/N46E011.hgt` und `L32/N47E011.hgt` → beide im Ordner.
  - `zipSlipIsRefused`: Eintrag `../evil.hgt` → `IOException`, nichts außerhalb des Ordners.
  - `failedDownloadLeavesNoFile`: der Fake wirft → keine `.hgt`- und keine `.part`-Datei.
- [ ] **Step 2–4:** rot → implementieren → `.\gradlew.bat build` (in `$geo`) → 64 Tests grün.
- [ ] **Step 5: Commit** `"Services: download SRTM tiles (SRTM1 from ESA, SRTM3 from viewfinderpanoramas)"`

---

### Task 3: GeoHelper – openrouteservice

**Files:**
- Modify: `HttpTransport.java` (`Response post(URI uri, String json, Map<String, String> headers) throws IOException`), `JdkHttpTransport.java`, `FakeTransport.java` (Test; merkt sich die Bodies)
- Create: `OpenRouteServiceElevationProvider.java`
- Test: `OpenRouteServiceElevationProviderTest.java`

**Interfaces (Produces):**
- `public final class OpenRouteServiceElevationProvider implements ElevationProvider`:
  - Konstruktor `(String apiKey, HttpTransport transport)`; `name()` = `"openrouteservice"`;
  - `Duration minRequestInterval()` = 1,5 s; Blöcke von höchstens 1000 Punkten (der Aufrufer wartet zwischen den Blöcken).
- **Anfragen** wie in GPXEditor:
  - 1 Punkt: `POST https://api.openrouteservice.org/elevation/point` mit `{"format_in":"point","format_out":"geojson","geometry":[lon,lat]}`;
  - mehr Punkte: `POST https://api.openrouteservice.org/elevation/line` mit `{"format_in":"polyline","format_out":"geojson","geometry":[[lon,lat],…]}`;
  - Header `Authorization: <Key>`, `Content-Type: application/json`.
- **Antwort:** `geometry.coordinates`, als `[lon,lat,ele]` bei einem Punkt bzw. als Liste davon.
- **Fehler:**

  | Antwort | Ergebnis |
  |---|---|
  | 401, 403 mit „Quota exceeded“ | `QUOTA_EXCEEDED` |
  | 401, 403 sonst | `CONFIGURATION` |
  | 429, 5xx, `IOException` | `RETRYABLE` |
  | 404 (außerhalb der Daten) | leere Werte für diesen Block |
  | sonst | `FAILED` mit Status |

- [ ] **Step 1: Fehlschlagende Tests:**
  - `postsOnePointAndReadsItsElevation`, `postsALineForSeveralPoints` (Body prüfen), `splitsIntoBlocksOf1000`;
  - `quotaAndKeyErrors`, `outsideTheDataIsEmpty`.
- [ ] **Step 2–4:** rot → implementieren → `.\gradlew.bat build` → 69 Tests grün; README von GeoHelper ergänzen (Höhe, SRTM, Download, ORS).
- [ ] **Step 5: Commit** `"Services: elevation from openrouteservice, POST in HttpTransport"`

---

### Task 4: GeoTagger – Höhe im Lauf

**Files:**
- Create in `$app\src\main\java\tf\geotagger\enrich\`: `AltitudeSource.java`, `AltitudeRun.java`, `AltitudeEnricher.java`, `AltitudeSummary.java`
- Test: `AltitudeEnricherTest.java`, `AltitudeSummaryTest.java`, `FakeElevationProvider.java`

**Interfaces (Produces):**
- `public enum AltitudeSource { SRTM_FIRST, SRTM_ONLY, ORS_ONLY; String label(); }`
- `public record AltitudeRun(Map<MediaFile, Double> altitudes, List<MediaFile> notFound, List<MediaFile> failed, String problem)`
- `public final class AltitudeEnricher`:
  - Konstruktor `(ElevationProvider srtm, ElevationProvider ors, AltitudeSource source, Sleeper sleeper)`; je nach Quelle darf `srtm` oder `ors` null sein;
  - `AltitudeRun run(List<MediaFile> files, BiConsumer<Integer, Integer> progress) throws InterruptedException`.
- **Ablauf:**
  1. SRTM für alle Dateien mit Koordinaten in einem Aufruf, sofern die Quelle es erlaubt.
  2. Der Rest geht an ORS, sofern erlaubt: gleiche Positionen einmal, Blöcke zu 1000, `minRequestInterval` zwischen den Blöcken.
  3. Wiederholungen nach 1 / 2 / 4 s bei `RETRYABLE`. Bleibt ein Fehler oder kommen `QUOTA_EXCEEDED` / `CONFIGURATION`, bricht der ORS-Teil ab und die restlichen Dateien sind `failed`.
  4. Höhe auf 0,1 m runden.
- `AltitudeSummary.text(run)`: `"Altitude found for 3 files, 1 without result, 2 failed"`; `hasProblems(run)`.

- [ ] **Step 1: Fehlschlagende Tests:**
  - `srtmFirstAsksOrsOnlyForTheGaps`, `srtmOnlyNeverAsksOrs`, `orsOnlyNeverReadsSrtm`;
  - `samePositionsAreAskedOnce`, `orsQuotaKeepsTheSrtmAltitudes`, `retriesWithGrowingWaits`;
  - `roundedToTenthOfAMetre`, `filesWithoutCoordinatesAreSkipped`;
  - `AltitudeSummaryTest` mit 2 Fällen.
- [ ] **Step 2–4:** rot → implementieren → `.\gradlew.bat build` → 174 Tests grün.
- [ ] **Step 5: Commit** `"Enrich: altitude from SRTM, gaps from openrouteservice"`

---

### Task 5: Preferences – Höhe

**Files:**
- Modify: `GeoTaggerPreferences.java`, `PreferenceEditor.java`
- Create: `$app\src\main\java\tf\geotagger\main\AltitudeServiceFactory.java`
- Test: `AltitudeServiceFactoryTest.java`

**Interfaces (Produces):**
- **Preferences** (seit Plan 5a über `ITypedPreference`, Auswahl-Einstellungen als typisierte Enums):
  - `ALTITUDE_SOURCE` (`AltitudeSource.class`, `SRTM_FIRST`)
  - `SRTM_FOLDER` (`String`, `""`)
  - `SRTM_DOWNLOAD_FORMAT` (`SrtmFormat.class`, `SRTM3`)
  - `SRTM_INTERPOLATION` (`SrtmInterpolation.class`, `BILINEAR`)
  - `ORS_API_KEY` (`String`, `""`)
- **`AltitudeServiceFactory`:**
  - `static SrtmElevationProvider srtm(String folder, SrtmInterpolation interpolation) throws GeoServiceException`: `CONFIGURATION` mit `"Please choose the folder of the SRTM files in the preferences"`, wenn der Ordner leer ist oder fehlt;
  - `static OpenRouteServiceElevationProvider ors(String apiKey) throws GeoServiceException`: `CONFIGURATION` ohne Schlüssel;
  - `static AltitudeEnricher enricher(Sleeper sleeper) throws GeoServiceException`: baut nach `ALTITUDE_SOURCE` nur, was gebraucht wird.
- **`PreferenceEditor`**, Abschnitt „Altitude“ unter dem Ortsdienst:
  - Quelle (`ChoiceBox`);
  - SRTM-Ordner (`TextField` und „Browse…“ mit `DirectoryChooser`);
  - Download-Format (`ChoiceBox`: „SRTM3 - 90 m, viewfinderpanoramas“ / „SRTM1 - 30 m, ESA“);
  - Interpolation (`ChoiceBox`);
  - ORS-Key (`TextField`) mit „Check“ wie beim Ortsdienst: Höhe von München im Hintergrund, z. B. „OK - 520 m“.

- [ ] **Step 1: Fehlschlagende Tests:**
  - `srtmNeedsAFolder`, `orsNeedsAKey`;
  - `enricherBuildsOnlyWhatTheSourceNeeds`: `SRTM_ONLY` ohne Key ist kein Fehler.
- [ ] **Step 2–4:** rot → implementieren → `.\gradlew.bat build` → 177 Tests grün; der Dialog wird manuell geprüft.
- [ ] **Step 5: Commit** `"Preferences: altitude source, SRTM folder / download / interpolation, openrouteservice key"`

---

### Task 6: „Find altitude“ in der Werkzeugleiste

**Files:**
- Modify: `GeoTagger.fxml` (Button `findAltitudeButton` „Find altitude“ nach „Find place“), `GeoTagger.java`
- Test: `$app\src\test\java\tf\geotagger\main\GeoTaggerAltitudeUiTest.java` (neu)

**Interfaces (Produces):**
- **Controller:**
  - `void setAltitudeServices(Callable<AltitudeEnricher> enricher, Callable<SrtmElevationProvider> srtm, Predicate<List<String>> confirmDownload, SrtmDownloader downloader)` (package-private, für UI-Tests). Standard sind die Factory, eine `CONFIRMATION`-Alert-Rückfrage und `new SrtmDownloader(new JdkFileDownload())`.
  - `static List<MediaFile> altitudeScope(List<MediaFile> selected, List<MediaFile> listed)` wie `placeScope`, nur mit „ohne Höhe“.
- **`findAltitudes()`:**
  1. Scope bestimmen.
  2. Erlaubt die Quelle SRTM, `missingTiles` der Positionen ermitteln. Fehlen welche und bestätigt der Nutzer die Rückfrage (`"<n> SRTM tiles missing (N46E011, …) - download now (about <x> MB)?"`), lädt ein Hintergrund-Task sie mit Fortschritt herunter; danach geht es weiter. Ein Download-Fehler erscheint in einer Meldung, dann geht es mit den vorhandenen Kacheln weiter.
  3. Hintergrund-Task `AltitudeEnricher.run`, Nachricht `"Finding altitudes: <n> / <total>"`.
  4. Ergebnis als **eine** `ChangeFieldsAction("Find altitude", …)` mit `ALTITUDE`, dazu Zusammenfassung und Hinweis bei Problemen wie bei „Find place“.
  - `findAltitudeButton` ist gesperrt, wenn `busy`, kein Ordner oder ein leerer Scope.

- [ ] **Step 1: Fehlschlagende Tests** (Fakes ohne Netz, Kachel synthetisch in `@TempDir`):
  - `findAltitudeFillsListedFilesWithoutAltitude`: ein Undo setzt zurück.
  - `findAltitudeOverwritesSelectedFiles`.
  - `findAltitudeIsDisabledWithNothingToLookUp`.
  - `missingTilesAreDownloadedAfterConfirmation`: Bestätigung „ja“, der Fake-Download legt die Kachel ab → Höhe aus SRTM.
  - `declinedDownloadContinuesWithout`: „nein“ → die Höhe kommt vom Fake-ORS.
- [ ] **Step 2–4:** `.\gradlew.bat uiTest --tests tf.geotagger.main.GeoTaggerAltitudeUiTest` rot → implementieren → `.\gradlew.bat build`, dann dieselbe UI-Klasse grün.
- [ ] **Step 5: Commit** `"Find altitude: SRTM with download on request, gaps from openrouteservice - one undo step"`

---

### Task 7: Spec, README, Suite, manuelle Prüfung

- [ ] **Step 1: Spec:**
  - „Datenfluss 3. Anreichern“: Höhe je Datei ohne Raster; Quellen, Download, Interpolation, ORS-Grenzen.
  - „Einstellungen“: die neuen Einträge.
  - „Höhe aus Track“ bleibt beim Sync-Dialog (Plan 6).
- [ ] **Step 2: README** (GeoTagger): „Find altitude“ ergänzen.
- [ ] **Step 3:** `.\gradlew.bat build uiTest` → 177 Tests und 71 UI-Tests grün (Hände weg von Maus und Tastatur).
- [ ] **Step 4: Commit** `"Spec and README: find altitude"`
- [ ] **Step 5: Manuell prüfen** (Nutzer):
  1. SRTM-Ordner wählen, z. B. den von GPXEditor. Fotos aus Südtirol: Höhen plausibel, ohne Download, wenn die Kacheln schon da sind.
  2. Ein Ordner aus einer neuen Gegend → Rückfrage mit Kachelnamen; Download mit Fortschritt (SRTM3 und einmal SRTM1); danach Höhen.
  3. Download ablehnen → mit ORS-Key kommen Höhen von ORS, ohne Key eine klare Meldung.
  4. Interpolation umstellen → leicht andere Höhen an Hängen.
  5. „Check“ für den ORS-Key, gültig und falsch.
  6. Speichern: Die Höhe steht in EXIF `GPSAltitude` bzw. bei Videos in den QuickTime-Tags.

---

## Abschluss von Plan 5b

- GeoHelper 69 Tests, GeoTagger 177 Tests und 71 UI-Tests grün, keine Compilerwarnungen.
- Abschluss-Review durch einen frischen Reviewer; Push durch den Nutzer.
- Danach Plan 6: Track-Sync-Dialog.

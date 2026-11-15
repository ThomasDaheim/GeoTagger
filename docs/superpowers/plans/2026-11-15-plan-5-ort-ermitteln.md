# GeoTagger Plan 5: Ort ermitteln – Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ein Knopf „Find place“ ergänzt Ländercode, Land, Region, Ort und Sublocation aus den Koordinaten, wahlweise über GeoNames oder Mapbox. Außerdem verschiebt die automatische Auswahl nach dem Laden die Karte nicht mehr.

**Architecture:**
- **GeoHelper `tf.geo.service`** (neu):
  - `ReverseGeocoder` mit den Implementierungen `GeoNamesReverseGeocoder` und `MapboxReverseGeocoder`; Ergebnis ist ein `Place`.
  - Fehler meldet `GeoServiceException` mit einer Art (`RETRYABLE`, `QUOTA_EXCEEDED`, `CONFIGURATION`, `FAILED`).
  - HTTP läuft über das schmale Interface `HttpTransport`. Die Implementierung `JdkHttpTransport` nutzt `java.net.http`; Tests setzen eine Attrappe ein.
  - Die Dienste sind bewusst einfache Einzelabfragen ohne Cache, Drosselung und Wiederholung.
- **GeoTagger `tf.geotagger.enrich`** (neu), laut Spec zuständig für Zusammenfassen, Drosselung, Cache und Fortschritt:
  - `GridCell` rundet auf ein Raster von 0,001° (etwa 100 m).
  - `PlaceEnricher` fragt je Rasterzelle einmal ab, cacht im Speicher, wartet zwischen den Anfragen und wiederholt mit wachsender Wartezeit.
- **UI:**
  - Knopf „Find place“ in der Werkzeugleiste. Er läuft im Hintergrund mit Fortschritt; das Ergebnis ist **ein** Undo-Schritt, dazu eine Zusammenfassung.
  - In den Preferences: Dienst, GeoNames-Benutzer, Mapbox-Token und Sprache.

**Tech Stack:** wie Plan 4c; neu in GeoHelper: Jackson databind (BOM 2.21.2, wie GeoTagger) und `java.net.http`.

**Spec:** [2026-11-03-geotagger-design.md](../specs/2026-11-03-geotagger-design.md): Abschnitte „Umfang“, „Architektur / GeoHelper“, „Datenfluss 3. Anreichern“ und „Fehlerbehandlung“. Task 7 passt die Spec an die Entscheidungen unten an. Vorgänger: [Plan 4c](2026-11-07-plan-4c-layout.md). Die Höhe kommt in **Plan 5b**.

## Global Constraints

- Alles aus den Global Constraints von Plan 4 bis 4c gilt weiter:
  - Englisch, `master`, Lizenz-Header, vollständiges Javadoc, keine Compilerwarnungen (auch in `src/test`).
  - `$app` (GeoTagger) und `$geo` (GeoHelper) wie in Plan 4; GeoHelper bleibt `tf.GeoHelper:GeoHelper:0.1` und wird über `gradlew build` per `publishToMavenLocal` bereitgestellt.
  - `$git` wird dynamisch ermittelt, weil sich der Ordner von GitHub Desktop bei Updates ändert (seit 15.11.2026 `app-3.6.11`):
    `$git = (Get-ChildItem "$env:LOCALAPPDATA\GitHubDesktop\app-*\resources\app\git\cmd\git.exe" | Sort-Object { [version]($_.FullName -replace '.*app-([\d.]+).*','$1') } | Select-Object -Last 1).FullName`
- **JavaHelper 1.18:** Der Nutzer hat JavaHelper und GPXEditor am 15.11.2026 released und die Versionen erhöht (JavaHelper 1.18, GPXEditor 5.11). GeoTagger stellt in Task 1 von `tf.JavaHelper:JavaHelper:1.17` auf `1.18` um (liegt in mavenLocal). GeoHelper und ExifToolHelper nutzen JavaHelper nicht; GPXEditor ist für keines der drei Repos eine Abhängigkeit.
  - Kein Drag & Drop über den Roboter.
- **UI-Tests:** je Task nur die neuen und geänderten UI-Tests per `--tests`, je einmal rot und einmal grün. Die komplette `uiTest`-Suite läuft einmal vor dem Abschluss-Review und einmal nach dessen Fixes.
- **Kein Netz in automatischen Tests:** Dienste werden über `HttpTransport`-Attrappen bzw. ein Fake-`ReverseGeocoder` getestet. Echte Abfragen gibt es nur im manuellen Test (Task 7).
- **Keine E-Mail-Adresse o. Ä. im User-Agent:** `JdkHttpTransport` sendet `User-Agent: GeoHelper`.
- **Entscheidungen des Nutzers (15.11.2026):**
  - **Sprache** der Ortsnamen wird in den Preferences gewählt: Sprachcode, z. B. `de` oder `en`. Ein leerer Wert schickt keinen Sprachparameter, dann liefert der Dienst seine Vorgabe.
  - **Auswahl überschreibt:** Mit Auswahl bekommen alle ausgewählten Dateien mit Koordinaten einen neuen Ort, auch wenn sie schon einen haben. Ohne Auswahl werden, wie in der Spec, alle gelisteten (gefilterten) Dateien mit Koordinaten und ohne Ort abgefragt.
  - **Keine Zeitzone aus den Koordinaten:** Die Zeitzone kommt nur aus der Datei, dem Sync-Dialog (Plan 6) oder dem Bearbeiten-Dialog. Grund: Bei DSLR-Fotos stimmt die Zeitzone des Ortes nur, wenn die Kamerauhr auf Ortszeit stand.
  - **Aufteilung:** Plan 5 = Ort, Plan 5b = Höhe (SRTM, openrouteservice, „Find altitude“).
  - **Karte nach dem Laden („Variante 1“):** Die erste Datei wird weiter automatisch ausgewählt (rote Markierung, Vorschau), aber die Karte bleibt auf alle Dateien eingepasst.
- **Ort konsistent:** Ein gefundener Ort setzt immer alle fünf Ortsfelder. Teile, die der Dienst nicht liefert, werden geleert (`null`), damit kein alter Ort mit einem neuen gemischt wird.
- **Kein Abbrechen** eines laufenden „Find place“ in Plan 5. Wie beim Laden und Speichern ist das Fenster solange gesperrt; die Drosselung hält Läufe kurz (GeoNames 250 ms, Mapbox 100 ms je Anfrage).

## Review Focus

1. **Kontingent mitten im Lauf erschöpft** (GeoNames: Stündlich-/Tageslimit, Status 18/19/20): Die bis dahin gefundenen Orte werden als ein Undo-Schritt übernommen. Die übrigen Dateien bleiben unverändert, und die Meldung des Dienstes erscheint in der Zusammenfassung. → `PlaceEnricherTest.quotaStopsTheRun` (Task 4)
2. **Überschreiben mit unvollständigem Ort** (z. B. Wildnis ohne Ort): Die fehlenden Teile werden geleert, nicht mit alten Werten gemischt. → `GeoTaggerPlaceUiTest.findPlaceOverwritesSelectedFiles` (Task 6)
3. **Dienst oder Sprache zwischen zwei Läufen geändert:** Der Cache ist je Dienst und Sprache getrennt, es gibt keine veralteten Namen. → `PlaceServiceFactoryTest.cacheKeyDependsOnServiceAndLanguage` (Task 5)
4. **Rastergrenzen und negative Koordinaten** (West, Süd, −0,0005): Die Zellen sind eindeutig und stabil. → `GridCellTest` (Task 4)
5. **Nichts abzufragen** (Auswahl ohne Koordinaten, alle haben schon einen Ort): Kein Dienstaufruf, kein leerer Undo-Schritt, ein Hinweis in der Statusleiste. → `GeoTaggerPlaceUiTest.nothingToLookUpChangesNothing` (Task 6)

---

### Task 1: JavaHelper 1.18, Karte bleibt nach dem Laden eingepasst

**Files:**
- Modify: `$app\build.gradle` (`implementation 'tf.JavaHelper:JavaHelper:1.18'`)
- Modify: `$app\src\main\java\tf\geotagger\main\GeoTagger.java` (`loadFolder` → `onSucceeded`, `selectionChanged`)
- Test: `$app\src\test\java\tf\geotagger\main\GeoTaggerMapUiTest.java`

- [ ] **Step 0: JavaHelper 1.18**

In `$app\build.gradle` von `1.17` auf `1.18` umstellen.
Run: `.\gradlew.bat build`
Expected: 133 Tests grün, keine Compilerwarnungen (Deprecations aus 1.18 werden behoben).
Commit: `& $git -C $app commit -am "JavaHelper 1.18"`. Ob die UI unverändert läuft, zeigt die gesamte UI-Suite in Task 7.

**Ursache:** `onSucceeded` ruft `mapPane.fitToFiles(files)` und danach `select(0)` auf. Die Einzelauswahl ruft in `selectionChanged` `mapPane.panToFile(...)` und verschiebt so die eingepasste Ansicht.

- [ ] **Step 1: Fehlschlagender Test `loadingShowsAllFilesOnTheMap`**

Der Test legt `a.jpg` mit 48.14 / 11.58 (München) und `b.jpg` mit 36.1 / -115.17 (Las Vegas) an, beide per ExifTool wie in `loadImages`. Dann lädt er den Ordner und wartet mit `UiTestSupport.waitUntilMapReady`.
- `js("myMap.getBounds().contains([48.14, 11.58]) && myMap.getBounds().contains([36.1, -115.17])")` ist `true`.
- `a.jpg` ist ausgewählt (die automatische Auswahl bleibt).

- [ ] **Step 2: Test laufen lassen, Fehler erwarten**

Run: `.\gradlew.bat uiTest --tests tf.geotagger.main.GeoTaggerMapUiTest.loadingShowsAllFilesOnTheMap`
Expected: FAIL (`false`), weil die Karte auf München zentriert ist.

- [ ] **Step 3: Implementieren**

Ein Feld `panToSelection` (Standard `true`) wird um das automatische `select(0)` herum auf `false` gesetzt. `selectionChanged` ruft `panToFile` nur auf, wenn es `true` ist. Klicks und Blättern zentrieren weiter.

- [ ] **Step 4: Test laufen lassen**

Run: wie Step 2 → PASS; außerdem `.\gradlew.bat build` → 133 Tests grün.

- [ ] **Step 5: Commit**

```powershell
& $git -C $app add src
& $git -C $app commit -m "Map keeps all files in view after loading - the automatic selection of the first file doesn't pan"
```

---

### Task 2: GeoHelper – Dienst-Grundlagen und Mapbox

**Files:**
- Modify: `$geo\build.gradle`: `implementation platform('com.fasterxml.jackson:jackson-bom:2.21.2')` und `implementation 'com.fasterxml.jackson.core:jackson-databind'`
- Create in `$geo\src\main\java\tf\geo\service\`: `Place.java`, `ReverseGeocoder.java`, `GeoServiceException.java`, `HttpTransport.java`, `JdkHttpTransport.java`, `MapboxReverseGeocoder.java`
- Test: `$geo\src\test\java\tf\geo\service\PlaceTest.java`, `MapboxReverseGeocoderTest.java`, `FakeTransport.java` (Testhelfer: liefert eine feste `Response` oder wirft `IOException`, merkt sich die URIs)

**Interfaces (Produces):**
- `public record Place(String countryCode, String country, String state, String city, String sublocation)`:
  - Leere oder nur aus Leerzeichen bestehende Teile werden `null`, Texte getrimmt, der Ländercode in Großbuchstaben (`Locale.ROOT`).
  - `boolean isEmpty()` ist true, wenn alle Teile `null` sind.
- `public interface ReverseGeocoder`:
  - `Optional<Place> reverse(LatLon position) throws GeoServiceException`: leer, wenn der Dienst nichts findet;
  - `Duration minRequestInterval()`;
  - `String name()`, z. B. `"Mapbox"`, für Meldungen.
- `public final class GeoServiceException extends Exception`:
  - `public enum Kind { RETRYABLE, QUOTA_EXCEEDED, CONFIGURATION, FAILED }`;
  - Konstruktoren `(Kind, String)` und `(Kind, String, Throwable)`;
  - `Kind kind()`.
- `public interface HttpTransport`:
  - `Response get(URI uri) throws IOException`;
  - `record Response(int status, String body)`.
- `public final class JdkHttpTransport implements HttpTransport`: `HttpClient` mit Connect-Timeout 10 s, Request-Timeout 30 s, `User-Agent: GeoHelper`, Body als UTF-8.
- `public final class MapboxReverseGeocoder implements ReverseGeocoder`:
  - Konstruktor `(String accessToken, String language, HttpTransport transport)`;
  - package-private `URI requestUri(LatLon position)`;
  - package-private `static Optional<Place> parse(String json)`;
  - `minRequestInterval()` = 100 ms (Mapbox erlaubt 1000 Anfragen/min);
  - `name()` = `"Mapbox"`.

**Mapbox Geocoding v6 (Reverse):**
- URL: `https://api.mapbox.com/search/geocode/v6/reverse?longitude=<lon>&latitude=<lat>&access_token=<token>` und, wenn die Sprache nicht leer ist, `&language=<lang>`.
- Zahlen per `BigDecimal.valueOf(x).toPlainString()` (nie `1.0E-4`); Token und Sprache URL-kodiert (UTF-8).
- Ohne `types`: Mapbox liefert dann das genaueste Merkmal zuerst, auch abseits von Adressen. GPXEditor nutzt `types=address` und findet in der Wildnis nichts.
- **Parsen** (Jackson `readTree`) von `features[0].properties`:
  - `countryCode` = `context.country.country_code`, `country` = `context.country.name`
  - `state` = `context.region.name`, `city` = `context.place.name`
  - `sublocation` = `context.locality.name`, sonst `context.neighborhood.name`
  - Ist `feature_type` selbst `country`, `region`, `place`, `locality` oder `neighborhood`, füllt `properties.name` diesen Teil, falls er noch leer ist.
  - Leeres `features` → `Optional.empty()`.
- **Status:**

  | Antwort | Ergebnis |
  |---|---|
  | 200 | parsen |
  | 401, 403 | `CONFIGURATION` („Mapbox access token rejected“) |
  | 429, 5xx, `IOException` | `RETRYABLE` |
  | sonst | `FAILED` mit Status im Text |

- [ ] **Step 1: Fehlschlagende Tests**

```java
// PlaceTest
@Test void blankPartsBecomeNullAndCodeIsUpperCase() {
    final Place place = new Place(" it ", "Italien", "", "Meran", "  ");
    assertEquals(new Place("IT", "Italien", null, "Meran", null), place);
    assertTrue(new Place(null, " ", null, null, "").isEmpty());
}

// MapboxReverseGeocoderTest - JSON-Muster als Textblöcke im Test, nachgebaut nach dem Beispiel der Mapbox-Doku
@Test void buildsTheReverseRequest()        // requestUri(new LatLon(46.67, 11.16)) mit Token "tok", Sprache "de" ->
    // https://api.mapbox.com/search/geocode/v6/reverse?longitude=11.16&latitude=46.67&access_token=tok&language=de
@Test void emptyLanguageSendsNoLanguage()   // Sprache "" -> URI ohne "language="
@Test void readsPlaceFromTheContext()       // address-Feature, context: country IT/"Italien", region "Trentino-Südtirol",
    // place "Meran", locality "Obermais", neighborhood "Maria Trost" -> Place("IT","Italien","Trentino-Südtirol","Meran","Obermais")
@Test void neighborhoodWhenThereIsNoLocality()  // ohne locality -> sublocation "Maria Trost"
@Test void placeFeatureIsTheCityItself()    // features[0]: feature_type "place", name "Moab", context region "Utah",
    // country US/"United States" -> Place("US","United States","Utah","Moab",null)
@Test void noFeaturesMeansNoPlace()         // {"type":"FeatureCollection","features":[]} -> Optional.empty()
@Test void httpProblemsBecomeKinds()        // FakeTransport: 401 -> CONFIGURATION, 429 -> RETRYABLE, 503 -> RETRYABLE,
    // 422 -> FAILED, IOException -> RETRYABLE
```

- [ ] **Step 2: Tests laufen lassen, Fehler erwarten**

Run: `.\gradlew.bat test --tests tf.geo.service.*` (in `$geo`)
Expected: FAIL, Kompilierfehler „package tf.geo.service does not exist“.

- [ ] **Step 3: Implementieren** (siehe Interfaces). `JdkHttpTransport` bekommt keinen eigenen Test; geprüft wird er im manuellen Test.

- [ ] **Step 4: Tests laufen lassen**

Run: `.\gradlew.bat build` (in `$geo`)
Expected: 36 Tests grün (bisher 28, dazu 1 `PlaceTest` und 7 Mapbox-Tests); veröffentlicht nach mavenLocal.

- [ ] **Step 5: Commit**

```powershell
& $git -C $geo add build.gradle src
& $git -C $geo commit -m "Services: reverse geocoding base (Place, ReverseGeocoder, HttpTransport) and Mapbox v6"
```

---

### Task 3: GeoHelper – GeoNames

**Files:**
- Create: `$geo\src\main\java\tf\geo\service\GeoNamesReverseGeocoder.java`
- Modify: `$geo\README.md` (Abschnitt „Services“: Interfaces, beide Dienste, was jeder liefert, `HttpTransport`)
- Test: `$geo\src\test\java\tf\geo\service\GeoNamesReverseGeocoderTest.java`

**Interfaces (Produces):**
- `public final class GeoNamesReverseGeocoder implements ReverseGeocoder`:
  - Konstruktor `(String username, String language, HttpTransport transport)`;
  - package-private `URI requestUri(LatLon)` und `static Optional<Place> parse(String json) throws GeoServiceException`;
  - `minRequestInterval()` = 250 ms;
  - `name()` = `"GeoNames"`.

**GeoNames `findNearbyPlaceName`:**
- URL: `https://secure.geonames.org/findNearbyPlaceNameJSON?lat=<lat>&lng=<lon>&style=FULL&maxRows=1&username=<user>`, bei nicht leerer Sprache dazu `&lang=<lang>`.
- **Parsen** von `geonames[0]`: `countryCode`, `countryName` → country, `adminName1` → state, `name` → city; sublocation bleibt `null`, weil GeoNames hier keinen Ortsteil liefert. Ein leeres `geonames` ergibt `Optional.empty()`.
- **Fehler:** GeoNames antwortet mit HTTP 200 und `{"status":{"message":"…","value":N}}`. Der Text der Ausnahme ist `"GeoNames: " + message`.

  | `value` | Bedeutung | Ergebnis |
  |---|---|---|
  | 15 | kein Ergebnis | `Optional.empty()` |
  | 18, 19, 20 | Tages-, Stunden-, Wochenlimit | `QUOTA_EXCEEDED` |
  | 10 | Benutzer unbekannt oder nicht für Webservices freigeschaltet | `CONFIGURATION` |
  | 13, 22 | Datenbank-Timeout, Server überlastet | `RETRYABLE` |
  | sonst | – | `FAILED` |

  HTTP-Status ungleich 200: 5xx → `RETRYABLE`, sonst `FAILED`. `IOException` → `RETRYABLE`.

- [ ] **Step 1: Fehlschlagende Tests**

```java
@Test void buildsTheRequest()          // (46.67, 11.16), "demo", "de" ->
    // https://secure.geonames.org/findNearbyPlaceNameJSON?lat=46.67&lng=11.16&style=FULL&maxRows=1&username=demo&lang=de
@Test void readsPlace()                // {"geonames":[{"name":"Meran","countryCode":"IT","countryName":"Italien",
    // "adminName1":"Trentino-Südtirol"}]} -> Place("IT","Italien","Trentino-Südtirol","Meran",null)
@Test void noResultIsEmpty()           // {"geonames":[]} und {"status":{"message":"no result","value":15}} -> Optional.empty()
@Test void limitsAreQuotaExceeded()    // value 18, 19 -> QUOTA_EXCEEDED, message enthält den Text von GeoNames
@Test void unknownUserIsConfiguration() // value 10 -> CONFIGURATION
@Test void overloadIsRetryable()       // value 22 -> RETRYABLE; HTTP 503 -> RETRYABLE
```

- [ ] **Step 2: Tests laufen lassen, Fehler erwarten**

Run: `.\gradlew.bat test --tests tf.geo.service.GeoNamesReverseGeocoderTest` (in `$geo`)
Expected: FAIL, Kompilierfehler „cannot find symbol: class GeoNamesReverseGeocoder“.

- [ ] **Step 3: Implementieren**, README ergänzen.

- [ ] **Step 4: Tests laufen lassen**

Run: `.\gradlew.bat build` (in `$geo`)
Expected: 42 Tests grün; veröffentlicht nach mavenLocal.

- [ ] **Step 5: Commit**

```powershell
& $git -C $geo add README.md src
& $git -C $geo commit -m "Services: GeoNames reverse geocoding (findNearbyPlaceName)"
```

---

### Task 4: GeoTagger – Raster, Cache, Drosselung und Wiederholung

**Files:**
- Create in `$app\src\main\java\tf\geotagger\enrich\`: `GridCell.java`, `Sleeper.java`, `PlaceRun.java`, `PlaceEnricher.java`
- Test: `$app\src\test\java\tf\geotagger\enrich\GridCellTest.java`, `PlaceEnricherTest.java`, `FakeGeocoder.java` (Testhelfer: liefert je Aufruf das nächste vorgegebene Ergebnis oder wirft die vorgegebene Ausnahme, zählt die Aufrufe)

**Interfaces (Produces):**
- `public record GridCell(long latitudeIndex, long longitudeIndex)`:
  - `static GridCell of(LatLon position)` mit `Math.round(value / 0.001)` je Achse;
  - `LatLon center()`. Abgefragt wird immer die Zellmitte, damit der Cache stimmt.
- `@FunctionalInterface public interface Sleeper { void sleep(Duration duration) throws InterruptedException; }`; in der App `Thread::sleep`.
- `public record PlaceRun(Map<MediaFile, Place> places, List<MediaFile> notFound, List<MediaFile> failed, String problem)`:
  - `notFound`: Der Dienst kennt an der Stelle keinen Ort.
  - `failed`: Fehler nach allen Wiederholungen, Kontingent erschöpft oder Konfiguration falsch.
  - `problem`: die erste Fehlermeldung, sonst `null`.
- `public final class PlaceEnricher`:
  - Konstruktor `(ReverseGeocoder geocoder, Map<GridCell, Optional<Place>> cache, Sleeper sleeper)`;
  - `PlaceRun run(List<MediaFile> files, BiConsumer<Integer, Integer> progress) throws InterruptedException`. `progress` bekommt (erledigte Zellen, Zellen gesamt); Dateien ohne Koordinaten werden übersprungen.

**Ablauf von `run`:**
1. Die Dateien werden nach `GridCell` gruppiert, in der Reihenfolge des ersten Vorkommens. Zellen im Cache werden nicht abgefragt.
2. Vor jeder Anfrage außer der ersten im Lauf wartet der Enricher `geocoder.minRequestInterval()`.
3. Nach den Ausnahmen:

   | Art | Verhalten |
   |---|---|
   | `RETRYABLE` | bis zu 3 Wiederholungen mit 1 s, 2 s, 4 s Wartezeit; danach ist die Zelle `failed` |
   | `QUOTA_EXCEEDED`, `CONFIGURATION` | Abbruch: diese und alle restlichen Zellen sind `failed` |
   | `FAILED` | nur diese Zelle `failed` |

4. Gefundene und nicht gefundene Zellen kommen in den Cache, fehlgeschlagene nicht; ein späterer Lauf versucht sie neu.

- [ ] **Step 1: Fehlschlagende Tests**

```java
// GridCellTest
@Test void nearbyPositionsShareACell()      // (46.67001, 11.16001) und (46.67004, 11.16003) -> gleiche Zelle; center() = (46.67, 11.16)
@Test void westAndSouthAreStable()          // (-33.8751, -151.2049) -> Indizes (-33875, -151205); (-0.0004, 0) und (0.0004, 0) -> Index 0

// PlaceEnricherTest - Dateien über new MediaFile(path, MediaType.IMAGE, snapshot mit Koordinaten), Interval 100 ms
@Test void filesInTheSameCellNeedOneRequest()   // 2 Dateien, 1 Zelle -> 1 Aufruf, beide in places
@Test void cachedCellsAreNotRequestedAgain()    // zweiter run mit demselben Cache -> keine weiteren Aufrufe
@Test void waitsBetweenRequests()               // 3 Zellen -> Sleeper bekam [100 ms, 100 ms]
@Test void retriesWithGrowingWaits()            // RETRYABLE, RETRYABLE, Ort -> gefunden; Sleeper enthält 1 s, 2 s
@Test void givesUpAfterThreeRetries()           // immer RETRYABLE -> 4 Aufrufe, Datei in failed, problem gesetzt
@Test void quotaStopsTheRun()                   // 3 Zellen: Ort, QUOTA_EXCEEDED -> 2 Aufrufe; 1. in places, 2. und 3. in failed,
    // problem = Text der Ausnahme
@Test void notFoundIsReportedAndCached()        // Optional.empty() -> notFound; zweiter run fragt nicht erneut
@Test void filesWithoutCoordinatesAreSkipped()  // nicht in places, notFound oder failed; kein Aufruf
```

- [ ] **Step 2: Tests laufen lassen, Fehler erwarten**

Run: `.\gradlew.bat test --tests tf.geotagger.enrich.*`
Expected: FAIL, Kompilierfehler „package tf.geotagger.enrich does not exist“.

- [ ] **Step 3: Implementieren** (siehe Interfaces und Ablauf).

- [ ] **Step 4: Tests laufen lassen**

Run: `.\gradlew.bat build`
Expected: 143 Tests grün (133 + 2 `GridCellTest` + 8 `PlaceEnricherTest`).

- [ ] **Step 5: Commit**

```powershell
& $git -C $app add src
& $git -C $app commit -m "Enrich: places per 100 m grid cell with cache, throttling and retries"
```

---

### Task 5: Preferences und Dienstauswahl

**Files:**
- Modify: `$app\src\main\java\tf\geotagger\main\GeoTaggerPreferences.java`, `PreferenceEditor.java`
- Create: `$app\src\main\java\tf\geotagger\enrich\PlaceService.java`, `$app\src\main\java\tf\geotagger\main\PlaceServiceFactory.java`
- Test: `$app\src\test\java\tf\geotagger\main\PlaceServiceFactoryTest.java`

**Interfaces (Produces):**
- **Preferences:**
  - `PLACE_SERVICE("placeService", "GEONAMES", String.class)`
  - `GEONAMES_USERNAME("geoNamesUsername", "", String.class)`
  - `MAPBOX_ACCESS_TOKEN("mapboxAccessToken", "", String.class)`
  - `PLACE_LANGUAGE("placeLanguage", "de", String.class)`
- `public enum PlaceService { GEONAMES("GeoNames"), MAPBOX("Mapbox"); String label(); }`
- `final class PlaceServiceFactory`:
  - `static ReverseGeocoder create() throws GeoServiceException`: baut den gewählten Dienst mit `JdkHttpTransport` und der Sprache (getrimmt).
    - Ohne Benutzer bzw. Token wirft er `CONFIGURATION` mit `"Please enter your GeoNames user name in the preferences"` bzw. `"... your Mapbox access token ..."`.
    - Ein unbekannter `PLACE_SERVICE`-Wert fällt auf GeoNames zurück.
  - `static String cacheKey()`: `<service>|<language>`.
- **`PreferenceEditor`**, neue Zeilen unter ExifTool, Muster wie die vorhandenen Zeilen:
  - „Place service:“ als `ChoiceBox<PlaceService>`, angezeigt mit `label()`;
  - „GeoNames user:“ und „Mapbox access token:“, je ein `TextField` mit 30 Spalten;
  - „Language of places:“, ein `TextField` mit 5 Spalten und dem Hinweis „e.g. de, en - empty: service default“.
  - „Save“ speichert die Werte getrimmt.
  - Der Dialog bekommt keinen UI-Test, er gehört zum manuellen Test.

- [ ] **Step 1: Fehlschlagende Tests** (nutzen den Test-Preferences-Knoten; `@BeforeEach` leert ihn)

```java
@Test void geoNamesNeedsAUser()                // GEONAMES, Benutzer "" -> CONFIGURATION, Text enthält "GeoNames user name"
@Test void mapboxNeedsAToken()                 // MAPBOX, Token "" -> CONFIGURATION
@Test void createsTheChosenService()           // GEONAMES/"demo" -> name() "GeoNames"; MAPBOX/"tok" -> name() "Mapbox"
@Test void cacheKeyDependsOnServiceAndLanguage() // GEONAMES|de, GEONAMES|en, MAPBOX|de unterscheiden sich
```

- [ ] **Step 2: Tests laufen lassen, Fehler erwarten**

Run: `.\gradlew.bat test --tests tf.geotagger.main.PlaceServiceFactoryTest`
Expected: FAIL, Kompilierfehler.

- [ ] **Step 3: Implementieren**, `PreferenceEditor` erweitern.

- [ ] **Step 4: Tests laufen lassen**

Run: `.\gradlew.bat build`
Expected: 147 Tests grün.

- [ ] **Step 5: Commit**

```powershell
& $git -C $app add src
& $git -C $app commit -m "Preferences: place service (GeoNames / Mapbox), user name, access token, language"
```

---

### Task 6: „Find place“ in der Werkzeugleiste

**Files:**
- Modify: `$app\src\main\resources\GeoTagger.fxml` (Button `findPlaceButton` „Find place“ nach „Edit...“, Tooltip „Country, state, city and sublocation from the coordinates - for the selected files, or for all listed files without place“), `GeoTagger.java`
- Create: `$app\src\main\java\tf\geotagger\enrich\PlaceSummary.java`
- Test: `$app\src\test\java\tf\geotagger\enrich\PlaceSummaryTest.java`, `$app\src\test\java\tf\geotagger\main\GeoTaggerPlaceUiTest.java` (neu; Setup wie `GeoTaggerEditUiTest`)

**Interfaces (Produces):**
- `public final class PlaceSummary`:
  - `static String text(PlaceRun run)`: z. B. `"Places found for 3 files, 1 without result, 2 failed"`. Teile mit 0 entfallen außer dem ersten; Einzahl „1 file“.
  - `static boolean hasProblems(PlaceRun run)`: true bei `failed` nicht leer.
- **Controller:**
  - `static List<MediaFile> placeScope(List<MediaFile> selected, List<MediaFile> listed)` (package-private): Mit Auswahl sind es die ausgewählten Dateien mit Koordinaten, sonst die gelisteten mit Koordinaten und ohne Ort.
  - `void setPlaceService(Callable<ReverseGeocoder> service)` (package-private, für UI-Tests); Standard ist `PlaceServiceFactory::create`.
  - Der Cache ist ein Feld `Map<String, Map<GridCell, Optional<Place>>>` mit `PlaceServiceFactory.cacheKey()` als Schlüssel. Er gilt für die Sitzung.
  - `@FXML void findPlaces()`:
    1. Bei `busy` oder ohne Ordner passiert nichts.
    2. Ist `placeScope(...)` leer, zeigt die Statusleiste `"No files to look up - they need coordinates (and no place, if nothing is selected)"`, sonst passiert nichts.
    3. Den Dienst holen. Bei `GeoServiceException` erscheint ein Alert (`ERROR`, Text der Ausnahme), sonst passiert nichts.
    4. `Task` über `runTask`: `PlaceEnricher` mit `Thread::sleep`; Nachricht `"Finding places: <done> / <total>"`, Fortschritt je Zelle.
    5. `onSucceeded`:
       - Jede Datei in `run.places()` bekommt alle fünf Ortsfelder (`COUNTRY_CODE`, `COUNTRY`, `STATE`, `CITY`, `SUBLOCATION`), fehlende Teile als `null`.
       - Daraus wird **eine** `ChangeFieldsAction("Find place", …)` über `perform`, nur wenn es etwas zu ändern gibt.
       - Danach zeigt die Statusleiste `PlaceSummary.text(run)`. Bei `hasProblems` kommt ein `WARNING`-Alert mit Zusammenfassung und `run.problem()`.
    6. `onFailed`: Statusleiste `"Finding places failed"` und `ERROR`-Alert.
  - `findPlaceButton` ist gesperrt, solange `busy` ist oder kein Ordner geladen ist (in `updateButtons`).

- [ ] **Step 1: Fehlschlagende Tests**

```java
// PlaceSummaryTest
@Test void countsFoundNotFoundAndFailed()   // 3/1/2 -> "Places found for 3 files, 1 without result, 2 failed"
@Test void singularAndZeroParts()           // 1/0/0 -> "Places found for 1 file"; hasProblems false

// GeoTaggerPlaceUiTest - a.jpg mit Koordinaten ohne Ort, b.jpg mit Koordinaten und City "Bozen", c.jpg ohne Koordinaten;
// controller.setPlaceService(() -> fake) mit Place("IT","Italien","Trentino-Südtirol","Meran",null), Interval 0
@Test void findPlaceIsDisabledWithoutFolder()   // direkt nach dem Start ist #findPlaceButton deaktiviert
@Test void findPlaceFillsListedFilesWithoutPlace() // Auswahl leeren, #findPlaceButton klicken, warten bis nicht busy:
    // a hat alle Teile (Sublocation null), b behält "Bozen", c unverändert; 1x #undoButton -> a wieder ohne Ort;
    // Statusleiste "Places found for 1 file"
@Test void findPlaceOverwritesSelectedFiles()   // b auswählen (City "Bozen", Sublocation per ExifTool "Altstadt"):
    // b -> City "Meran", Sublocation null (geleert); a bleibt ohne Ort
@Test void nothingToLookUpChangesNothing()      // nur c auswählen -> kein Aufruf des Fakes, Undo bleibt deaktiviert,
    // Statusleiste beginnt mit "No files to look up"
```

- [ ] **Step 2: Tests laufen lassen, Fehler erwarten**

Run: `.\gradlew.bat test --tests tf.geotagger.enrich.PlaceSummaryTest`, dann `.\gradlew.bat uiTest --tests tf.geotagger.main.GeoTaggerPlaceUiTest`
Expected: FAIL (Kompilierfehler, danach fehlt `#findPlaceButton`).

- [ ] **Step 3: Implementieren** (siehe Interfaces).

- [ ] **Step 4: Tests laufen lassen**

Run: `.\gradlew.bat build`, dann `.\gradlew.bat uiTest --tests tf.geotagger.main.GeoTaggerPlaceUiTest`
Expected: 149 Tests grün; 4 Place-UI-Tests grün.

- [ ] **Step 5: Commit**

```powershell
& $git -C $app add src
& $git -C $app commit -m "Find place: selected files, or all listed files without place - one undo step with summary"
```

---

### Task 7: Spec, README, gesamte UI-Suite, manuelle Prüfung

- [ ] **Step 1: Spec anpassen**
  - „Umfang / In Version 1“: Die Zeile „Zeitzone offline aus den Koordinaten“ ersetzen durch: „Zeitzone: aus der Datei, dem Sync-Dialog oder dem Bearbeiten-Dialog, nicht aus den Koordinaten (Entscheidung 15.11.2026: bei DSLR-Fotos stimmt die Zeitzone des Ortes nur, wenn die Kamerauhr auf Ortszeit stand).“
  - Felder-Tabelle, Quelle der Zeitzone: „Kamera-Zeitzone aus dem Sync-Dialog, manuell“.
  - GeoHelper-Tabelle, `tf.geo.service`:
    - `ReverseGeocoder` (GeoNames `findNearbyPlaceName`, Mapbox Geocoding v6) und `ElevationProvider` (Plan 5b);
    - `TimeZoneLookup` und offline-timezone streichen;
    - dazu `HttpTransport` erwähnen.
  - „Datenfluss 3. Anreichern“:
    - Raster 0,001°;
    - Drosselung GeoNames 250 ms, Mapbox 100 ms;
    - Wiederholung 1/2/4 s;
    - Kontingent oder Konfiguration brechen den Lauf ab;
    - die Zeitzonen-Zeile streichen.
  - „Hauptfenster“: Regel „Mit Auswahl überschreibt die Aktion“; ein gefundener Ort setzt alle fünf Felder.
  - „Umfang / Einstellungen“: Dienst, GeoNames-Benutzer, Mapbox-Token, Sprache der Orte (leer = Vorgabe des Dienstes).
- [ ] **Step 2: README** (GeoTagger): Status um „Find place (GeoNames or Mapbox)“ ergänzen; „Planned next“: Höhe (Plan 5b), Track-Sync-Dialog (Plan 6).
- [ ] **Step 3: Gesamte Suite**

Run: `.\gradlew.bat build uiTest installDist`
Expected: 149 Tests und 57 UI-Tests grün (52 + 1 aus Task 1 + 4 aus Task 6).

- [ ] **Step 4: Commit**

```powershell
& $git -C $app add README.md docs
& $git -C $app commit -m "Spec and README: find place, no time zone from the location"
```

- [ ] **Step 5: Manuell prüfen** (Nutzer; mit `USA_2024` und einem Ordner mit Fotos aus Südtirol)
  1. **Karte:** Nach dem Öffnen von `USA_2024` sind alle Cluster sichtbar, auch München; die erste Datei ist rot markiert.
  2. **Preferences:**
     - GeoNames-Benutzer eintragen, Sprache `de`.
     - „Find place“ ohne Auswahl füllt nur Dateien ohne Ort, mit deutschen Namen.
     - Ein Undo nimmt den ganzen Lauf zurück.
  3. **Mapbox:** Token eintragen, Dienst Mapbox, einige Dateien auswählen: Sie werden überschrieben, mit Sublocation, wo Mapbox eine liefert.
  4. **Sprache:** Mit Sprache leer bzw. `en` ändern sich die Namen bei erneutem Lauf (eigener Cache je Sprache).
  5. **Fehler:**
     - Falscher GeoNames-Benutzer → klare Meldung, nichts geändert.
     - Ohne Benutzer → Hinweis auf die Preferences.
  6. **Dauer:** ein ganzer Urlaubsordner, Laufzeit und Fortschritt plausibel; die Zusammenfassung stimmt.
  7. **Speichern:** Die Ortsfelder landen in IPTC und XMP (Bild-Info prüfen).

---

## Abschluss von Plan 5

- GeoHelper `gradlew build`: 42 Tests grün, veröffentlicht nach mavenLocal.
- GeoTagger `gradlew build`: 149 Tests grün; `gradlew uiTest`: 57 UI-Tests grün; keine Compilerwarnungen.
- Die Checkliste aus Task 7 ist abgehakt; der Push ist Sache des Nutzers.
- Danach Plan 5b: Höhe (SRTM mit `.hgt`-Dateien, openrouteservice, „Find altitude“).

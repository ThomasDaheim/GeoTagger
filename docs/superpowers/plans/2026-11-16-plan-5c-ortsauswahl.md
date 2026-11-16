# GeoTagger Plan 5c: Ortsauswahl bei mehreren Stadtteilen, Prüfen-Knopf – Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Liegen mehrere Stadtteile nahe am Foto, fragt „Find place“ wie GeoSetter in einem Auswahlfenster nach („Ortsdaten auswählen“). Außerdem prüft ein Knopf in den Preferences die Zugangsdaten des gewählten Dienstes.

**Architecture:**
- **GeoHelper:**
  - Neu ist `NearbyPlace(Place place, double distanceKm)`.
  - `ReverseGeocoder.nearby(LatLon)` liefert die Kandidaten; der erste Eintrag ist immer die beste Antwort, also das, was `reverse` liefert.
  - Die Standard-Implementierung im Interface baut auf `reverse` auf und gilt für Mapbox (ein Ergebnis, Entfernung 0).
  - `GeoNamesReverseGeocoder` liefert aus derselben Anfrage alle Orte und Stadtteile der 10 nächsten Treffer.
- **GeoTagger `enrich`:**
  - Der Cache hält je Rasterzelle die Kandidatenliste.
  - `PlaceRun` kennt zusätzlich die Dateien, bei denen mehr als ein Kandidat im Umkreis aus den Preferences liegt.
- **UI:**
  - Nach dem Lauf erscheint je Rasterzelle mit solchen Dateien ein Dialog `PlaceChoiceDialog` (Aufbau wie GeoSetter: Vorschau links, Liste „Region; Ort; Stadtteil (0,72 km)“, darunter editierbare Felder Region, Ort, Sublocation).
  - Er hat die Knöpfe „Use“, „Use for all“ und „Cancel“.
  - Alles zusammen bleibt **ein** Undo-Schritt.
- **Preferences:** neu sind „Ask between places within … km“ (0 = nie fragen) und ein Knopf „Check“ für den Ortsdienst.

**Tech Stack:** wie Plan 5.

**Spec:** [2026-11-03-geotagger-design.md](../specs/2026-11-03-geotagger-design.md), „Datenfluss 3. Anreichern“. Task 6 ergänzt die Spec. Vorgänger: [Plan 5](2026-11-15-plan-5-ort-ermitteln.md) samt Rückmeldungen vom 16.11.2026.

## Global Constraints

- Alle Global Constraints von Plan 5 gelten weiter, unter anderem:
  - die UI-Test-Strategie: gezielt per `--tests`, die gesamte Suite vor und nach dem Abschluss-Review; nie `build` zusammen mit `--tests`;
  - kein Netz in automatischen Tests;
  - `$git` wird dynamisch ermittelt.
- **Entscheidungen des Nutzers (16.11.2026):**
  - Der **Umkreis** für „mehrere in der Nähe“ ist eine Einstellung, `PLACE_CHOICE_RADIUS_KM`, Standard `1.0` km. Bei `0` wird nie gefragt, dann gilt wie bisher die beste Antwort.
  - **Die Liste** zeigt Ort/Stadtteil mit Entfernung, dazu je Ort einen Eintrag ohne Stadtteil.
  - **„Use for all“** gibt es nur, wenn im Lauf mehrere Rasterzellen zu fragen sind (wie GeoSetter bei mehreren ausgewählten Bildern). Es gilt für den Rest des Laufs, ohne weitere Dialoge.
  - Der **Prüfen-Knopf** gilt für beide Dienste.
- **Wie GeoSetter:**
  - Die Felder unter der Liste sind editierbar, der Nutzer kann den Stadtteil also auch selbst eintippen.
  - **„Cancel“** lässt die Dateien dieser Zelle unverändert, wie „Abbrechen“ in GeoSetter.
- **Anders als GeoSetter:** In der Liste stehen nur Orte (`PPL*`) und Stadtteile (`PPLX`), keine Firmen oder Gebäude. GeoSetter zeigt dort z. B. „E.ON Bayern AG“.
- **„Use for all“ im Detail:** Spätere Zellen bekommen Region, Ort und Sublocation der Auswahl nur, wenn der gewählte Ort unter **ihren** Kandidaten vorkommt; sonst ihre beste Antwort. Ein in München gewählter Stadtteil landet so nie auf Bildern aus Rom.

## Review Focus

1. **Die Auswahl ändert nichts an der Logik ohne Dialog:** Mit Umkreis 0, bei Mapbox und bei nur einem Kandidaten im Umkreis ist das Ergebnis genau wie in Plan 5 (Rückmeldung vom 16.11.). → `PlaceEnricherTest.oneCandidateNeedsNoChoice`, `GeoTaggerPlaceUiTest.radiusZeroNeverAsks` (Task 2/4)
2. **„Use for all“ über verschiedene Orte hinweg:** Eine Zelle, deren Kandidaten den gewählten Ort nicht enthalten, bekommt ihre eigene beste Antwort. → `PlaceChoicesTest.useForAllOnlyWhereThePlaceIsNearby` (Task 3)
3. **„Cancel“ mitten in mehreren Dialogen:** Nur diese Zelle bleibt unverändert, die anderen werden übernommen; alles bleibt ein Undo-Schritt. → `GeoTaggerPlaceUiTest.cancelKeepsTheseFiles` (Task 4)
4. **Prüfen ohne Netz oder mit falschen Daten:** Das Fenster friert nicht ein, die Meldung des Dienstes erscheint. → manuell (Task 6), Dienst-Fehlerarten bereits getestet
5. **Editierte Felder:** Ein eingetippter Stadtteil wird übernommen, auch wenn er nicht in der Liste steht; leere Felder löschen den Wert. → `GeoTaggerPlaceUiTest.typedSublocationIsUsed` (Task 4)

---

### Task 1: GeoHelper – Kandidaten mit Entfernung

**Files:**
- Create: `$geo\src\main\java\tf\geo\service\NearbyPlace.java`
- Modify: `ReverseGeocoder.java` (Default-Methode `nearby`), `GeoNamesReverseGeocoder.java`, `README.md`
- Test: `GeoNamesReverseGeocoderTest.java`, `$geo\src\test\java\tf\geo\service\ReverseGeocoderTest.java`

**Interfaces (Produces):**
- `public record NearbyPlace(Place place, double distanceKm)`
- `ReverseGeocoder`: `default List<NearbyPlace> nearby(LatLon position) throws GeoServiceException`. Der Standard ist `reverse(position)` als ein Eintrag mit Entfernung 0, ohne Ort eine leere Liste.
- `GeoNamesReverseGeocoder.nearby`, aus derselben Anfrage wie `reverse` (eine Anfrage, keine doppelten Credits):
  1. Der erste Eintrag ist das Ergebnis von `reverse`, also nächster Stadtteil plus nächster Ort. Seine Entfernung ist die des Stadtteils bzw. des Orts.
  2. Danach kommt jeder weitere Treffer nach Entfernung: ein Stadtteil (`PPLX`) als Place mit dem nächsten Ort, ein Ort (`PPL*`) als Place ohne Sublocation.
  3. Doppelte Einträge (gleicher Ort und gleicher Stadtteil) entfallen.
  - Die Entfernung kommt aus dem Feld `distance` (Text in km).
  - `reverse` liefert weiter den ersten Eintrag.

- [ ] **Step 1: Fehlschlagende Tests**
  - `ReverseGeocoderTest.defaultNearbyIsTheSingleAnswer`: ein Test-Geocoder mit `reverse` → `Place X` ergibt `[NearbyPlace(X, 0)]`; ohne Ort `[]`.
  - `GeoNamesReverseGeocoderTest.nearbyListsDistrictsAndCitiesByDistance`: die Barcelona-Antwort aus Plan 5 mit `distance` (Eixample 0.65, el Raval 0.74, Barcelona 0.94), dazu `L'Hospitalet` (PPLA3, 3.1). Erwartet:
    1. Barcelona/Eixample 0.65
    2. Barcelona/el Raval 0.74
    3. Barcelona ohne Sublocation 0.94
    4. L'Hospitalet ohne Sublocation 3.1
  - `GeoNamesReverseGeocoderTest.reverseIsTheFirstOfNearby`: `reverse` liefert `nearby().get(0).place()`.
- [ ] **Step 2: Fehlschlag prüfen:** `.\gradlew.bat test --tests tf.geo.service.*` (in `$geo`) → Kompilierfehler.
- [ ] **Step 3: Implementieren**; README: `nearby` erklären.
- [ ] **Step 4:** `.\gradlew.bat build` (in `$geo`) → 48 Tests grün, veröffentlicht.
- [ ] **Step 5: Commit** `"Services: nearby places with distance (GeoNames: districts and cities of one request)"`

---

### Task 2: GeoTagger – Kandidaten im Lauf

**Files:**
- Modify: `PlaceEnricher.java`, `PlaceRun.java`, `GeoTagger.java` (Cache-Typ)
- Test: `PlaceEnricherTest.java`, `FakeGeocoder.java` (kann zusätzlich Kandidatenlisten liefern)

**Interfaces (Produces):**
- Cache: `Map<GridCell, List<NearbyPlace>>`; eine leere Liste bedeutet „nichts gefunden“.
- `PlaceEnricher`: Konstruktor bekommt zusätzlich `double choiceRadiusKm`.
- `PlaceRun`: zusätzlich `Map<MediaFile, List<NearbyPlace>> choices`, die Dateien mit **mehr als einem** Kandidaten innerhalb `choiceRadiusKm`, mit genau diesen Kandidaten. Diese Dateien stehen weiter auch in `places`, mit der besten Antwort.

- [ ] **Step 1: Fehlschlagende Tests**
  - `severalCandidatesNearbyNeedAChoice`: Kandidaten 0.65 / 0.74 / 0.94 / 3.1 bei Radius 1.0 → `choices` hat die Datei mit den ersten drei.
  - `oneCandidateNeedsNoChoice`: nur ein Kandidat im Radius → `choices` ist leer, `places` wie bisher.
  - `radiusZeroNeverAsks`: Radius 0 → `choices` ist leer.
  - Die bisherigen Tests bleiben; `FakeGeocoder` liefert für einen `Place` eine einelementige Liste.
- [ ] **Step 2–4:** `.\gradlew.bat test --tests tf.geotagger.enrich.*` rot → implementieren → `.\gradlew.bat build` → 158 Tests grün.
- [ ] **Step 5: Commit** `"Enrich: candidates per cell, files with several places nearby"`

---

### Task 3: Auswahl übernehmen – Logik

**Files:**
- Create: `$app\src\main\java\tf\geotagger\enrich\PlaceChoices.java`
- Test: `$app\src\test\java\tf\geotagger\enrich\PlaceChoicesTest.java`

**Interfaces (Produces):**
- `public final class PlaceChoices`:
  - `static List<List<MediaFile>> groups(Map<MediaFile, List<NearbyPlace>> choices)`: Dateien mit gleicher Kandidatenliste bilden eine Gruppe, in der Reihenfolge des Laufs.
  - `static Place forOtherGroup(Place chosen, List<NearbyPlace> candidates)`: Steht der gewählte Ort (gleiche `city`) unter den Kandidaten, gilt die Auswahl (Region, Ort, Sublocation wie gewählt, Land und Ländercode aus dem Kandidaten), sonst der erste Kandidat.

- [ ] **Step 1: Fehlschlagende Tests:** `groupsKeepTheOrderOfTheRun`, `useForAllOnlyWhereThePlaceIsNearby` (Barcelona/Eixample gewählt → eine Gruppe mit Barcelona-Kandidaten bekommt Eixample, eine Gruppe mit Rom-Kandidaten ihren ersten Kandidaten).
- [ ] **Step 2–4:** rot → implementieren → `.\gradlew.bat build` → 160 Tests grün.
- [ ] **Step 5: Commit** `"Enrich: choosing among nearby places, use for all"`

---

### Task 4: Dialog „Choose place“

**Files:**
- Create: `$app\src\main\java\tf\geotagger\main\PlaceChoiceDialog.java`
- Modify: `GeoTagger.java` (`findPlaces` → `onSucceeded`), `GeoTaggerPreferences.java` (`PLACE_CHOICE_RADIUS_KM("placeChoiceRadiusKm", "1.0", Double.class)`)
- Test: `GeoTaggerPlaceUiTest.java`

**Interfaces (Produces):**
- `final class PlaceChoiceDialog`:
  - `static Optional<Choice> show(Window owner, List<MediaFile> files, List<NearbyPlace> candidates, Image preview, boolean offerForAll)`
  - `record Choice(Place place, boolean forAll)`
  - Leeres `Optional` bei „Cancel“ / Esc.
- **Aufbau** (modal, Stylesheet wie `EditDialog`):
  - Titel `"Choose place - " + Dateiname` (bei mehreren Dateien `+ " (+n)"`).
  - Links die Vorschau der ersten Datei (bis 320 px; `null` → leer).
  - Rechts Ländercode und Land (nur lesbar), die Liste `#placeChoiceList` mit Einträgen `"Region; Ort; Stadtteil (0,72 km)"` bzw. `"Region; Ort (0,94 km)"` (Komma als Dezimaltrenner wie GeoSetter), vorausgewählt der erste Eintrag.
  - Darunter `#placeChoiceState`, `#placeChoiceCity`, `#placeChoiceSublocation` als `TextField`; sie werden beim Wechsel der Auswahl gefüllt und sind editierbar.
  - Knöpfe `#placeChoiceUse` („Use“, Default), `#placeChoiceUseForAll` („Use for all“, nur bei `offerForAll`), `#placeChoiceCancel` („Cancel“).
- **Controller `onSucceeded`:**
  1. Für jede Gruppe aus `PlaceChoices.groups(run.choices())` zeigt er den Dialog, mit `offerForAll` = es folgen weitere Gruppen. Die Vorschau holt er über den vorhandenen `PreviewProvider`, synchron im Speicher-Cache oder `null`.
  2. Nach „Use for all“ bekommen alle folgenden Gruppen `PlaceChoices.forOtherGroup(...)` ohne Dialog.
  3. Bei „Cancel“ fällt die Gruppe aus `places` heraus.
  4. Danach geht es wie bisher weiter: `PlaceValues` → **eine** `ChangeFieldsAction`.

- [ ] **Step 1: Fehlschlagende Tests** (Fake liefert für a.jpg die Barcelona-Kandidaten; Radius aus den Preferences)
  - `severalDistrictsAskForAChoice`: Find place → Dialog; den 2. Eintrag wählen, „Use“ → a: City Barcelona, Sublocation el Raval.
  - `typedSublocationIsUsed`: im Dialog Sublocation „Sant Antoni“ eintippen → übernommen.
  - `cancelKeepsTheseFiles`: a und b in verschiedenen Zellen, beide mehrdeutig. Beim ersten Dialog „Cancel“, beim zweiten „Use“ → a unverändert, b gesetzt, ein Undo nimmt b zurück.
  - `radiusZeroNeverAsks`: `PLACE_CHOICE_RADIUS_KM` = 0 → kein Dialog, a bekommt Barcelona/Eixample.
- [ ] **Step 2: Fehlschlag prüfen:** `.\gradlew.bat uiTest --tests tf.geotagger.main.GeoTaggerPlaceUiTest` → rot (kein Dialog).
- [ ] **Step 3–4:** implementieren → `.\gradlew.bat build`, dann dieselbe UI-Klasse → grün.
- [ ] **Step 5: Commit** `"Find place: choose among several districts nearby, like GeoSetter"`

---

### Task 5: Preferences – Umkreis und „Check“

**Files:**
- Modify: `PreferenceEditor.java`, `PlaceServiceFactory.java`
- Test: `PlaceServiceFactoryTest.java`

**Interfaces (Produces):**
- `PlaceServiceFactory.create(PlaceService service, String user, String token, String language)` (package-private). Das bisherige `create()` nutzt diese Methode mit den gespeicherten Werten.
- **`PreferenceEditor`:**
  - Neue Zeile „Ask between places within:“ mit einem `TextField` (5 Spalten) plus „km (0: never ask)“.
  - Ungültige Zahlen werden beim Speichern zu 1.0.
  - Knopf „Check“ hinter dem Ortsdienst:
    - Er baut den Dienst aus den **aktuellen Feldern** (noch ungespeichert) und fragt in einem Hintergrund-Thread `reverse(48.137, 11.575)` (München) ab.
    - Das Label daneben zeigt `"OK - München, Bayern"`, `"OK"` (kein Ort) oder die Fehlermeldung.
    - Solange die Abfrage läuft, ist der Knopf gesperrt.

- [ ] **Step 1: Fehlschlagende Tests:** `createsFromGivenValues` (MAPBOX/""/"tok"/"en" → `name()` „Mapbox“; GEONAMES ohne Benutzer → CONFIGURATION).
- [ ] **Step 2–4:** rot → implementieren → `.\gradlew.bat build` → 161 Tests grün. Der Dialog wird manuell geprüft (Task 6).
- [ ] **Step 5: Commit** `"Preferences: radius for the choice of places, check of the place service"`

---

### Task 6: Spec, README, Suite, manuelle Prüfung

- [ ] **Step 1: Spec** „Datenfluss 3. Anreichern“ ergänzen:
  - Auswahlfenster bei mehreren Kandidaten im Umkreis;
  - „Use for all“-Regel; „Cancel“ lässt die Dateien unverändert;
  - nur Orte und Stadtteile, keine Firmen.
  - Unter „Einstellungen“: Umkreis und „Check“.
- [ ] **Step 2: README:** Auswahl und „Check“ ergänzen.
- [ ] **Step 3:** `.\gradlew.bat build uiTest` → 161 Tests und 65 UI-Tests grün (Hände weg von Maus und Tastatur).
- [ ] **Step 4: Commit** `"Spec and README: choice of places, check of the place service"`
- [ ] **Step 5: Manuell prüfen** (Nutzer):
  1. Barcelona-Bild mit GeoNames → Auswahlfenster mit Stadtteilen und Entfernungen. Einen Eintrag wählen bzw. einen eigenen Stadtteil eintippen.
  2. Mehrere Bilder aus verschiedenen Stadtteilen und Städten auswählen → „Use for all“ erscheint. Bilder einer anderen Stadt bekommen ihren eigenen Ort.
  3. „Cancel“ → diese Bilder bleiben unverändert.
  4. Umkreis 0 → kein Fenster. Mapbox → kein Fenster.
  5. „Check“ mit gültigem und falschem GeoNames-Benutzer bzw. Mapbox-Token, auch offline.

---

## Abschluss von Plan 5c

- GeoHelper 48 Tests, GeoTagger 161 Tests und 65 UI-Tests grün, keine Compilerwarnungen.
- Abschluss-Review durch einen frischen Reviewer; Push durch den Nutzer.

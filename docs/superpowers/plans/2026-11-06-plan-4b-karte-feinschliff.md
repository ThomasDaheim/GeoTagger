# GeoTagger Plan 4b: Feinschliff Karte, Tracks, Start – Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Die Wünsche aus der manuellen Prüfung von Plan 4 umsetzen:
- weniger Bündelung auf der Karte;
- Track-Farben aus der GPX-Datei;
- Rahmenauswahl mit Strg+Ziehen;
- Icons für Undo und Redo;
- ein schnellerer Start;
- eine Dateiliste, die sich schon während des Lesens füllt.

**Architecture:**
- **GeoHelper** bekommt:
  - einstellbares Clustering (`MapConfig`);
  - Tracks aus mehreren Linien mit je eigener Farbe (`TrackLine`);
  - die Rahmenauswahl als Bibliotheksfunktion: Strg+Ziehen meldet ein Rechteck über `MapListener.areaSelected`, wie in GPXEditors `TrackMap`.
- **GeoTagger:**
  - `GpxReader` liest Tracks und Segmente mit Farbe statt einer flachen Punktliste. Damit verschwinden auch die geraden Verbindungslinien zwischen Segmenten.
  - Der Controller baut die Karte erst nach dem Anzeigen des Fensters auf.
  - Die Dateiliste wird blockweise während des Lesens gefüllt.
  - Startskripte (`installDist`) erlauben den Start ohne Gradle.

**Tech Stack:** wie Plan 4 (Java 25, JavaFX 25.0.1 inkl. web, Leaflet 1.8.0, markercluster 1.4.1, TestFX 4.0.18).

**Spec:** [2026-11-03-geotagger-design.md](../specs/2026-11-03-geotagger-design.md), Abschnitte „Umfang“ (Leaflet-Karte, Track-Liste), „Datenfluss“ (Ordner öffnen: „Die Liste füllt sich schrittweise“). Vorgänger: [Plan 4](2026-11-05-plan-4-karte.md).

## Global Constraints

- Alles aus den Global Constraints von Plan 4 gilt weiter: Englisch, `master`, Lizenz-Header, vollständiges Javadoc, keine Compilerwarnungen, GeoHelper bleibt 0.1, Leaflet 1.8.0, `$git`/`$app`/`$geo`/`$gpx` wie dort.
- **UI-Tests:**
  - kein Drag & Drop über den Roboter; das kann minutenlang in der Windows-Schleife hängen;
  - Mausziehen ohne DnD (Rahmen) geht über `robot.press(...).moveTo(...)`.
- **Werte aus der Prüfung des Nutzers:**
  - Clustering: Radius **40 px** statt 80, ab Zoom **15** gar nicht mehr. Eine echte Mindestzahl (8–10) kann markercluster nicht.
  - Rahmenauswahl: **Strg+Ziehen** wählt die Dateien im Rechteck, **Strg+Umschalt+Ziehen** ergänzt die Auswahl. Das Rechteck wird gezeichnet wie in GPXEditor (`TrackMap.java:691–764`).
  - Undo/Redo: Icons `undo.png` / `redo.png` aus `$gpx\src\main\resources` (16×16), Tooltip „Undo (Ctrl+Z)“ / „Redo (Ctrl+Y)“, kein Text.
  - Track-Farbe in dieser Reihenfolge:
    1. `gpx_style` `<line><color>` als Hex (`FF0000` oder `#FF0000`);
    2. Garmin `<gpxx:DisplayColor>`, Namen wie in GPXEditors `GarminColor`; `DarkYellow` wird zu `goldenrod`, `Transparent` wird ignoriert;
    3. sonst die bisherige Farbreihe.

## Review Focus

1. **Startpfad:** Der Ordner lädt, bevor die Karte (jetzt später angelegt) existiert. Marker und Tracks dürfen nicht verloren gehen. → `GeoTaggerMapUiTest.markersShownForFilesWithCoordinates` mit Ordnerladen direkt nach `@Start` (Task 5)
2. **GPX mit mehreren `trk`/`trkseg`, mit und ohne Farbe, mit ungültiger Farbe** (`#12`, `Purple`) → `GpxReaderTest.readsTracksWithColors` (Task 2)
3. **Strg+Klick ohne Ziehen, Rechteck ohne Dateien:** wählt nichts ab bzw. leert die Auswahl nur ohne Umschalt. Ein Klick ohne Strg verschiebt weiter die Karte. → `LeafletMapViewTest.ctrlClickWithoutDragSelectsNoArea` (Task 1)
4. **Ordner wird während des Lesens gewechselt oder das Lesen schlägt mittendrin fehl:** Es entsteht keine gemischte Liste. → `GeoTaggerUiTest.loadingTwiceKeepsOnlyTheSecondFolder` (Task 6)
5. **Undo/Redo-Buttons ohne Text:** IDs, Deaktivierung und Strg+Z/Y funktionieren weiter. → bestehende Tests `undoAndRedo`, `undoAndRedoWithKeysOutsideTextFields` (Task 4)

---

### Task 1: GeoHelper – Clustering einstellbar, Tracks mit Farben, Rahmenauswahl

**Files:**
- Modify: `$geo\src\main\java\tf\geo\map\MapConfig.java`, `LeafletMapView.java`, `MapListener.java`, `MapCallback.java`
- Create: `$geo\src\main\java\tf\geo\map\TrackLine.java`
- Modify: `$geo\src\main\resources\tf\geo\map\leaflet\GeoMap.js`
- Test: `LeafletMapViewTest.java`

**Interfaces:**
- Produces:
  - `MapConfig(List<MapLayer> baseLayers, LatLon initialCenter, int initialZoom, int clusterRadius, int noClusteringFromZoom)`, `defaults()` with `40` and `15`
  - `public record TrackLine(List<LatLon> points, String color)`
  - `LeafletMapView.showTrack(String id, List<TrackLine> lines)`: replaces the old signature. All lines of one id are shown / hidden / removed together (`L.featureGroup`).
  - `MapListener.areaSelected(LatLon southWest, LatLon northEast, boolean shiftDown)` (default: nothing)
  - JS: `geoClusterOptions` is set by `LeafletMapView` before `GeoMap.js` runs; `geoTracks[id]` is a `featureGroup`; `geoTrackCount()` / `geoVisibleTrackCount()` count ids as before.

- [ ] **Step 1: Fehlschlagende Tests**

```java
@Test
void clusterOptionsComeFromTheConfig() {
    final LeafletMapView map = FxTestSupport.readyMap();
    assertEquals(40, number(js(map, "geoCluster.options.maxClusterRadius")));
    assertEquals(15, number(js(map, "geoCluster.options.disableClusteringAtZoom")));
}

@Test
void trackWithSeveralColouredLines() {
    final LeafletMapView map = FxTestSupport.readyMap();
    FxTestSupport.onFx(() -> {
        map.showTrack("t", List.of(new TrackLine(List.of(new LatLon(46, 11), new LatLon(46.1, 11.1)), "#ff0000"),
                new TrackLine(List.of(new LatLon(47, 12), new LatLon(47.1, 12.1)), "darkblue")));
        return null;
    });
    assertEquals(1, number(js(map, "geoTrackCount()")));
    assertEquals(2, number(js(map, "geoTracks['t'].getLayers().length")));
    assertEquals("darkblue", js(map, "geoTracks['t'].getLayers()[1].options.color"));
}

@Test
void ctrlDragSelectsAnArea() {
    final LeafletMapView map = FxTestSupport.readyMap();
    final List<String> events = new ArrayList<>();
    FxTestSupport.onFx(() -> {
        map.setMapListener(new MapListener() {
            @Override
            public void areaSelected(LatLon southWest, LatLon northEast, boolean shiftDown) {
                events.add(southWest.latitude() < northEast.latitude() && southWest.longitude() < northEast.longitude()
                        ? "area " + shiftDown : "wrong corners");
            }
        });
        final WebView web = map.getWebView();
        web.fireEvent(mouse(MouseEvent.MOUSE_PRESSED, 300, 200, true, false));
        web.fireEvent(mouse(MouseEvent.MOUSE_DRAGGED, 100, 400, true, false));
        web.fireEvent(mouse(MouseEvent.MOUSE_RELEASED, 100, 400, true, false));
        return null;
    });
    assertEquals(List.of("area false"), events);
}

@Test
void ctrlClickWithoutDragSelectsNoArea() {
    // as above, but PRESSED and RELEASED at the same point -> events stays empty
}
```

`mouse(type, x, y, ctrl, shift)` baut ein `MouseEvent` mit `MouseButton.PRIMARY`, `primaryButtonDown = true` und Szenen- bzw. Bildschirmkoordinaten gleich x/y. Dafür gibt es eine private Hilfsmethode im Test.

- [ ] **Step 2: Tests laufen lassen, Fehler erwarten**

Run (in `$geo`): `.\gradlew.bat test --tests tf.geo.map.LeafletMapViewTest`
Expected: FAIL, Kompilierfehler „cannot find symbol: class TrackLine“.

- [ ] **Step 3: Implementieren**

- **`setUpMap`** setzt vor `GeoMap.js` die Variable `var geoClusterOptions = {maxClusterRadius: r, disableClusteringAtZoom: z, spiderfyOnMaxZoom: true, chunkedLoading: false};`. `GeoMap.js` nutzt sie für `L.markerClusterGroup(geoClusterOptions)`.
- **`geoShowTrack(id, lines)`** mit `lines = [{coords: [[lat,lng],…], color: "…"}]`: Es entsteht eine `L.featureGroup` aus Polylines (weight 3, opacity 0.8). `geoSetTrackVisible` und `geoRemoveTrack` arbeiten auf der Gruppe.
- **Rahmenauswahl** in `LeafletMapView`, übernommen aus GPXEditor `TrackMap.handleMouseCntrl*` (Zeilen 691–764):
  - `addEventFilter` für `MOUSE_PRESSED`, `MOUSE_DRAGGED` und `MOUSE_RELEASED` auf der WebView, nur mit gedrückter Strg-Taste, und dann mit `consume()`, damit die Karte nicht mitzieht;
  - ein `javafx.scene.shape.Rectangle` mit der Style-Klasse `selectRect` (halbtransparent blau, gestrichelter Rand) im StackPane, `managed = false`;
  - beim Loslassen: Ecken über `latLonAt` umrechnen, Minimum und Maximum bilden, `areaSelected` melden;
  - unter 3 px Ausdehnung wird nichts gemeldet;
  - dazu `disableCntrlDrag` / `enableCntrlDrag` aus `$gpx\src\main\resources\leaflet\MapHelper.js:41–61` in `GeoMap.js`, damit Leaflet bei gedrückter Strg-Taste weder zieht noch per Box zoomt.
- **`MapCallback`** braucht dafür nichts; die Meldung kommt aus Java.

- [ ] **Step 4: Tests laufen lassen**

Run (in `$geo`): `.\gradlew.bat build`
Expected: BUILD SUCCESSFUL, 28 Tests grün, veröffentlicht in mavenLocal.

- [ ] **Step 5: Commit**

```powershell
& $git -C $geo add src
& $git -C $geo commit -m "Map: cluster options, tracks with coloured lines, area selection with ctrl + drag"
```

---

### Task 2: GPX – Tracks und Segmente mit Farbe lesen

**Files:**
- Modify: `$app\src\main\java\tf\geotagger\track\GpxReader.java`
- Create: `$app\src\main\java\tf\geotagger\track\GpxLine.java`
- Test: `$app\src\test\java\tf\geotagger\track\GpxReaderTest.java`

**Interfaces:**
- Produces:
  - `public record GpxLine(List<TrackPoint> points, String color)`: one `trkseg`. `color` is a CSS colour of its `trk` or null.
  - `static List<GpxLine> GpxReader.readLines(Path gpx) throws IOException`: segments without points are left out.
  - `GpxReader.read(Path)` stays: the flat list for the sync, now built from `readLines`.
  - `static String GpxReader.cssColor(String gpxStyleColor, String garminColor)`: null if neither is usable.

- [ ] **Step 1: Fehlschlagende Tests**

```java
@Test
void readsTracksWithColors() throws IOException {
    final Path gpx = Files.writeString(dir.resolve("colors.gpx"), """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1" creator="test" xmlns="http://www.topografix.com/GPX/1/1"
                 xmlns:gpxx="http://www.garmin.com/xmlschemas/GpxExtensions/v3">
              <trk><extensions><line xmlns="http://www.topografix.com/GPX/gpx_style/0/2"><color>00FF00</color></line></extensions>
                <trkseg><trkpt lat="46.0" lon="11.0"/><trkpt lat="46.1" lon="11.1"/></trkseg>
                <trkseg><trkpt lat="46.2" lon="11.2"/></trkseg>
              </trk>
              <trk><extensions><gpxx:TrackExtension><gpxx:DisplayColor>DarkYellow</gpxx:DisplayColor></gpxx:TrackExtension></extensions>
                <trkseg><trkpt lat="47.0" lon="12.0"/></trkseg>
              </trk>
              <trk><trkseg><trkpt lat="48.0" lon="13.0"/></trkseg><trkseg/></trk>
            </gpx>
            """);
    final List<GpxLine> lines = GpxReader.readLines(gpx);
    assertEquals(List.of("#00FF00", "#00FF00", "goldenrod"), lines.subList(0, 3).stream().map(GpxLine::color).toList());
    assertNull(lines.get(3).color());
    assertEquals(4, lines.size());
    assertEquals(5, GpxReader.read(gpx).size());
}

@Test
void unusableColorsAreIgnored() {
    assertNull(GpxReader.cssColor("#12", null));
    assertNull(GpxReader.cssColor(null, "Purple"));
    assertNull(GpxReader.cssColor(null, "Transparent"));
    assertEquals("#ff0000", GpxReader.cssColor("#ff0000", "Blue"));
    assertEquals("darkred", GpxReader.cssColor(null, "DarkRed"));
}
```

- [ ] **Step 2: Tests laufen lassen, Fehler erwarten**

Run: `.\gradlew.bat test --tests tf.geotagger.track.GpxReaderTest`
Expected: FAIL, Kompilierfehler „cannot find symbol: method readLines“.

- [ ] **Step 3: Implementieren**

- **Jackson-Modell erweitern:**
  - `Trk` bekommt `@JacksonXmlProperty(localName = "extensions") TrkExtensions extensions`.
  - `TrkExtensions` hat `line` (mit `color`) und `TrackExtension` (mit `DisplayColor`).
  - Namespaces werden ignoriert, wie bisher mit `@JsonIgnoreProperties(ignoreUnknown = true)`.
- **`cssColor`:**
  - Hex mit oder ohne `#` und genau 6 Hex-Ziffern wird zu `#` + Hex.
  - Garmin-Namen gelten wie in GPXEditor `GarminColor.java:18–34`, kleingeschrieben, `DarkYellow` als `goldenrod`.
  - Alles andere ergibt null.

- [ ] **Step 4: Tests laufen lassen**

Run: `.\gradlew.bat build`
Expected: BUILD SUCCESSFUL, 125 Tests grün.

- [ ] **Step 5: Commit**

```powershell
& $git -C $app add src
& $git -C $app commit -m "GPX: tracks and segments with colours from gpx_style or Garmin"
```

---

### Task 3: Karte in GeoTagger – Farben, Segmente, Rahmenauswahl

**Files:**
- Modify: `$app\src\main\java\tf\geotagger\track\GpxTrack.java` (`lines()` statt nur `points()`), `TrackListPane.java`, `MapPane.java`, `GeoTagger.java`
- Test: `GeoTaggerMapUiTest.java`, `GeoTaggerTrackUiTest.java`

**Interfaces:**
- Consumes: `TrackLine`, `showTrack(String, List<TrackLine>)`, `MapListener.areaSelected` (Task 1); `GpxReader.readLines` (Task 2)
- Produces:
  - `GpxTrack`:
    - `loaded(List<GpxLine> lines)`: ohne Punkte → `FAILED` „No track points“, wie bisher;
    - `List<GpxLine> lines()`;
    - `points()` bleibt und liefert alle Punkte, für den Sync.
  - `MapPane.showTrack(GpxTrack track, String fallbackColor)`: jede Linie in ihrer eigenen Farbe oder in `fallbackColor`.
  - `MapPane.setOnAreaSelected(BiConsumer<List<MediaFile>, Boolean> handler)`: die Dateien mit Koordinaten im Rechteck und „Shift gedrückt“.
  - Controller:
    - ohne Shift `clearSelection()`, dann `select` aller Treffer, die in `mediaTable.getItems()` stehen (gefiltert);
    - mit Shift werden sie zur Auswahl hinzugefügt.

- [ ] **Step 1: Fehlschlagende Tests**

```java
// GeoTaggerMapUiTest
@Test
void ctrlDragSelectsTheFilesInTheArea(FxRobot robot) throws TimeoutException {
    loadImages(robot);
    js(robot, "myMap.setView([46.55, 11.55], 10, {animate: false})");
    select(robot, "c.jpg");
    final WebView web = controller.mapPane().mapView().getWebView();
    final Point2D a = point(robot, 46.45, 11.45);   // screen point of lat/lon via getPointForLatLng + localToScreen
    final Point2D b = point(robot, 46.65, 11.65);
    robot.press(KeyCode.CONTROL).moveTo(a).press(MouseButton.PRIMARY).moveTo(b).release(MouseButton.PRIMARY).release(KeyCode.CONTROL);
    WaitForAsyncUtils.waitForFxEvents();
    assertEquals(Set.of("a.jpg", "b.jpg"), table(robot).getSelectionModel().getSelectedItems().stream()
            .map(file -> file.path().getFileName().toString()).collect(Collectors.toSet()));
}

// GeoTaggerTrackUiTest - TOUR gets <extensions><line xmlns="http://www.topografix.com/GPX/gpx_style/0/2"><color>0000FF</color></line></extensions>
@Test
void trackColourComesFromTheFile(FxRobot robot) throws IOException, TimeoutException {
    loadFolder(robot);
    final GpxTrack tour = track(robot, "tour.gpx");
    robot.interact(() -> tour.shownProperty().set(true));
    WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> tour.state() == GpxTrack.State.LOADED);
    WaitForAsyncUtils.waitForFxEvents();
    assertEquals("#0000FF", js("geoTracks[" + jsId(tour) + "].getLayers()[0].options.color"));
}
```

`point(robot, lat, lng)` holt per `getPointForLatLng` den WebView-Punkt und rechnet ihn mit `web.localToScreen` in Bildschirmkoordinaten um. `jsId(tour)` liefert `tour.id()` als JS-String-Literal, mit `\` → `\\` und in `'…'`.

- [ ] **Step 2: Tests laufen lassen, Fehler erwarten**

Run: `.\gradlew.bat uiTest --tests "tf.geotagger.main.GeoTagger*UiTest"`
Expected: die zwei neuen Tests FAIL.

- [ ] **Step 3: Implementieren**

Siehe Interfaces. `TrackListPane.loaded` nutzt `GpxReader.readLines`. Die Ersatzfarbe aus der Farbreihe gilt nur für Linien ohne eigene Farbe.

- [ ] **Step 4: Tests laufen lassen**

Run: `.\gradlew.bat build`, dann `.\gradlew.bat uiTest`
Expected: 125 Tests grün, 33 UI-Tests grün.

- [ ] **Step 5: Commit**

```powershell
& $git -C $app add src
& $git -C $app commit -m "Map: track colours from the GPX file, separate segments, ctrl + drag selects files"
```

---

### Task 4: Icons für Undo und Redo

**Files:**
- Copy: `$gpx\src\main\resources\undo.png`, `redo.png` → `$app\src\main\resources\`
- Modify: `GeoTagger.fxml`
- Test: `GeoTaggerEditUiTest.java`

- [ ] **Step 1: Fehlschlagender Test**

```java
@Test
void undoAndRedoShowIcons(FxRobot robot) {
    for (String id : List.of("#undoButton", "#redoButton")) {
        final Button button = robot.lookup(id).queryButton();
        assertTrue(button.getText().isEmpty(), id);
        assertTrue(button.getGraphic() instanceof ImageView view && view.getImage() != null && !view.getImage().isError(), id);
        assertTrue(button.getTooltip().getText().startsWith(id.equals("#undoButton") ? "Undo" : "Redo"), id);
    }
}
```

- [ ] **Step 2: Test laufen lassen, Fehler erwarten**

Run: `.\gradlew.bat uiTest --tests tf.geotagger.main.GeoTaggerEditUiTest.undoAndRedoShowIcons`
Expected: FAIL (Text „Undo“ ist nicht leer).

- [ ] **Step 3: FXML anpassen**

`text` entfällt. Dazu kommen `<graphic><ImageView><Image url="@/undo.png"/></ImageView></graphic>` und `<tooltip><Tooltip text="Undo (Ctrl+Z)"/></tooltip>`, für Redo entsprechend. Die IDs bleiben.

- [ ] **Step 4: Tests laufen lassen**

Run: `.\gradlew.bat uiTest`
Expected: 34 UI-Tests grün (auch `undoAndRedo` und `undoAndRedoWithKeysOutsideTextFields`).

- [ ] **Step 5: Commit**

```powershell
& $git -C $app add src
& $git -C $app commit -m "Undo / redo buttons with icons (from GPXEditor)"
```

---

### Task 5: Schneller starten

**Files:**
- Modify: `MapPane.java`, `GeoTagger.java`, `GeoTaggerManager.java`, `$app\build.gradle`, `$app\README.md`

**Interfaces:**
- Produces:
  - `MapPane()` builds **no** WebView anymore.
  - `void MapPane.start()` creates the `LeafletMapView`, wires the listeners, context menu, drop and area selection, and calls `displayMap`.
  - All `MapPane` methods before `start()` are kept as `Consumer<LeafletMapView>` and run in `start()`, in order.
  - `isReady()` is false before `start()`; `mapView()` returns null before `start()`.
  - Controller: `lateInitialize` calls `Platform.runLater(mapPane::start)` after the window is shown.
  - `gradlew installDist` produces `build\install\GeoTagger\bin\GeoTagger.bat` with module path and upgrade module path. `build` is finalized by `installDist`, as in GPXEditor.

- [ ] **Step 1: Messen**

Die Zeitmarken wie in der Messung vom 06.11. vorübergehend in `GeoTaggerManager.start` einbauen: Start, FXML geladen, Fenster sichtbar, ExifTool bereit, Karte bereit (JVM-Uptime). Dann `gradlew run`.

Expected: Werte ähnlich 0,9 s / 3,3 s / 4,5 s / 5,1 s / 5,3 s. Die Zahlen gehen ins Ledger.

- [ ] **Step 2: Karte erst nach dem Fenster**

Siehe Interfaces. `UiTestSupport.waitUntilMapReady` bleibt unverändert, weil `isReady()` vor `start()` false ist.

- [ ] **Step 3: Startskripte**

`startScripts`-Block und `tasks.named('build') { finalizedBy 'installDist' }` aus GPXEditor `build.gradle:280–300` übernehmen, mit `javafxJars` und `jsobjectJar` von GeoTagger.

- [ ] **Step 4: Nachmessen und testen**

Die Zeitmarken wie in Step 1. Expected: „Fenster sichtbar“ mindestens 1,5 s früher als in Step 1; die Zahlen kommen ins Ledger. Danach die Zeitmarken wieder entfernen.

Run: `.\gradlew.bat build`, dann `.\gradlew.bat uiTest`
Expected: 125 Tests und 34 UI-Tests grün. `build\install\GeoTagger\bin\GeoTagger.bat` existiert.

Start ohne Gradle: `build\install\GeoTagger\bin\GeoTagger.bat`. Das Fenster erscheint, nach kurzer Zeit auch die Karte. Danach ein `Stop-Process` nur für diesen Prozess.

- [ ] **Step 5: README und Commit**

In den Notes der README: „Note on running: `gradlew installDist`, then start `build\install\GeoTagger\bin\GeoTagger.bat` - faster than `gradlew run`.“

```powershell
& $git -C $app add build.gradle README.md src
& $git -C $app commit -m "Faster start: map after the window is shown, start scripts via installDist"
```

---

### Task 6: Dateiliste füllt sich beim Lesen

**Files:**
- Modify: `$app\src\main\java\tf\geotagger\metadata\MetadataReader.java`, `GeoTagger.java`
- Test: `MetadataReaderTest.java`, `GeoTaggerUiTest.java`

**Interfaces:**
- Produces:
  - `List<MediaFile> MetadataReader.read(List<Path> files, Consumer<List<MediaFile>> chunkRead)`: wie bisher, aber jeder fertige Block geht an `chunkRead`. Die `IntConsumer`-Variante bleibt und ruft diese Methode auf.
  - Controller:
    - Vor dem Lesen werden `files` geleert, `folder` gesetzt und das Undo des alten Ordners gelöscht. Der alte Ordner ist zu diesem Zeitpunkt schon gespeichert oder verworfen; die Rückfrage kommt vorher.
    - Jeder Block landet per `Platform.runLater` mit `files.addAll(block)` und `refreshFiles()` in der Liste. Die Tabelle bleibt währenddessen deaktiviert (`busy`).
    - Ein zweites `loadFolder` vor dem Ende des ersten ignoriert die alten Blöcke: Jeder Ladevorgang hat eine Nummer, und `runLater` prüft sie.
    - Schlägt das Lesen fehl, wird die Liste geleert, der Status lautet „Loading … failed“.

- [ ] **Step 1: Fehlschlagende Tests**

```java
// MetadataReaderTest
@Test
void chunksAreHandedOverWhileReading() throws IOException {
    // 105 copies as in readsInChunksKeepsOrderAndReportsProgress
    final List<Integer> chunkSizes = new ArrayList<>();
    final List<MediaFile> media = reader.read(files, chunk -> chunkSizes.add(chunk.size()));
    assertEquals(List.of(50, 50, 5), chunkSizes);
    assertEquals(105, media.size());
}

// GeoTaggerUiTest
@Test
void loadingTwiceKeepsOnlyTheSecondFolder(FxRobot robot) throws IOException, TimeoutException, InterruptedException {
    final Path first = Files.createDirectory(dir.resolve("first"));
    final Path second = Files.createDirectory(dir.resolve("second"));
    for (int i = 0; i < 120; i++) {
        TestFiles.image(first, String.format("f%03d.jpg", i));
    }
    TestFiles.image(second, "s.jpg");
    robot.interact(() -> {
        controller.loadFolder(first, false);
        controller.loadFolder(second, false);
    });
    UiTestSupport.waitUntilIdle(controller);
    Thread.sleep(500);   // late blocks of the first folder would arrive now
    WaitForAsyncUtils.waitForFxEvents();
    assertEquals(List.of("s.jpg"), robot.lookup("#mediaTable").queryTableView().getItems().stream()
            .map(item -> ((MediaFile) item).path().getFileName().toString()).toList());
}
```

- [ ] **Step 2: Tests laufen lassen, Fehler erwarten**

Run: `.\gradlew.bat test --tests tf.geotagger.metadata.MetadataReaderTest`
Expected: FAIL, Kompilierfehler (keine Methode mit `Consumer<List<MediaFile>>`).

- [ ] **Step 3: Implementieren**

Siehe Interfaces. `waitUntilIdle` passt weiter: `busy` endet erst nach dem letzten Block.

- [ ] **Step 4: Tests laufen lassen**

Run: `.\gradlew.bat build`, dann `.\gradlew.bat uiTest`
Expected: 126 Tests grün, 35 UI-Tests grün.

- [ ] **Step 5: Commit**

```powershell
& $git -C $app add src
& $git -C $app commit -m "File list fills while the metadata is read"
```

---

### Task 7: Doku und manuelle Prüfung

- [ ] **Step 1: READMEs**

- **GeoTagger:** Rahmenauswahl, Track-Farben aus der GPX-Datei, Startskript.
- **GeoHelper:** Clustering-Optionen, `TrackLine`, `areaSelected`.

- [ ] **Step 2: Manuell prüfen** (mit dem Ordner aus der Prüfung von Plan 4)

1. Bündel erst bei mehr Nähe. Ab Zoom 15 gibt es keine Bündel mehr.
2. Strg+Ziehen zeichnet ein Rechteck und wählt die Dateien darin. Strg+Umschalt+Ziehen ergänzt die Auswahl. Ziehen ohne Strg verschiebt weiter die Karte.
3. Tracks mit Farbe in der GPX-Datei, z. B. aus GPXEditor gespeichert, erscheinen in dieser Farbe. Segmente sind nicht mehr durch gerade Linien verbunden.
4. Undo und Redo zeigen Icons und Tooltips.
5. Start über `build\install\GeoTagger\bin\GeoTagger.bat`: Das Fenster ist spürbar schneller da, die Karte kommt kurz danach.
6. Großer Ordner: Die Liste füllt sich in 50er-Blöcken, die Zähler der Filter laufen mit.

- [ ] **Step 3: Commit**

```powershell
& $git -C $app add README.md
& $git -C $app commit -m "README: plan 4b"
& $git -C $geo add README.md
& $git -C $geo commit -m "README: cluster options, coloured tracks, area selection"
```

---

## Abschluss von Plan 4b

- **GeoHelper:** 28 Tests grün, in mavenLocal veröffentlicht.
- **GeoTagger:** 126 Tests und 35 UI-Tests grün, keine Compilerwarnungen.
- Die Checkliste aus Task 7 ist abgehakt.
- Danach Plan 5 (Anreichern).

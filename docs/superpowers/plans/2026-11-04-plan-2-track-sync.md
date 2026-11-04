# GeoTagger Plan 2: Track-Sync-Logik – Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use anthropic-skills:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Dateien ohne Koordinaten bekommen sie anhand ihrer Aufnahmezeit aus GPX-Tracks. Das ist reine, getestete Logik ohne UI, auf die der Sync-Dialog in Plan 6 aufsetzt.

**Architecture:**
- **`tf.geotagger.track`** liest GPX-Trackpunkte mit Jackson XML (wie in den anderen Anwendungen) und legt sie auf einer zeitlich sortierten Zeitleiste mit Nachbarsuche ab.
- **`tf.geotagger.sync`** rechnet die Kamerazeit über Kamera-Zeitzone und Offset in UTC um und sucht die Nachbarpunkte. Daraus berechnet es pro Datei ein Ergebnis, die Vorschau des Dialogs. Auf Wunsch überträgt es die Ergebnisse als ausstehende Änderungen in die `MediaFile`s.
- Beide Pakete hängen nur von `tf.geotagger.model` ab.

**Tech Stack:** Java 25, Jackson XML (`jackson-dataformat-xml`, BOM 2.21.2 wie in GPXEditor), `java.time`, JUnit 5.13.0.

Spec: [2026-11-03-geotagger-design.md](../specs/2026-11-03-geotagger-design.md), Abschnitt „Track-Sync-Dialog“

## Global Constraints

- Java 25 über die Gradle-Toolchain, Gradle 9.2.1 über den Wrapper. Gearbeitet wird direkt auf `master`.
- **Lizenz-Header:** Jede `.java`-Datei, auch in `src/test`, beginnt mit dem BSD-Header aus GPXEditor („Copyright (c) 2014ff Thomas Feuster“, 25 Zeilen, wörtlich übernommen, z. B. von `GeoTagger\src\main\java\tf\geotagger\model\Field.java`). Die Codeblöcke unten lassen ihn der Kürze halber weg.
- Code und Kommentare sind auf Englisch. Compilerwarnungen in `src/main` und `src/test` werden behoben (`-Xlint:unchecked`, `-Xlint:deprecation`).
- **GPX** wird mit **Jackson XML** gelesen (`jackson-dataformat-xml`, BOM 2.21.2), dem Datenbindungs-Ansatz, den auch GPXEditor nutzt.
  - Konsistenz zwischen den Anwendungen geht hier vor einem abhängigkeitsfreien StAX-Parser.
  - gpx-parser wird nicht verwendet: Es gibt ihn nur selbst gebaut in mavenLocal, und für den Sync reichen Zeit, Koordinate und Höhe.
  - Eine Probe gegen Jackson 2.21.2 hat bestätigt:
    - Records mit `@JacksonXmlProperty(isAttribute = true)` und `@JacksonXmlElementWrapper(useWrapping = false)` funktionieren.
    - Der GPX-Namensraum wird ignoriert, `FAIL_ON_UNKNOWN_PROPERTIES = false` überspringt `wpt`, `name` und `extensions`.
    - Fehlende Listen kommen als `null`.
    - DTD-Entities werden nicht aufgelöst.
- **Git** in jeder PowerShell-Sitzung:
  ```powershell
  $git = 'C:\Users\thomas\AppData\Local\GitHubDesktop\app-3.6.7\resources\app\git\cmd\git.exe'
  $app = 'D:\Thomas\Development\NetBeansProjects\GeoTagger'
  ```
  Gradle-Befehle laufen in `$app` (`Set-Location $app`).
- **Fachliche Regeln aus der Spec** (verbindlich):
  - Maximale Zeitdifferenz zum nächsten Trackpunkt: Standard **30 min**.
  - Zuweisung: **zwischen den Nachbarpunkten interpolieren** (Standard) oder exakt den nächsten Punkt nehmen. Liegt nur einer der beiden Nachbarn innerhalb des Maximums, wird dieser Punkt genommen.
  - Mit „Keine Trackpunkte in der Zukunft berücksichtigen“ zählt nur der Vorgänger.
  - „Großer Abstand“ bedeutet: Der Abstand zum nächsten echten Trackpunkt ist größer als die halbe maximale Differenz.
  - „Höhe aus Track übernehmen“ (Standard an) betrifft nur Dateien, die in diesem Lauf Koordinaten bekommen.
  - „Zeitzone in Datei schreiben“ (Standard aus) setzt die Kamera-Zeitzone als `OffsetTimeOriginal`. Das gilt nur für Bilder, Videos bekommen keine Zeitzone.
  - Die Kamera-Zeitzone wird aus `OffsetTimeOriginal` der Dateien vorbelegt (häufigster Wert), sonst aus der Systemzeitzone.
  - Dateien: „Ohne Koordinaten“ oder „Auswahl“, dazu „Vorhandene überschreiben“ (Standard aus).
  - Die Aufnahmezeit in der Datei wird nie verändert. Offset und Zeitzone gelten nur für den Abgleich.
- **Zeitsemantik:**
  - `CaptureTime.utc == false`: Die Zeit ist Kamera-Ortszeit und wird in der Kamera-Zeitzone gedeutet. Das gilt für EXIF/XMP `DateTimeOriginal`, auch bei Canon-Videos, die `DateTimeOriginal` haben.
  - `CaptureTime.utc == true`: QuickTime `CreateDate`, die Zeit ist bereits UTC, die Kamera-Zeitzone wird ignoriert.
  - Der Offset gilt in beiden Fällen. Ein positiver Offset bedeutet: Die Kamera geht vor, die echte Zeit ist also früher.

## Dateistruktur

```
src\main\java\tf\geotagger\track\
  TrackPoint.java        -- one GPX track point: time, lat, lon, optional elevation
  GpxReader.java         -- read track points of a GPX 1.0 / 1.1 file (Jackson XML), list GPX files of a folder
  TrackTimeline.java     -- all points of the loaded tracks sorted by time, neighbour search, load several files
src\main\java\tf\geotagger\sync\
  CameraClock.java       -- capture time -> UTC (camera zone + offset), clock shown by the camera now
  SyncParameters.java    -- dialog parameters incl. defaults and suggested camera zone
  SyncResult.java        -- per file: status, corrected time, position, elevation, gap to nearest real point
  TrackSync.java         -- match files against a timeline, choose eligible files, apply results
src\test\java\tf\geotagger\track\
  GpxReaderTest.java, TrackTimelineTest.java
src\test\java\tf\geotagger\sync\
  TestMedia.java         -- MediaFile factory for sync tests
  CameraClockTest.java, SyncParametersTest.java, TrackSyncTest.java, TrackSyncApplyTest.java
```

---

### Task 1: GPX lesen

**Files:**
- Modify: `build.gradle` (Jackson XML)
- Create: `src\main\java\tf\geotagger\track\TrackPoint.java`, `GpxReader.java`
- Test: `src\test\java\tf\geotagger\track\GpxReaderTest.java`

**Interfaces:**
- Consumes: –
- Produces:
  - `public record TrackPoint(Instant time, double latitude, double longitude, Double elevation)`. `elevation` darf `null` sein.
  - `public final class GpxReader`
    - `static List<TrackPoint> read(Path gpx) throws IOException` liefert die `trkpt` aller `trk`/`trkseg` in Dateireihenfolge. Punkte ohne `time` oder ohne Koordinate werden übersprungen. `wpt` und `rtept` werden ignoriert.
    - `static List<Path> findGpxFiles(Path dir) throws IOException` liefert die `*.gpx` direkt im Ordner, sortiert.

- [ ] **Step 1: Fehlschlagenden Test schreiben**

`src\test\java\tf\geotagger\track\GpxReaderTest.java`:

```java
package tf.geotagger.track;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GpxReaderTest {
    private static final String GPX_11 = """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1" creator="test" xmlns="http://www.topografix.com/GPX/1/1">
              <wpt lat="1" lon="1"><time>2026-09-02T09:00:00Z</time></wpt>
              <trk><name>Tag 1</name>
                <trkseg>
                  <trkpt lat="46.4071" lon="11.5932"><ele>1200.5</ele><time>2026-09-02T09:08:00Z</time></trkpt>
                  <trkpt lat="46.4080" lon="11.6000"><time>2026-09-02T09:09:00.500Z</time><extensions><time>x</time></extensions></trkpt>
                </trkseg>
                <trkseg>
                  <trkpt lat="-33.5" lon="-0.25"><ele>3</ele><time>2026-09-02T11:10:00+02:00</time></trkpt>
                  <trkpt lat="46.5" lon="11.7"><ele>1300</ele></trkpt>
                </trkseg>
              </trk>
            </gpx>
            """;

    private static final String GPX_10 = """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.0" creator="test" xmlns="http://www.topografix.com/GPX/1/0">
              <trk><trkseg>
                <trkpt lat="46.4" lon="11.6"><time>2026-09-02T09:08:00</time></trkpt>
              </trkseg></trk>
            </gpx>
            """;

    @TempDir
    Path dir;

    private Path gpx(String name, String content) throws IOException {
        return Files.writeString(dir.resolve(name), content);
    }

    @Test
    void readsTrackPointsOfAllSegments() throws IOException {
        final List<TrackPoint> points = GpxReader.read(gpx("tag1.gpx", GPX_11));

        assertEquals(3, points.size());
        assertEquals(new TrackPoint(Instant.parse("2026-09-02T09:08:00Z"), 46.4071, 11.5932, 1200.5), points.get(0));
        assertEquals(Instant.parse("2026-09-02T09:09:00.500Z"), points.get(1).time());
        assertNull(points.get(1).elevation());
        assertEquals(new TrackPoint(Instant.parse("2026-09-02T09:10:00Z"), -33.5, -0.25, 3.0), points.get(2));
    }

    @Test
    void gpx10WithoutZoneIsReadAsUtc() throws IOException {
        final List<TrackPoint> points = GpxReader.read(gpx("alt.gpx", GPX_10));

        assertEquals(List.of(new TrackPoint(Instant.parse("2026-09-02T09:08:00Z"), 46.4, 11.6, null)), points);
    }

    @Test
    void brokenFileFails() throws IOException {
        final Path broken = gpx("kaputt.gpx", "<gpx><trk><trkseg><trkpt lat=\"1\" lon=\"2\">");
        assertThrows(IOException.class, () -> GpxReader.read(broken));
    }

    @Test
    void findsGpxFilesOfTheFolderOnly() throws IOException {
        gpx("a.gpx", GPX_10);
        gpx("b.GPX", GPX_10);
        gpx("notiz.txt", "x");
        Files.createDirectory(dir.resolve("sub"));
        gpx("sub/c.gpx", GPX_10);

        assertEquals(List.of(dir.resolve("a.gpx"), dir.resolve("b.GPX")), GpxReader.findGpxFiles(dir));
    }
}
```

- [ ] **Step 2: Test laufen lassen, Fehler erwarten**

Run: `.\gradlew.bat test --tests "tf.geotagger.track.GpxReaderTest"`
Expected: FAIL, Kompilierfehler „cannot find symbol: class GpxReader“.

- [ ] **Step 3: Jackson XML in `build.gradle` aufnehmen**

In `build.gradle` kommen im Block `dependencies` nach `implementation 'tf.ExifToolHelper:ExifToolHelper:0.1'` diese Zeilen dazu:

```groovy

    // GPX is read with Jackson XML - the same data binding the other applications (e.g. GPXEditor) use
    // BOM: one version for all jackson modules - same as GPXEditor
    implementation platform('com.fasterxml.jackson:jackson-bom:2.21.2')
    implementation 'com.fasterxml.jackson.dataformat:jackson-dataformat-xml'
```

- [ ] **Step 4: Implementierung schreiben**

`src\main\java\tf\geotagger\track\TrackPoint.java`:

```java
package tf.geotagger.track;

import java.time.Instant;
import java.util.Objects;

/**
 * One point of a GPX track.
 *
 * @param elevation may be null if the track has no elevation
 */
public record TrackPoint(Instant time, double latitude, double longitude, Double elevation) {
    public TrackPoint {
        Objects.requireNonNull(time);
    }
}
```

`src\main\java\tf\geotagger\track\GpxReader.java`:

```java
package tf.geotagger.track;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Reads the track points (trk / trkseg / trkpt) of GPX 1.0 and 1.1 files.
 *
 * Jackson XML is used on purpose instead of the JDK's StAX parser: it is the data binding the other applications
 * (e.g. GPXEditor) use as well, and consistency between them is preferred over saving a dependency.
 * Element names are matched without namespace, so both GPX versions work. Only time, position and elevation are
 * needed for the track sync - all other elements (wpt, rte, name, extensions, ...) are ignored.
 */
public final class GpxReader {
    // only the parts of the GPX schema we need - see https://www.topografix.com/GPX/1/1/
    // lists are null if the element is missing
    record Gpx(@JacksonXmlElementWrapper(useWrapping = false) @JacksonXmlProperty(localName = "trk") List<Trk> tracks) {
    }

    record Trk(@JacksonXmlElementWrapper(useWrapping = false) @JacksonXmlProperty(localName = "trkseg") List<Trkseg> segments) {
    }

    record Trkseg(@JacksonXmlElementWrapper(useWrapping = false) @JacksonXmlProperty(localName = "trkpt") List<Trkpt> points) {
    }

    record Trkpt(@JacksonXmlProperty(isAttribute = true, localName = "lat") Double latitude,
            @JacksonXmlProperty(isAttribute = true, localName = "lon") Double longitude,
            @JacksonXmlProperty(localName = "ele") Double elevation,
            // parsed by ourselves: GPX 1.0 files sometimes lack the zone
            @JacksonXmlProperty(localName = "time") String time) {
    }

    // thread safe once configured; Jackson XML doesn't resolve DTD entities
    private static final XmlMapper MAPPER = XmlMapper.builder()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .build();

    private GpxReader() {
    }

    /**
     * Track points in file order - points without time or position are skipped.
     */
    public static List<TrackPoint> read(Path gpx) throws IOException {
        final Gpx content;
        try (InputStream in = Files.newInputStream(gpx)) {
            content = MAPPER.readValue(in, Gpx.class);
        } catch (JsonProcessingException ex) {
            throw new IOException("Can't read GPX file " + gpx + ": " + ex.getOriginalMessage(), ex);
        }

        final List<TrackPoint> points = new ArrayList<>();
        for (Trk track : nullToEmpty(content.tracks())) {
            for (Trkseg segment : nullToEmpty(track.segments())) {
                for (Trkpt point : nullToEmpty(segment.points())) {
                    final Instant time = toInstant(point.time());
                    if (time != null && point.latitude() != null && point.longitude() != null) {
                        points.add(new TrackPoint(time, point.latitude(), point.longitude(), point.elevation()));
                    }
                }
            }
        }
        return points;
    }

    /**
     * GPX files directly in dir (no sub folders), sorted by name.
     */
    public static List<Path> findGpxFiles(Path dir) throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".gpx"))
                    .sorted()
                    .toList();
        }
    }

    private static <T> List<T> nullToEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }

    private static Instant toInstant(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        final String trimmed = text.trim();
        try {
            return OffsetDateTime.parse(trimmed).toInstant();
        } catch (DateTimeParseException ex) {
            // GPX requires UTC - times without zone are read as UTC
            try {
                return LocalDateTime.parse(trimmed).toInstant(ZoneOffset.UTC);
            } catch (DateTimeParseException ex2) {
                return null;
            }
        }
    }
}
```

Hinweis: Steht in `lat`, `lon` oder `ele` keine Zahl, schlägt Jackson für die ganze Datei fehl, statt nur den Punkt zu überspringen. Die Datei landet dann als fehlerhaft in der Track-Liste. Das ist gewollt: Solche Dateien sind kaputt, und ein stilles Weglassen würde Lücken im Track verstecken.

- [ ] **Step 5: Tests laufen lassen**

Run: `.\gradlew.bat test`
Expected: BUILD SUCCESSFUL, 57 Tests grün (53 bisher + 4) und keine Compilerwarnungen.

- [ ] **Step 6: Commit**

```powershell
& $git -C $app add build.gradle src
& $git -C $app commit -m "Read GPX track points with Jackson XML"
```

---

### Task 2: Zeitleiste aller Trackpunkte

**Files:**
- Create: `src\main\java\tf\geotagger\track\TrackTimeline.java`
- Test: `src\test\java\tf\geotagger\track\TrackTimelineTest.java`

**Interfaces:**
- Consumes: `TrackPoint`, `GpxReader.read(Path)` aus Task 1
- Produces: `public final class TrackTimeline`
  - `TrackTimeline(Collection<TrackPoint> points)`: Die Punkte werden nach Zeit sortiert, mehrere Tracks dürfen sich mischen.
  - `int size()`, `boolean isEmpty()`
  - `TrackPoint atOrBefore(Instant time)`: letzter Punkt mit `time <= t`, sonst `null`
  - `TrackPoint atOrAfter(Instant time)`: erster Punkt mit `time >= t`, sonst `null`
  - `static LoadResult load(List<Path> gpxFiles)` mit `public record LoadResult(TrackTimeline timeline, Map<Path, String> failures)`. Unlesbare Dateien landen in `failures` (Pfad → Meldung), die übrigen werden trotzdem genutzt.

- [ ] **Step 1: Fehlschlagenden Test schreiben**

`src\test\java\tf\geotagger\track\TrackTimelineTest.java`:

```java
package tf.geotagger.track;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrackTimelineTest {
    private static final Instant T0 = Instant.parse("2026-09-02T09:00:00Z");

    @TempDir
    Path dir;

    private static TrackPoint point(long seconds) {
        return new TrackPoint(T0.plusSeconds(seconds), 46.0 + seconds / 1000.0, 11.0, null);
    }

    @Test
    void findsNeighboursAcrossUnsortedTracks() {
        final TrackTimeline timeline = new TrackTimeline(List.of(point(120), point(0), point(60)));

        assertEquals(3, timeline.size());
        assertEquals(point(0), timeline.atOrBefore(T0.plusSeconds(30)));
        assertEquals(point(60), timeline.atOrAfter(T0.plusSeconds(30)));
    }

    @Test
    void exactTimeIsItsOwnNeighbour() {
        final TrackTimeline timeline = new TrackTimeline(List.of(point(0), point(60), point(120)));

        assertEquals(point(60), timeline.atOrBefore(T0.plusSeconds(60)));
        assertEquals(point(60), timeline.atOrAfter(T0.plusSeconds(60)));
    }

    @Test
    void noNeighbourOutsideTheTrack() {
        final TrackTimeline timeline = new TrackTimeline(List.of(point(0), point(60)));

        assertNull(timeline.atOrBefore(T0.minusSeconds(1)));
        assertNull(timeline.atOrAfter(T0.plusSeconds(61)));
    }

    @Test
    void emptyTimelineHasNoNeighbours() {
        final TrackTimeline timeline = new TrackTimeline(List.of());

        assertTrue(timeline.isEmpty());
        assertNull(timeline.atOrBefore(T0));
        assertNull(timeline.atOrAfter(T0));
    }

    @Test
    void loadKeepsReadableFilesAndReportsBrokenOnes() throws IOException {
        final Path good = Files.writeString(dir.resolve("gut.gpx"), """
                <gpx version="1.1" xmlns="http://www.topografix.com/GPX/1/1"><trk><trkseg>
                  <trkpt lat="46.4" lon="11.6"><time>2026-09-02T09:08:00Z</time></trkpt>
                  <trkpt lat="46.5" lon="11.7"><time>2026-09-02T09:09:00Z</time></trkpt>
                </trkseg></trk></gpx>
                """);
        final Path broken = Files.writeString(dir.resolve("kaputt.gpx"), "<gpx><trk>");

        final TrackTimeline.LoadResult result = TrackTimeline.load(List.of(good, broken));

        assertEquals(2, result.timeline().size());
        assertEquals(List.of(broken), List.copyOf(result.failures().keySet()));
    }
}
```

- [ ] **Step 2: Test laufen lassen, Fehler erwarten**

Run: `.\gradlew.bat test --tests "tf.geotagger.track.TrackTimelineTest"`
Expected: FAIL, Kompilierfehler „cannot find symbol: class TrackTimeline“.

- [ ] **Step 3: Implementierung schreiben**

`src\main\java\tf\geotagger\track\TrackTimeline.java`:

```java
package tf.geotagger.track;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * All points of the tracks used for a sync, sorted by time - tracks may overlap or come in any order.
 */
public final class TrackTimeline {
    /**
     * @param failures GPX files that couldn't be read, with the reason - the other files are part of the timeline
     */
    public record LoadResult(TrackTimeline timeline, Map<Path, String> failures) {
        public LoadResult {
            failures = Map.copyOf(failures);
        }
    }

    private final List<TrackPoint> points;
    private final Instant[] times;

    public TrackTimeline(Collection<TrackPoint> points) {
        this.points = points.stream().sorted(Comparator.comparing(TrackPoint::time)).toList();
        this.times = this.points.stream().map(TrackPoint::time).toArray(Instant[]::new);
    }

    public static LoadResult load(List<Path> gpxFiles) {
        final List<TrackPoint> points = new ArrayList<>();
        final Map<Path, String> failures = new LinkedHashMap<>();
        for (Path gpx : gpxFiles) {
            try {
                points.addAll(GpxReader.read(gpx));
            } catch (IOException ex) {
                failures.put(gpx, ex.getMessage());
            }
        }
        return new LoadResult(new TrackTimeline(points), failures);
    }

    public int size() {
        return points.size();
    }

    public boolean isEmpty() {
        return points.isEmpty();
    }

    /**
     * Last point at or before time - null if the track starts later.
     */
    public TrackPoint atOrBefore(Instant time) {
        final int index = firstIndexAtOrAfter(time);
        if (index < times.length && times[index].equals(time)) {
            return points.get(index);
        }
        return index > 0 ? points.get(index - 1) : null;
    }

    /**
     * First point at or after time - null if the track ends earlier.
     */
    public TrackPoint atOrAfter(Instant time) {
        final int index = firstIndexAtOrAfter(time);
        return index < times.length ? points.get(index) : null;
    }

    private int firstIndexAtOrAfter(Instant time) {
        int low = 0;
        int high = times.length;
        while (low < high) {
            final int mid = (low + high) >>> 1;
            if (times[mid].isBefore(time)) {
                low = mid + 1;
            } else {
                high = mid;
            }
        }
        return low;
    }
}
```

- [ ] **Step 4: Tests laufen lassen**

Run: `.\gradlew.bat test`
Expected: BUILD SUCCESSFUL, 62 Tests grün.

- [ ] **Step 5: Commit**

```powershell
& $git -C $app add src
& $git -C $app commit -m "Add track timeline with neighbour search"
```

---

### Task 3: Kamerauhr und Sync-Parameter

**Files:**
- Create: `src\main\java\tf\geotagger\sync\CameraClock.java`, `SyncParameters.java`
- Test:
  - `src\test\java\tf\geotagger\sync\TestMedia.java`
  - `src\test\java\tf\geotagger\sync\CameraClockTest.java`
  - `src\test\java\tf\geotagger\sync\SyncParametersTest.java`

**Interfaces:**
- Consumes: `CaptureTime`, `MediaFile`, `Field`, `MediaType` und `Snapshot` aus `tf.geotagger.model`
- Produces:
  - `public final class CameraClock`
    - `static Instant toUtc(CaptureTime captureTime, ZoneId cameraZone, Duration cameraOffset)`
    - `static LocalDateTime cameraDisplay(Instant now, ZoneId cameraZone, Duration cameraOffset)` liefert das, was die Kamera jetzt anzeigen müsste. Das ist die Anzeige „Kamera-Uhrzeit“ im Dialog.
    - Offset positiv bedeutet: Die Kamera geht vor.
  - `public record SyncParameters(ZoneId cameraZone, Duration cameraOffset, Duration maxGap, boolean interpolate, boolean onlyPastPoints, boolean takeElevation, boolean writeTimeZone)`
    - `static final Duration DEFAULT_MAX_GAP = Duration.ofMinutes(30)`
    - `static SyncParameters defaults(ZoneId cameraZone)` mit Offset 0, 30 min, interpolate, nicht nur Vergangenheit, Höhe übernehmen, keine Zeitzone schreiben
    - `static ZoneId suggestedCameraZone(List<MediaFile> files, ZoneId fallback)`
  - Test-Hilfe `final class TestMedia` (Paket `tf.geotagger.sync`, nur in `src/test`):
    - `static MediaFile image(LocalDateTime localCaptureTime)`
    - `static MediaFile file(MediaType type, CaptureTime captureTime, Map<Field, Object> values)`

- [ ] **Step 1: Test-Hilfe und fehlschlagende Tests schreiben**

`src\test\java\tf\geotagger\sync\TestMedia.java`:

```java
package tf.geotagger.sync;

import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import tf.geotagger.model.CaptureTime;
import tf.geotagger.model.Field;
import tf.geotagger.model.MediaFile;
import tf.geotagger.model.MediaType;
import tf.geotagger.model.Snapshot;

/**
 * MediaFiles for sync tests - no file on disk needed.
 */
final class TestMedia {
    private static final AtomicInteger COUNTER = new AtomicInteger();

    private TestMedia() {
    }

    /**
     * Image with a camera local capture time (null: no capture time) and no metadata values.
     */
    static MediaFile image(LocalDateTime localCaptureTime) {
        return file(MediaType.IMAGE, localCaptureTime == null ? null : new CaptureTime(localCaptureTime, false), Map.of());
    }

    static MediaFile file(MediaType type, CaptureTime captureTime, Map<Field, Object> values) {
        final Path path = Path.of("C:/fotos/media_" + COUNTER.incrementAndGet() + (type == MediaType.IMAGE ? ".jpg" : ".mov"));
        return new MediaFile(path, type, new Snapshot(captureTime, FileTime.fromMillis(0), values));
    }
}
```

`src\test\java\tf\geotagger\sync\CameraClockTest.java`:

```java
package tf.geotagger.sync;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import tf.geotagger.model.CaptureTime;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CameraClockTest {
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    private static CaptureTime local(String dateTime) {
        return new CaptureTime(LocalDateTime.parse(dateTime), false);
    }

    @Test
    void localTimeInSummer() {
        assertEquals(Instant.parse("2026-09-02T09:08:00Z"),
                CameraClock.toUtc(local("2026-09-02T11:08:00"), BERLIN, Duration.ZERO));
    }

    @Test
    void localTimeInWinter() {
        assertEquals(Instant.parse("2026-11-04T10:44:03Z"),
                CameraClock.toUtc(local("2026-11-04T11:44:03"), BERLIN, Duration.ZERO));
    }

    @Test
    void cameraAheadOrBehindIsCorrected() {
        assertEquals(Instant.parse("2026-09-02T09:08:00Z"),
                CameraClock.toUtc(local("2026-09-02T11:10:13"), BERLIN, Duration.ofSeconds(133)));
        assertEquals(Instant.parse("2026-09-02T09:08:00Z"),
                CameraClock.toUtc(local("2026-09-02T11:05:47"), BERLIN, Duration.ofSeconds(-133)));
    }

    @Test
    void fixedOffsetZone() {
        assertEquals(Instant.parse("2026-09-02T14:08:00Z"),
                CameraClock.toUtc(local("2026-09-02T09:08:00"), ZoneOffset.ofHours(-5), Duration.ZERO));
    }

    @Test
    void utcCaptureTimeIgnoresTheZoneButNotTheOffset() {
        final CaptureTime utc = new CaptureTime(LocalDateTime.parse("2026-09-02T09:08:00"), true);
        assertEquals(Instant.parse("2026-09-02T09:08:00Z"), CameraClock.toUtc(utc, BERLIN, Duration.ZERO));
        assertEquals(Instant.parse("2026-09-02T08:08:00Z"), CameraClock.toUtc(utc, BERLIN, Duration.ofHours(1)));
    }

    @Test
    void cameraDisplayShowsWhatTheCameraShouldShowNow() {
        assertEquals(LocalDateTime.parse("2026-09-02T11:10:13"),
                CameraClock.cameraDisplay(Instant.parse("2026-09-02T09:08:00Z"), BERLIN, Duration.ofSeconds(133)));
    }
}
```

`src\test\java\tf\geotagger\sync\SyncParametersTest.java`:

```java
package tf.geotagger.sync;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tf.geotagger.model.CaptureTime;
import tf.geotagger.model.Field;
import tf.geotagger.model.MediaFile;
import tf.geotagger.model.MediaType;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SyncParametersTest {
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    private static MediaFile withOffset(ZoneOffset offset) {
        return TestMedia.file(MediaType.IMAGE, new CaptureTime(LocalDateTime.parse("2026-09-02T11:08:00"), false),
                Map.of(Field.TIME_OFFSET, offset));
    }

    @Test
    void defaultsFollowTheSpec() {
        final SyncParameters parameters = SyncParameters.defaults(BERLIN);

        assertEquals(BERLIN, parameters.cameraZone());
        assertEquals(Duration.ZERO, parameters.cameraOffset());
        assertEquals(Duration.ofMinutes(30), parameters.maxGap());
        assertTrue(parameters.interpolate());
        assertFalse(parameters.onlyPastPoints());
        assertTrue(parameters.takeElevation());
        assertFalse(parameters.writeTimeZone());
    }

    @Test
    void maxGapMustBePositive() {
        assertThrows(IllegalArgumentException.class,
                () -> new SyncParameters(BERLIN, Duration.ZERO, Duration.ZERO, true, false, true, false));
    }

    @Test
    void suggestedZoneIsTheMostFrequentOffsetOrTheFallback() {
        final List<MediaFile> files = List.of(withOffset(ZoneOffset.ofHours(2)), TestMedia.image(LocalDateTime.parse("2026-09-02T11:08:00")),
                withOffset(ZoneOffset.ofHours(1)), withOffset(ZoneOffset.ofHours(2)));

        assertEquals(ZoneOffset.ofHours(2), SyncParameters.suggestedCameraZone(files, BERLIN));
        assertEquals(BERLIN, SyncParameters.suggestedCameraZone(
                List.of(TestMedia.image(LocalDateTime.parse("2026-09-02T11:08:00"))), BERLIN));
    }
}
```

- [ ] **Step 2: Tests laufen lassen, Fehler erwarten**

Run: `.\gradlew.bat test --tests "tf.geotagger.sync.*"`
Expected: FAIL, Kompilierfehler „cannot find symbol: class CameraClock“.

- [ ] **Step 3: Implementierung schreiben**

`src\main\java\tf\geotagger\sync\CameraClock.java`:

```java
package tf.geotagger.sync;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import tf.geotagger.model.CaptureTime;

/**
 * Converts between the camera clock and real (UTC) time.
 * cameraOffset > 0 means the camera clock is ahead - the real time is earlier than the recorded one.
 */
public final class CameraClock {
    private CameraClock() {
    }

    public static Instant toUtc(CaptureTime captureTime, ZoneId cameraZone, Duration cameraOffset) {
        final Instant recorded = captureTime.utc()
                // QuickTime CreateDate - already UTC, the camera zone doesn't matter
                ? captureTime.dateTime().toInstant(ZoneOffset.UTC)
                : captureTime.dateTime().atZone(cameraZone).toInstant();
        return recorded.minus(cameraOffset);
    }

    /**
     * What the camera display should show at the given moment - for comparison with the real camera.
     */
    public static LocalDateTime cameraDisplay(Instant now, ZoneId cameraZone, Duration cameraOffset) {
        return LocalDateTime.ofInstant(now.plus(cameraOffset), cameraZone);
    }
}
```

`src\main\java\tf\geotagger\sync\SyncParameters.java`:

```java
package tf.geotagger.sync;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import tf.geotagger.model.MediaFile;

/**
 * Parameters of the track sync dialog - see spec, section "Track-Sync-Dialog".
 *
 * @param cameraZone zone the camera clock was set to
 * @param cameraOffset additional deviation of the camera clock, positive = camera ahead
 * @param maxGap maximum time between capture time and the nearest track point
 * @param interpolate interpolate between the neighbouring track points instead of taking the nearest one
 * @param onlyPastPoints only use track points before the capture time
 * @param takeElevation set the elevation of the track for files that get coordinates
 * @param writeTimeZone set the camera zone as OffsetTimeOriginal for images that get coordinates
 */
public record SyncParameters(ZoneId cameraZone, Duration cameraOffset, Duration maxGap, boolean interpolate,
        boolean onlyPastPoints, boolean takeElevation, boolean writeTimeZone) {
    public static final Duration DEFAULT_MAX_GAP = Duration.ofMinutes(30);

    public SyncParameters {
        Objects.requireNonNull(cameraZone);
        Objects.requireNonNull(cameraOffset);
        Objects.requireNonNull(maxGap);
        if (maxGap.isNegative() || maxGap.isZero()) {
            throw new IllegalArgumentException("maxGap must be positive: " + maxGap);
        }
    }

    public static SyncParameters defaults(ZoneId cameraZone) {
        return new SyncParameters(cameraZone, Duration.ZERO, DEFAULT_MAX_GAP, true, false, true, false);
    }

    /**
     * Most frequent OffsetTimeOriginal of the files - fallback (usually the system zone) if none has one.
     */
    public static ZoneId suggestedCameraZone(List<MediaFile> files, ZoneId fallback) {
        final Map<ZoneOffset, Long> counts = files.stream()
                .map(MediaFile::timeOffset)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        return counts.entrySet().stream()
                // ties: deterministic by offset
                .max(Map.Entry.<ZoneOffset, Long>comparingByValue().thenComparing(Map.Entry.<ZoneOffset, Long>comparingByKey()))
                .<ZoneId>map(Map.Entry::getKey)
                .orElse(fallback);
    }
}
```

- [ ] **Step 4: Tests laufen lassen**

Run: `.\gradlew.bat test`
Expected: BUILD SUCCESSFUL, 71 Tests grün.

- [ ] **Step 5: Commit**

```powershell
& $git -C $app add src
& $git -C $app commit -m "Add camera clock conversion and sync parameters"
```

---

### Task 4: Dateien mit der Zeitleiste abgleichen

**Files:**
- Create: `src\main\java\tf\geotagger\sync\SyncResult.java`, `TrackSync.java`
- Test: `src\test\java\tf\geotagger\sync\TrackSyncTest.java`

**Interfaces:**
- Consumes:
  - `TrackTimeline.atOrBefore` und `atOrAfter`, `TrackPoint` (Tasks 1 und 2)
  - `CameraClock.toUtc` und `SyncParameters` (Task 3)
  - `TestMedia` (Task 3, nur Test)
- Produces:
  - `public record SyncResult(MediaFile file, Status status, Instant correctedTime, Double latitude, Double longitude, Double elevation, Duration gap)`
    - `enum Status { MATCHED, LARGE_GAP, NO_MATCH, NO_CAPTURE_TIME }`
    - `boolean hasPosition()` ist `true` bei `MATCHED` oder `LARGE_GAP`.
    - `gap` ist der Abstand zum nächsten echten Trackpunkt. Bei `NO_MATCH` ist er `null`, wenn es auf der zulässigen Seite gar keinen Punkt gibt. Bei `NO_CAPTURE_TIME` sind `correctedTime` und `gap` immer `null`.
  - `public final class TrackSync` mit `static List<SyncResult> match(List<MediaFile> files, TrackTimeline timeline, SyncParameters parameters)`. Das Ergebnis hat dieselbe Reihenfolge wie `files`, und die Dateien werden dabei nicht verändert.

- [ ] **Step 1: Fehlschlagenden Test schreiben**

`src\test\java\tf\geotagger\sync\TrackSyncTest.java`:

```java
package tf.geotagger.sync;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import tf.geotagger.model.MediaFile;
import tf.geotagger.track.TrackPoint;
import tf.geotagger.track.TrackTimeline;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class TrackSyncTest {
    private static final double DELTA = 1e-9;

    // camera clock in UTC keeps capture time = track time in most tests
    private static final SyncParameters UTC = SyncParameters.defaults(ZoneOffset.UTC);

    private static final TrackTimeline TIMELINE = new TrackTimeline(List.of(
            point("09:00:00", 46.0, 11.0, 1000.0),
            point("09:10:00", 46.1, 11.2, 1100.0),
            point("12:00:00", 47.0, 12.0, null)));

    private static TrackPoint point(String time, double latitude, double longitude, Double elevation) {
        return new TrackPoint(Instant.parse("2026-09-02T" + time + "Z"), latitude, longitude, elevation);
    }

    private static MediaFile shotAt(String time) {
        return TestMedia.image(LocalDateTime.parse("2026-09-02T" + time));
    }

    private static SyncResult match(MediaFile file, SyncParameters parameters) {
        return TrackSync.match(List.of(file), TIMELINE, parameters).get(0);
    }

    private static SyncParameters withInterpolate(boolean interpolate) {
        return new SyncParameters(ZoneOffset.UTC, Duration.ZERO, SyncParameters.DEFAULT_MAX_GAP, interpolate, false, true, false);
    }

    private static SyncParameters onlyPast() {
        return new SyncParameters(ZoneOffset.UTC, Duration.ZERO, SyncParameters.DEFAULT_MAX_GAP, true, true, true, false);
    }

    @Test
    void interpolatesBetweenNeighbours() {
        final MediaFile file = shotAt("09:05:00");
        final SyncResult result = match(file, UTC);

        assertSame(file, result.file());
        assertEquals(SyncResult.Status.MATCHED, result.status());
        assertEquals(Instant.parse("2026-09-02T09:05:00Z"), result.correctedTime());
        assertEquals(46.05, result.latitude(), DELTA);
        assertEquals(11.1, result.longitude(), DELTA);
        assertEquals(1050.0, result.elevation(), DELTA);
        assertEquals(Duration.ofMinutes(5), result.gap());
        assertFalse(file.isChanged());
    }

    @Test
    void exactTrackPointIsUsed() {
        final SyncResult result = match(shotAt("09:10:00"), UTC);

        assertEquals(46.1, result.latitude(), DELTA);
        assertEquals(11.2, result.longitude(), DELTA);
        assertEquals(1100.0, result.elevation(), DELTA);
        assertEquals(Duration.ZERO, result.gap());
    }

    @Test
    void nearestPointWithoutInterpolation() {
        assertEquals(46.0, match(shotAt("09:04:00"), withInterpolate(false)).latitude(), DELTA);
        assertEquals(46.1, match(shotAt("09:07:00"), withInterpolate(false)).latitude(), DELTA);
    }

    @Test
    void onlyNeighbourWithinMaxGapIsTakenAndLargeGapIsFlagged() {
        final SyncResult result = match(shotAt("09:30:00"), UTC);

        assertEquals(SyncResult.Status.LARGE_GAP, result.status());
        assertEquals(46.1, result.latitude(), DELTA);
        assertEquals(1100.0, result.elevation(), DELTA);
        assertEquals(Duration.ofMinutes(20), result.gap());
    }

    @Test
    void noMatchBeyondMaxGap() {
        final SyncResult result = match(shotAt("10:30:00"), UTC);

        assertEquals(SyncResult.Status.NO_MATCH, result.status());
        assertNull(result.latitude());
        assertEquals(Duration.ofMinutes(80), result.gap());
    }

    @Test
    void pointAfterIsUsedBeforeTheTrackStarts() {
        final SyncResult result = match(shotAt("08:50:00"), UTC);

        assertEquals(SyncResult.Status.MATCHED, result.status());
        assertEquals(46.0, result.latitude(), DELTA);
        assertEquals(Duration.ofMinutes(10), result.gap());
    }

    @Test
    void onlyPastPointsIgnoresLaterPoints() {
        final SyncResult beforeStart = match(shotAt("08:50:00"), onlyPast());
        assertEquals(SyncResult.Status.NO_MATCH, beforeStart.status());
        assertNull(beforeStart.gap());

        final SyncResult inside = match(shotAt("09:05:00"), onlyPast());
        assertEquals(46.0, inside.latitude(), DELTA);
        assertEquals(Duration.ofMinutes(5), inside.gap());
    }

    @Test
    void cameraZoneAndOffsetAreApplied() {
        // Berlin summer time = UTC+2, camera one minute ahead: 11:06 camera time = 09:05 UTC
        final SyncParameters berlin = new SyncParameters(ZoneId.of("Europe/Berlin"), Duration.ofMinutes(1),
                SyncParameters.DEFAULT_MAX_GAP, true, false, true, false);

        final SyncResult result = match(shotAt("11:06:00"), berlin);

        assertEquals(Instant.parse("2026-09-02T09:05:00Z"), result.correctedTime());
        assertEquals(46.05, result.latitude(), DELTA);
    }

    @Test
    void fileWithoutCaptureTimeCantBeMatched() {
        final SyncResult result = match(TestMedia.image(null), UTC);

        assertEquals(SyncResult.Status.NO_CAPTURE_TIME, result.status());
        assertNull(result.correctedTime());
        assertNull(result.gap());
    }

    @Test
    void missingElevationOfOneNeighbourTakesTheOther() {
        final TrackTimeline timeline = new TrackTimeline(List.of(
                point("09:00:00", 46.0, 11.0, null),
                point("09:10:00", 46.1, 11.2, 1100.0)));

        final SyncResult result = TrackSync.match(List.of(shotAt("09:05:00")), timeline, UTC).get(0);

        assertEquals(1100.0, result.elevation(), DELTA);
    }
}
```

- [ ] **Step 2: Test laufen lassen, Fehler erwarten**

Run: `.\gradlew.bat test --tests "tf.geotagger.sync.TrackSyncTest"`
Expected: FAIL, Kompilierfehler „cannot find symbol: class TrackSync“.

- [ ] **Step 3: Implementierung schreiben**

`src\main\java\tf\geotagger\sync\SyncResult.java`:

```java
package tf.geotagger.sync;

import java.time.Duration;
import java.time.Instant;
import tf.geotagger.model.MediaFile;

/**
 * Outcome of the track sync for one file - one row of the preview table in the sync dialog.
 *
 * @param correctedTime capture time in UTC after applying camera zone and offset, null without capture time
 * @param gap time to the nearest real track point (not the interpolated position), null if there is none
 */
public record SyncResult(MediaFile file, Status status, Instant correctedTime, Double latitude, Double longitude,
        Double elevation, Duration gap) {
    public enum Status {
        MATCHED,
        /** matched, but the nearest track point is more than half of the maximum gap away */
        LARGE_GAP,
        /** no track point within the maximum gap */
        NO_MATCH,
        NO_CAPTURE_TIME
    }

    public boolean hasPosition() {
        return status == Status.MATCHED || status == Status.LARGE_GAP;
    }
}
```

`src\main\java\tf\geotagger\sync\TrackSync.java`:

```java
package tf.geotagger.sync;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import tf.geotagger.model.MediaFile;
import tf.geotagger.sync.SyncResult.Status;
import tf.geotagger.track.TrackPoint;
import tf.geotagger.track.TrackTimeline;

/**
 * Assigns positions from GPX tracks to files by their capture time - see spec, section "Track-Sync-Dialog",
 * "Algorithmus". Pure logic, the files are not changed by match().
 */
public final class TrackSync {
    private TrackSync() {
    }

    public static List<SyncResult> match(List<MediaFile> files, TrackTimeline timeline, SyncParameters parameters) {
        return files.stream().map(file -> match(file, timeline, parameters)).toList();
    }

    private static SyncResult match(MediaFile file, TrackTimeline timeline, SyncParameters parameters) {
        if (file.captureTime() == null) {
            return new SyncResult(file, Status.NO_CAPTURE_TIME, null, null, null, null, null);
        }
        final Instant time = CameraClock.toUtc(file.captureTime(), parameters.cameraZone(), parameters.cameraOffset());

        final TrackPoint before = timeline.atOrBefore(time);
        final TrackPoint after = parameters.onlyPastPoints() ? null : timeline.atOrAfter(time);
        final Duration gapBefore = before == null ? null : Duration.between(before.time(), time);
        final Duration gapAfter = after == null ? null : Duration.between(time, after.time());
        final boolean beforeUsable = gapBefore != null && gapBefore.compareTo(parameters.maxGap()) <= 0;
        final boolean afterUsable = gapAfter != null && gapAfter.compareTo(parameters.maxGap()) <= 0;
        final Duration gap = shorter(gapBefore, gapAfter);

        if (!beforeUsable && !afterUsable) {
            return new SyncResult(file, Status.NO_MATCH, time, null, null, null, gap);
        }
        final Status status = gap.compareTo(parameters.maxGap().dividedBy(2)) > 0 ? Status.LARGE_GAP : Status.MATCHED;

        if (parameters.interpolate() && beforeUsable && afterUsable && !before.time().equals(after.time())) {
            final double fraction = (double) gapBefore.toMillis() / Duration.between(before.time(), after.time()).toMillis();
            return new SyncResult(file, status, time,
                    interpolate(before.latitude(), after.latitude(), fraction),
                    interpolate(before.longitude(), after.longitude(), fraction),
                    elevation(before.elevation(), after.elevation(), fraction),
                    gap);
        }

        final TrackPoint nearest = !afterUsable || (beforeUsable && gapBefore.compareTo(gapAfter) <= 0) ? before : after;
        return new SyncResult(file, status, time, nearest.latitude(), nearest.longitude(), nearest.elevation(), gap);
    }

    private static Duration shorter(Duration first, Duration second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        return first.compareTo(second) <= 0 ? first : second;
    }

    private static double interpolate(double from, double to, double fraction) {
        return from + (to - from) * fraction;
    }

    private static Double elevation(Double from, Double to, double fraction) {
        if (from == null) {
            return to;
        }
        if (to == null) {
            return from;
        }
        return interpolate(from, to, fraction);
    }
}
```

- [ ] **Step 4: Tests laufen lassen**

Run: `.\gradlew.bat test`
Expected: BUILD SUCCESSFUL, 81 Tests grün.

- [ ] **Step 5: Commit**

```powershell
& $git -C $app add src
& $git -C $app commit -m "Match capture times against GPX tracks"
```

---

### Task 5: Dateiauswahl und Übernahme der Ergebnisse

**Files:**
- Modify: `src\main\java\tf\geotagger\sync\TrackSync.java`
- Test: `src\test\java\tf\geotagger\sync\TrackSyncApplyTest.java`

**Interfaces:**
- Consumes: `TrackSync.match`, `SyncResult` und `SyncParameters` (Tasks 3 und 4), `MediaFile.set`, `hasCoordinates()` und `mediaType()` (Plan 1)
- Produces: in `TrackSync`
  - `static List<MediaFile> eligible(List<MediaFile> candidates, boolean overwriteExisting)`
    - Der Dialog übergibt als Kandidaten alle Dateien („Ohne Koordinaten“) oder die Auswahl („Auswahl“).
    - Ohne `overwriteExisting` bleiben Dateien mit Koordinaten draußen.
  - `static int apply(List<SyncResult> results, SyncParameters parameters)`
    - Setzt Breite/Länge als ausstehende Änderung, außerdem Höhe (falls `takeElevation` und vorhanden) und `TIME_OFFSET` (falls `writeTimeZone`, nur Bilder).
    - Betroffen sind nur Ergebnisse mit `hasPosition()`.
    - Rückgabe: Anzahl der geänderten Dateien.
    - Plan 3 fasst den Aufruf zu **einem** Undo-Schritt zusammen.

- [ ] **Step 1: Fehlschlagenden Test schreiben**

`src\test\java\tf\geotagger\sync\TrackSyncApplyTest.java`:

```java
package tf.geotagger.sync;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tf.geotagger.model.CaptureTime;
import tf.geotagger.model.Field;
import tf.geotagger.model.MediaFile;
import tf.geotagger.model.MediaType;
import tf.geotagger.track.TrackPoint;
import tf.geotagger.track.TrackTimeline;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrackSyncApplyTest {
    private static final double DELTA = 1e-9;
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    private static final TrackTimeline TIMELINE = new TrackTimeline(List.of(
            new TrackPoint(Instant.parse("2026-09-02T09:00:00Z"), 46.0, 11.0, 1000.0),
            new TrackPoint(Instant.parse("2026-09-02T09:10:00Z"), 46.1, 11.2, 1100.0)));

    // 11:05 Berlin summer time = 09:05 UTC
    private static final LocalDateTime INSIDE = LocalDateTime.parse("2026-09-02T11:05:00");
    private static final LocalDateTime OUTSIDE = LocalDateTime.parse("2026-09-02T18:00:00");

    private static SyncParameters parameters(boolean takeElevation, boolean writeTimeZone) {
        return new SyncParameters(BERLIN, Duration.ZERO, SyncParameters.DEFAULT_MAX_GAP, true, false, takeElevation, writeTimeZone);
    }

    @Test
    void eligibleSkipsFilesWithCoordinatesUnlessOverwriting() {
        final MediaFile without = TestMedia.image(INSIDE);
        final MediaFile with = TestMedia.file(MediaType.IMAGE, new CaptureTime(INSIDE, false),
                Map.of(Field.LATITUDE, 1.0, Field.LONGITUDE, 2.0));

        assertEquals(List.of(without), TrackSync.eligible(List.of(without, with), false));
        assertEquals(List.of(without, with), TrackSync.eligible(List.of(without, with), true));
    }

    @Test
    void matchedFilesGetPositionAndElevation() {
        final MediaFile matched = TestMedia.image(INSIDE);
        final MediaFile unmatched = TestMedia.image(OUTSIDE);
        final SyncParameters parameters = parameters(true, false);

        final int applied = TrackSync.apply(TrackSync.match(List.of(matched, unmatched), TIMELINE, parameters), parameters);

        assertEquals(1, applied);
        assertEquals(46.05, matched.latitude(), DELTA);
        assertEquals(11.1, matched.longitude(), DELTA);
        assertEquals(1050.0, matched.altitude(), DELTA);
        assertNull(matched.timeOffset());
        assertFalse(unmatched.isChanged());
    }

    @Test
    void elevationOnlyWhenRequested() {
        final MediaFile matched = TestMedia.image(INSIDE);
        final SyncParameters parameters = parameters(false, false);

        TrackSync.apply(TrackSync.match(List.of(matched), TIMELINE, parameters), parameters);

        assertTrue(matched.hasCoordinates());
        assertNull(matched.altitude());
    }

    @Test
    void timeZoneIsWrittenForImagesOnly() {
        final MediaFile image = TestMedia.image(INSIDE);
        final MediaFile video = TestMedia.file(MediaType.VIDEO, new CaptureTime(INSIDE, false), Map.of());
        final SyncParameters parameters = parameters(true, true);

        TrackSync.apply(TrackSync.match(List.of(image, video), TIMELINE, parameters), parameters);

        assertEquals(ZoneOffset.ofHours(2), image.timeOffset());
        assertTrue(video.hasCoordinates());
        assertNull(video.timeOffset());
    }
}
```

- [ ] **Step 2: Test laufen lassen, Fehler erwarten**

Run: `.\gradlew.bat test --tests "tf.geotagger.sync.TrackSyncApplyTest"`
Expected: FAIL, Kompilierfehler „cannot find symbol: method eligible“.

- [ ] **Step 3: Implementierung ergänzen**

In `TrackSync.java` kommen diese Imports dazu:

```java
import tf.geotagger.model.Field;
import tf.geotagger.model.MediaType;
```

Diese Methoden kommen nach `match(List<MediaFile>, TrackTimeline, SyncParameters)`:

```java
    /**
     * Files the sync should handle: candidates are all files ("Ohne Koordinaten") or the selection ("Auswahl").
     */
    public static List<MediaFile> eligible(List<MediaFile> candidates, boolean overwriteExisting) {
        return candidates.stream().filter(file -> overwriteExisting || !file.hasCoordinates()).toList();
    }

    /**
     * Sets the results with a position as pending changes - nothing is written to the files.
     *
     * @return number of changed files
     */
    public static int apply(List<SyncResult> results, SyncParameters parameters) {
        int applied = 0;
        for (SyncResult result : results) {
            if (!result.hasPosition()) {
                continue;
            }
            final MediaFile file = result.file();
            file.set(Field.LATITUDE, result.latitude());
            file.set(Field.LONGITUDE, result.longitude());
            if (parameters.takeElevation() && result.elevation() != null) {
                file.set(Field.ALTITUDE, result.elevation());
            }
            // videos get no time zone - see spec, section "Felder"
            if (parameters.writeTimeZone() && file.mediaType() == MediaType.IMAGE) {
                file.set(Field.TIME_OFFSET, parameters.cameraZone().getRules().getOffset(result.correctedTime()));
            }
            applied++;
        }
        return applied;
    }
```

- [ ] **Step 4: Tests laufen lassen**

Run: `.\gradlew.bat test`
Expected: BUILD SUCCESSFUL, 85 Tests grün und keine Compilerwarnungen.

- [ ] **Step 5: Commit**

```powershell
& $git -C $app add src
& $git -C $app commit -m "Select eligible files and apply track sync results"
```

---

## Abschluss von Plan 2

- `.\gradlew.bat build` in `$app`: Alle 85 Tests sind grün, auch die mit den echten Musterdateien.
- Nicht in diesem Plan:
  - Der Dialog selbst folgt in Plan 6.
  - Das Laden geladener Tracks aus der Track-Liste und die Anzeige auf der Karte folgen in Plan 4.
  - Undo folgt in Plan 3.
- Danach Plan 3 (Hauptfenster) auf Basis des tatsächlich entstandenen Codes schreiben.

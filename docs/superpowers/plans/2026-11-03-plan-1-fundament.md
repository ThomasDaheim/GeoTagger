# GeoTagger Plan 1: Fundament (ExifTool, Modell, Metadaten) – Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use anthropic-skills:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Am Ende von Plan 1 können Metadaten ohne UI über ExifTool gelesen und geschrieben werden, und zwar so, wie es die Spec verlangt. Alle Zusicherungen aus den Schreibregeln sind durch Tests abgesichert.

**Architecture:**
- Das neue Library-Repo **GeoHelper** enthält einen ExifTool-Wrapper (`tf.geo.exiftool`). Er hält einen einzigen dauerhaft laufenden Prozess (`-stay_open`).
- Das App-Repo **GeoTagger** enthält das Modell (`tf.geotagger.model`) und die Übersetzung zwischen Modell und ExifTool-Tags (`tf.geotagger.metadata`).
- GeoTagger bezieht GeoHelper über mavenLocal, so wie GPXEditor JavaHelper bezieht.

**Tech Stack:** Java 25 (Gradle-Toolchain), Gradle 9.2.1 (Wrapper aus GPXEditor), Jackson 2.21.2 (nur in GeoHelper), JUnit 5.13.0, ExifTool (extern installiert).

Spec: [2026-11-03-geotagger-design.md](../specs/2026-11-03-geotagger-design.md)

## Einordnung: Fahrplan aller Pläne

Die Spec umfasst mehrere Teilsysteme. Sie wird deshalb in Pläne aufgeteilt, und jeder Plan liefert für sich testbare Software. Jeder weitere Plan wird erst geschrieben, wenn der vorige umgesetzt ist, damit er auf dem echten Code aufbaut.

| Plan | Inhalt | Ergebnis |
|---|---|---|
| **1 – Fundament** (dieser) | GeoHelper-Repo mit ExifTool-Wrapper. GeoTagger-Repo mit Modell, Formatprofilen, Lesen und Schreiben. | Getestete Kernlogik ohne UI |
| 2 – Track-Sync-Logik | GPX lesen (gpx-parser), Zeitzone und Offset, Interpolation, maximale Differenz, keine Zukunft | Getestete Sync-Logik ohne UI |
| 3 – Hauptfenster | JavaFX-App: Ordner öffnen (inkl. Unterordner), Liste, Filter, große Vorschau, Reiter „Details“ und „Bild-Info“, Undo/Redo (JavaHelper), Speichern, Einstellungen (ExifTool-Pfad). Das Modell bekommt dabei Beobachtbarkeit für die UI. | Bedienbare App ohne Karte |
| 4 – Karte | `tf.geo.map` aus GPXEditor nach GeoHelper portieren. Marker mit Clustering, Drag & Drop, Rechtsklick, Track-Liste und Track-Anzeige. | Koordinaten über die Karte setzen |
| 5 – Anreichern | `tf.geo.service`: Mapbox, GeoNames, openrouteservice, SRTM, Zeitzone. Dazu `enrich` mit Raster-Cache und Drosselung. | Ort, Höhe und Zeitzone ermitteln |
| 6 – Sync-Dialog | Dialog mit Live-Vorschau auf Basis von Plan 2 | Vollständiger Workflow |

## Global Constraints

- Java 25 über die Gradle-Toolchain, Gradle 9.2.1 über den Wrapper aus GPXEditor.
- Gruppe/Version:
  - GeoHelper: `tf.GeoHelper:GeoHelper:0.1`
  - GeoTagger: `tf.GeoTagger`, `0.1`
  - Versionen werden **nicht** pro Änderung erhöht. Das macht nur der Nutzer bei Releases.
- `mavenLocal()` liefert nur eigene Libraries (`content { includeGroup ... }`), alles andere kommt aus `mavenCentral()`.
- Paketpräfixe: `tf.geo.*` (GeoHelper), `tf.geotagger.*` (GeoTagger).
- Code und Kommentare sind auf Englisch, wie in GPXEditor und JavaHelper. Doku und Plan sind auf Deutsch.
- Compilerwarnungen gelten auch in `src/test` als Fehler, die zu beheben sind (`-Xlint:unchecked`, `-Xlint:deprecation`).
- Arbeitsbranch ist jeweils der durch `git init` entstandene Branch (`master`). Es gibt keine Feature-Branches und keine PRs.
- **Git** ist nicht im PATH. In jeder PowerShell-Sitzung zuerst:
  ```powershell
  $git = 'C:\Users\thomas\AppData\Local\GitHubDesktop\app-3.6.7\resources\app\git\cmd\git.exe'
  ```
  Falls der Pfad fehlt: `%LOCALAPPDATA%\GitHubDesktop\app-*\resources\app\git\cmd\git.exe`.
- **Repo-Variablen**, die in jeder PowerShell-Sitzung zusätzlich gesetzt werden müssen (die Commit-Schritte nutzen sie):
  ```powershell
  $root = 'D:\Thomas\Development\NetBeansProjects\GeoHelper'
  $app  = 'D:\Thomas\Development\NetBeansProjects\GeoTagger'
  $gpx  = 'D:\Thomas\Development\NetBeansProjects\GPXEditor'
  ```
- Gradle-Befehle laufen im jeweiligen Repo-Ordner (`Set-Location $root` bzw. `Set-Location $app`).
- **ExifTool** für Tests:
  - Gesucht wird über die Umgebungsvariable `EXIFTOOL_PATH`, sonst im `PATH`.
  - Auf dem Entwicklungsrechner liegt Version 12.57 aus GeoSetter unter `C:\Users\Thomas\AppData\Roaming\GeoSetter_beta\tools\exiftool.exe` im PATH.
  - Ohne ExifTool werden die betroffenen Tests übersprungen (`Assumptions`), sie schlagen nicht fehl.
- **Schreibregeln aus der Spec** (verbindlich):
  - Direkt in die Datei, wo ExifTool das Format schreiben kann. Sonst eine Sidecar-Datei `<name ohne Endung>.xmp`.
  - Kein `-overwrite_original`, damit `*_original` entsteht. Dazu `-P`, und das Erstellungsdatum wird zusätzlich selbst wiederhergestellt.
  - Die Aufnahmezeit wird nie verändert. Nur die Tags aus der Feldtabelle werden geschrieben, alle anderen (z. B. `GPSImgDirection`) bleiben erhalten.
  - IPTC nur bei JPEG und TIFF.
  - `OffsetTimeOriginal` nur bei Formaten mit EXIF, also nicht bei Videos und nicht bei Sidecar-Dateien.

---

## Dateistruktur

```
D:\Thomas\Development\NetBeansProjects\GeoHelper\
  build.gradle, settings.gradle, .gitignore, gradlew, gradlew.bat, gradle\wrapper\*
  src\main\java\tf\geo\exiftool\
    ExifTool.java            -- stay_open process: execute, version, readJson, extractBinary, close
    ExecResult.java          -- stdout/stderr lines of one command, errors/warnings helpers
    ExifToolException.java   -- process could not be started / crashed / timed out
  src\test\java\tf\geo\exiftool\
    ExifToolTestSupport.java -- locate ExifTool (EXIFTOOL_PATH / PATH), skip tests otherwise
    ExifToolTest.java        -- process handling
    ExifToolStartTest.java   -- missing executable
    ExifToolReadTest.java    -- readJson + extractBinary

D:\Thomas\Development\NetBeansProjects\GeoTagger\
  build.gradle, settings.gradle, .gitignore, gradlew, gradlew.bat, gradle\wrapper\*
  src\main\java\tf\geotagger\model\
    Field.java               -- editable metadata fields + value type
    MediaType.java           -- IMAGE / VIDEO
    CaptureTime.java         -- capture time as found in the file (local or UTC)
    Snapshot.java            -- immutable values as last read from the file
    MediaFile.java           -- original snapshot + pending changes + derived status
  src\main\java\tf\geotagger\metadata\
    FormatProfile.java       -- which tag families a file extension gets, sidecar naming
    ReadTags.java            -- tag names requested from ExifTool
    MetadataParser.java      -- ExifTool JSON entry -> values / capture time
    WriteArgsBuilder.java    -- pending changes -> ExifTool assignment arguments
    MetadataReader.java      -- read files in chunks of 100, sidecar overlay
    MetadataWriter.java      -- save pending changes, restore file times, re-read
    SaveResult.java          -- per-file outcome of a save run
  src\test\java\tf\geotagger\model\MediaFileTest.java
  src\test\java\tf\geotagger\metadata\
    ExifToolTestSupport.java -- same lookup as in GeoHelper (test code is not shared)
    TestFiles.java           -- create small JPEG/PNG/TIFF files with ImageIO
    FormatProfileTest.java, MetadataParserTest.java, WriteArgsBuilderTest.java
    MetadataReaderTest.java, MetadataWriterTest.java
  src\test\resources\samples\README.md  -- where the user drops real camera files
```

---

### Task 1: GeoHelper-Repo und ExifTool-Prozess

**Files:**
- Create: `D:\Thomas\Development\NetBeansProjects\GeoHelper\settings.gradle`
- Create: `D:\Thomas\Development\NetBeansProjects\GeoHelper\build.gradle`
- Create: `D:\Thomas\Development\NetBeansProjects\GeoHelper\.gitignore`
- Copy: Gradle-Wrapper aus `D:\Thomas\Development\NetBeansProjects\GPXEditor`
- Create: `src\main\java\tf\geo\exiftool\ExifToolException.java`, `ExecResult.java`, `ExifTool.java`
- Test: `src\test\java\tf\geo\exiftool\ExifToolTestSupport.java`, `ExifToolTest.java`, `ExifToolStartTest.java`

**Interfaces:**
- Consumes: –
- Produces:
  - `public class ExifTool implements AutoCloseable`
    - `ExifTool(Path executable)`
    - `ExifTool(Path executable, Duration timeout)`
    - `synchronized ExecResult execute(List<String> args)`
    - `String version()`
    - `void close()`
    - package-private `synchronized Process process()`
  - `public record ExecResult(List<String> stdout, List<String> stderr)` mit `String stdoutText()`, `List<String> errors()`, `List<String> warnings()` und `boolean hasErrors()`
  - `public class ExifToolException extends RuntimeException`

- [ ] **Step 1: Repo anlegen, Wrapper kopieren**

```powershell
$root = 'D:\Thomas\Development\NetBeansProjects\GeoHelper'
$gpx  = 'D:\Thomas\Development\NetBeansProjects\GPXEditor'
New-Item -ItemType Directory -Force "$root\gradle\wrapper", "$root\src\main\java\tf\geo\exiftool", "$root\src\test\java\tf\geo\exiftool" | Out-Null
Copy-Item "$gpx\gradlew", "$gpx\gradlew.bat" $root
Copy-Item "$gpx\gradle\wrapper\gradle-wrapper.jar", "$gpx\gradle\wrapper\gradle-wrapper.properties" "$root\gradle\wrapper"
& $git init $root
```

- [ ] **Step 2: Build-Dateien schreiben**

`settings.gradle`:

```groovy
rootProject.name = 'GeoHelper'
```

`build.gradle`:

```groovy
// =====================================================================================================================
// GeoHelper build - geo related helpers (ExifTool, map, geo services) for ThomasDaheim Java applications
// =====================================================================================================================
//
//   gradlew build  -> compile, test, jar + publishToMavenLocal (consumers use tf.GeoHelper:GeoHelper:<version>)
//   gradlew publishToMavenLocal -> only publish, without running the tests
//
// Tests that need ExifTool look for it in the environment variable EXIFTOOL_PATH and then on the PATH -
// without ExifTool they are skipped.
// =====================================================================================================================

plugins {
    id 'java-library'
    id 'maven-publish'
}

group = 'tf.GeoHelper'
version = '0.1'
description = 'Geo helpers (ExifTool, map, geo services) for ThomasDaheim Java applications'

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
    withSourcesJar()
}

repositories {
    mavenCentral()
}

dependencies {
    // BOM: one version for all jackson modules - same as GPXEditor
    implementation platform('com.fasterxml.jackson:jackson-bom:2.21.2')
    implementation 'com.fasterxml.jackson.core:jackson-databind'

    testImplementation platform('org.junit:junit-bom:5.13.0')
    testImplementation 'org.junit.jupiter:junit-jupiter'
    // Gradle 9 doesn't provide the launcher automatically anymore
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

tasks.withType(JavaCompile).configureEach {
    options.encoding = 'UTF-8'
    options.compilerArgs += ['-Xlint:unchecked', '-Xlint:deprecation']
}

tasks.named('test', Test) {
    useJUnitPlatform()
    systemProperty 'java.awt.headless', 'true'
    testLogging.showStandardStreams = true
}

publishing {
    publications {
        maven(MavenPublication) {
            from components.java
        }
    }
}

tasks.named('build') {
    finalizedBy 'publishToMavenLocal'
}
```

`.gitignore`:

```
.gradle/
build/
.idea/
*.iml
.nb-gradle/
nbproject/private/
```

- [ ] **Step 3: Test-Hilfe und fehlschlagende Tests schreiben**

`src\test\java\tf\geo\exiftool\ExifToolTestSupport.java`:

```java
package tf.geo.exiftool;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Assumptions;

/**
 * Finds the ExifTool executable for tests: environment variable EXIFTOOL_PATH first, then the PATH.
 * Tests call require() and are skipped if no ExifTool is installed.
 */
final class ExifToolTestSupport {
    private ExifToolTestSupport() {
    }

    static Optional<Path> locate() {
        final String configured = System.getenv("EXIFTOOL_PATH");
        if (configured != null && !configured.isBlank()) {
            final Path path = Path.of(configured.trim());
            return Files.isRegularFile(path) ? Optional.of(path) : Optional.empty();
        }
        final String pathVariable = System.getenv("PATH");
        if (pathVariable == null) {
            return Optional.empty();
        }
        for (String dir : pathVariable.split(File.pathSeparator)) {
            for (String name : List.of("exiftool.exe", "exiftool")) {
                try {
                    final Path candidate = Path.of(dir.trim().replace("\"", ""), name);
                    if (Files.isRegularFile(candidate)) {
                        return Optional.of(candidate);
                    }
                } catch (InvalidPathException ex) {
                    // broken PATH entry - ignore
                }
            }
        }
        return Optional.empty();
    }

    static Path require() {
        final Optional<Path> exifTool = locate();
        Assumptions.assumeTrue(exifTool.isPresent(), "ExifTool not found - set EXIFTOOL_PATH or put exiftool on the PATH");
        return exifTool.get();
    }
}
```

`src\test\java\tf\geo\exiftool\ExifToolTest.java`:

```java
package tf.geo.exiftool;

import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExifToolTest {
    private static ExifTool exifTool;

    @BeforeAll
    static void start() {
        exifTool = new ExifTool(ExifToolTestSupport.require());
    }

    @AfterAll
    static void stop() {
        if (exifTool != null) {
            exifTool.close();
        }
    }

    @Test
    void versionIsReported() {
        assertTrue(exifTool.version().matches("\\d+\\.\\d+.*"), exifTool.version());
    }

    @Test
    void processIsReusedForSeveralCommands() {
        exifTool.version();
        final Process first = exifTool.process();
        exifTool.version();
        assertSame(first, exifTool.process());
    }

    @Test
    void crashedProcessIsRestarted() throws InterruptedException {
        exifTool.version();
        exifTool.process().destroyForcibly().waitFor();
        assertTrue(exifTool.version().matches("\\d+\\.\\d+.*"));
    }

    @Test
    void errorsAreReportedInStderr() {
        final ExecResult result = exifTool.execute(List.of("C:/does/not/exist.jpg"));
        assertTrue(result.hasErrors(), result.toString());
    }

    @Test
    void commandWithoutErrorsHasNone() {
        final ExecResult result = exifTool.execute(List.of("-ver"));
        assertFalse(result.hasErrors(), result.toString());
    }
}
```

`src\test\java\tf\geo\exiftool\ExifToolStartTest.java`:

```java
package tf.geo.exiftool;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExifToolStartTest {
    @Test
    void missingExecutableFails() {
        try (ExifTool broken = new ExifTool(Path.of("C:/does/not/exist/exiftool.exe"))) {
            assertThrows(ExifToolException.class, broken::version);
        }
    }
}
```

- [ ] **Step 4: Tests laufen lassen, Kompilierfehler erwarten**

Run: `.\gradlew.bat test` (im Ordner `D:\Thomas\Development\NetBeansProjects\GeoHelper`)
Expected: FAIL, `compileTestJava` meldet „cannot find symbol: class ExifTool“.

- [ ] **Step 5: Implementierung schreiben**

`src\main\java\tf\geo\exiftool\ExifToolException.java`:

```java
package tf.geo.exiftool;

/**
 * ExifTool could not be started, crashed or didn't answer in time.
 * Errors that ExifTool reports for single files are not exceptions - they are part of the ExecResult.
 */
public class ExifToolException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ExifToolException(String message) {
        super(message);
    }

    public ExifToolException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

`src\main\java\tf\geo\exiftool\ExecResult.java`:

```java
package tf.geo.exiftool;

import java.util.List;

/**
 * Output of one ExifTool command, split into lines - without the {ready} markers.
 */
public record ExecResult(List<String> stdout, List<String> stderr) {
    public ExecResult {
        stdout = List.copyOf(stdout);
        stderr = List.copyOf(stderr);
    }

    public String stdoutText() {
        return String.join("\n", stdout);
    }

    public List<String> errors() {
        return stderr.stream().filter(line -> line.startsWith("Error")).toList();
    }

    public List<String> warnings() {
        return stderr.stream().filter(line -> line.startsWith("Warning")).toList();
    }

    public boolean hasErrors() {
        return !errors().isEmpty();
    }
}
```

`src\main\java\tf\geo\exiftool\ExifTool.java`:

```java
package tf.geo.exiftool;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * One long running ExifTool process ("-stay_open True -@ -") that executes commands one after the other.
 *
 * Protocol: the arguments of a command are written to stdin one per line, followed by "-execute<n>".
 * ExifTool ends its stdout output with "{ready<n>}". "-echo4 {ready<n>}" prints the same marker to stderr after
 * the command, so both streams can be read up to the end of the command.
 *
 * If the process crashed or doesn't answer within the timeout, it is restarted and the command is retried once.
 */
public class ExifTool implements AutoCloseable {
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);
    // put into the line queues when a stream ends - can't occur in ExifTool output
    private static final String END_OF_STREAM = "\u0000EOS";

    private final Path executable;
    private final Duration timeout;

    private Process process;
    private BufferedWriter stdin;
    private BlockingQueue<String> stdoutLines;
    private BlockingQueue<String> stderrLines;
    private int commandCounter = 0;

    public ExifTool(Path executable) {
        this(executable, DEFAULT_TIMEOUT);
    }

    public ExifTool(Path executable, Duration timeout) {
        this.executable = Objects.requireNonNull(executable);
        this.timeout = Objects.requireNonNull(timeout);
    }

    public synchronized ExecResult execute(List<String> args) {
        try {
            return executeOnce(args);
        } catch (ExifToolException ex) {
            // process crashed or hangs: restart and retry once
            stopQuietly();
            return executeOnce(args);
        }
    }

    public String version() {
        final ExecResult result = execute(List.of("-ver"));
        if (result.stdout().isEmpty()) {
            throw new ExifToolException("ExifTool didn't report a version: " + result);
        }
        return result.stdout().get(0).trim();
    }

    @Override
    public synchronized void close() {
        if (process == null) {
            return;
        }
        if (process.isAlive()) {
            try {
                stdin.write("-stay_open\nFalse\n");
                stdin.flush();
                if (!process.waitFor(5, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            } catch (IOException ex) {
                process.destroyForcibly();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
        }
        process = null;
    }

    // for tests only
    synchronized Process process() {
        return process;
    }

    private ExecResult executeOnce(List<String> args) {
        ensureStarted();
        final int id = ++commandCounter;
        final String marker = "{ready" + id + "}";
        try {
            for (String arg : args) {
                if (arg.indexOf('\n') >= 0 || arg.indexOf('\r') >= 0) {
                    throw new IllegalArgumentException("ExifTool arguments must not contain line breaks: " + arg);
                }
                stdin.write(arg);
                stdin.write('\n');
            }
            stdin.write("-echo4\n" + marker + "\n-execute" + id + "\n");
            stdin.flush();
        } catch (IOException ex) {
            throw new ExifToolException("Can't send command to ExifTool", ex);
        }
        final long deadline = System.nanoTime() + timeout.toNanos();
        final List<String> out = readUntil(stdoutLines, marker, deadline);
        final List<String> err = readUntil(stderrLines, marker, deadline);
        return new ExecResult(out, err);
    }

    private List<String> readUntil(BlockingQueue<String> lines, String marker, long deadline) {
        final List<String> result = new ArrayList<>();
        while (true) {
            final String line;
            try {
                line = lines.poll(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new ExifToolException("Interrupted while waiting for ExifTool", ex);
            }
            if (line == null) {
                throw new ExifToolException("ExifTool didn't answer within " + timeout);
            }
            if (END_OF_STREAM.equals(line)) {
                throw new ExifToolException("ExifTool terminated unexpectedly");
            }
            if (line.equals(marker)) {
                return result;
            }
            result.add(line);
        }
    }

    private void ensureStarted() {
        if (process != null && process.isAlive()) {
            return;
        }
        stopQuietly();
        // -common_args: filenames are passed as UTF-8 (umlauts in Windows paths)
        final List<String> command = List.of(executable.toString(),
                "-stay_open", "True", "-@", "-", "-common_args", "-charset", "filename=utf8");
        try {
            process = new ProcessBuilder(command).start();
        } catch (IOException ex) {
            process = null;
            throw new ExifToolException("Can't start ExifTool " + executable, ex);
        }
        stdin = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        stdoutLines = pump(process.getInputStream(), "stdout");
        stderrLines = pump(process.getErrorStream(), "stderr");
    }

    private void stopQuietly() {
        if (process != null) {
            process.destroyForcibly();
            process = null;
        }
    }

    private static BlockingQueue<String> pump(InputStream stream, String name) {
        final BlockingQueue<String> queue = new LinkedBlockingQueue<>();
        final Thread thread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    queue.add(line);
                }
            } catch (IOException ex) {
                // stream closed - process ended
            } finally {
                queue.add(END_OF_STREAM);
            }
        }, "ExifTool-" + name);
        thread.setDaemon(true);
        thread.start();
        return queue;
    }
}
```

- [ ] **Step 6: Tests laufen lassen**

Run: `.\gradlew.bat test`
Expected: BUILD SUCCESSFUL, 6 Tests grün. Ohne ExifTool laufen nur 1 Test grün und 5 werden übersprungen.

- [ ] **Step 7: Commit**

```powershell
& $git -C $root add .gitignore build.gradle settings.gradle gradlew gradlew.bat gradle src
& $git -C $root commit -m "Add GeoHelper project with stay_open ExifTool process"
```

---

### Task 2: ExifTool lesen (JSON) und Binärdaten extrahieren, nach mavenLocal veröffentlichen

**Files:**
- Modify: `src\main\java\tf\geo\exiftool\ExifTool.java`
- Test: `src\test\java\tf\geo\exiftool\ExifToolReadTest.java`

**Interfaces:**
- Consumes: `ExifTool.execute(List<String>)` aus Task 1
- Produces:
  - `public List<Map<String, Object>> readJson(List<Path> files, List<String> tags)`
    - Die Aufrufoptionen sind `-json -n -G1`, die Schlüssel haben die Form `Gruppe1:Tag` plus `SourceFile`.
    - Leere Dateiliste ergibt eine leere Liste.
    - Unlesbare Dateien fehlen in der Antwort oder liefern nur `SourceFile` und einen Fehler-Eintrag.
  - `public boolean extractBinary(Path file, String tag, Path target)` schreibt das Binär-Tag nach `target`. Rückgabe `false`, wenn das Tag fehlt.
  - Artefakt `tf.GeoHelper:GeoHelper:0.1` in `~/.m2`.

- [ ] **Step 1: Fehlschlagenden Test schreiben**

`src\test\java\tf\geo\exiftool\ExifToolReadTest.java`:

```java
package tf.geo.exiftool;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExifToolReadTest {
    private static ExifTool exifTool;

    @TempDir
    Path dir;

    @BeforeAll
    static void start() {
        exifTool = new ExifTool(ExifToolTestSupport.require());
    }

    @AfterAll
    static void stop() {
        if (exifTool != null) {
            exifTool.close();
        }
    }

    private static Path jpeg(Path file) throws IOException {
        ImageIO.write(new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB), "jpg", file.toFile());
        return file;
    }

    @Test
    void readsTagsWithUmlautsInValueAndPath() throws IOException {
        final Path file = jpeg(dir.resolve("Bild_ä.jpg"));
        exifTool.execute(List.of("-overwrite_original", "-XMP-photoshop:City=Völs", "-GPS:GPSLatitude#=46.5",
                "-GPS:GPSLatitudeRef#=S", file.toString()));

        final List<Map<String, Object>> entries = exifTool.readJson(List.of(file),
                List.of("XMP-photoshop:City", "Composite:GPSLatitude"));

        assertEquals(1, entries.size());
        final Map<String, Object> entry = entries.get(0);
        assertEquals(file.toAbsolutePath().normalize(), Path.of(entry.get("SourceFile").toString()).toAbsolutePath().normalize());
        assertEquals("Völs", entry.get("XMP-photoshop:City"));
        assertEquals(-46.5, ((Number) entry.get("Composite:GPSLatitude")).doubleValue(), 1e-9);
    }

    @Test
    void emptyFileListGivesEmptyResult() {
        assertTrue(exifTool.readJson(List.of(), List.of("XMP-photoshop:City")).isEmpty());
    }

    @Test
    void extractsBinaryTag() throws IOException {
        final Path thumb = jpeg(dir.resolve("thumb.jpg"));
        final Path file = jpeg(dir.resolve("image.jpg"));
        exifTool.execute(List.of("-overwrite_original", "-ThumbnailImage<=" + thumb, file.toString()));

        final Path target = dir.resolve("extracted.jpg");
        assertTrue(exifTool.extractBinary(file, "ThumbnailImage", target));
        assertArrayEquals(Files.readAllBytes(thumb), Files.readAllBytes(target));
    }

    @Test
    void missingBinaryTagGivesFalse() throws IOException {
        final Path file = jpeg(dir.resolve("plain.jpg"));
        assertFalse(exifTool.extractBinary(file, "PreviewImage", dir.resolve("none.jpg")));
    }
}
```

- [ ] **Step 2: Test laufen lassen, Fehler erwarten**

Run: `.\gradlew.bat test --tests "tf.geo.exiftool.ExifToolReadTest"`
Expected: FAIL, Kompilierfehler „cannot find symbol: method readJson“.

- [ ] **Step 3: Implementierung ergänzen**

In `ExifTool.java` kommen diese Imports dazu:

```java
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.Map;
```

Diese Konstante kommt unter `END_OF_STREAM`:

```java
    private static final ObjectMapper MAPPER = new ObjectMapper();
```

Diese Methoden kommen nach `version()`:

```java
    /**
     * Reads the given tags of all files with "-json -n -G1": numbers are numeric, keys are "Group1:Tag".
     * Files ExifTool can't read are missing in the result or only carry an error entry.
     */
    public List<Map<String, Object>> readJson(List<Path> files, List<String> tags) {
        if (files.isEmpty()) {
            return List.of();
        }
        final List<String> args = new ArrayList<>(List.of("-json", "-n", "-G1"));
        tags.forEach(tag -> args.add("-" + tag));
        files.forEach(file -> args.add(file.toAbsolutePath().toString()));
        final String json = execute(args).stdoutText().trim();
        if (json.isEmpty()) {
            return List.of();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (JsonProcessingException ex) {
            throw new ExifToolException("Can't parse ExifTool output", ex);
        }
    }

    /**
     * Writes a binary tag (e.g. PreviewImage, JpgFromRaw, ThumbnailImage) to target.
     * Returns false if the file doesn't contain the tag.
     */
    public boolean extractBinary(Path file, String tag, Path target) {
        try {
            Files.deleteIfExists(target);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        // -W! writes the output to the file named by the format string ("%" needs escaping there)
        final String format = target.toAbsolutePath().toString().replace("%", "%%");
        final ExecResult result = execute(List.of("-b", "-" + tag, "-W!", format, file.toAbsolutePath().toString()));
        return !result.hasErrors() && Files.isRegularFile(target);
    }
```

- [ ] **Step 4: Alle Tests laufen lassen**

Run: `.\gradlew.bat test`
Expected: BUILD SUCCESSFUL, 10 Tests grün.

- [ ] **Step 5: Nach mavenLocal veröffentlichen und prüfen**

Run: `.\gradlew.bat build`
Expected:
- BUILD SUCCESSFUL.
- `Test-Path "$env:USERPROFILE\.m2\repository\tf\GeoHelper\GeoHelper\0.1\GeoHelper-0.1.jar"` ergibt `True`.

- [ ] **Step 6: Commit**

```powershell
& $git -C $root add src
& $git -C $root commit -m "ExifTool: read tags as JSON and extract binary tags"
```

---

### Task 3: GeoTagger-Projekt und Modell

**Files:**
- Create in `D:\Thomas\Development\NetBeansProjects\GeoTagger`: `settings.gradle`, `build.gradle`, `.gitignore` und den Gradle-Wrapper. Das Repo existiert schon, mit Spec und Plan.
- Create: `src\main\java\tf\geotagger\model\Field.java`, `MediaType.java`, `CaptureTime.java`, `Snapshot.java`, `MediaFile.java`
- Test: `src\test\java\tf\geotagger\model\MediaFileTest.java`

**Interfaces:**
- Consumes: –
- Produces:
  - `public enum Field`
    - Konstanten: `LATITUDE, LONGITUDE, ALTITUDE` (`Double`), `COUNTRY_CODE, COUNTRY, STATE, CITY, SUBLOCATION` (`String`), `TIME_OFFSET` (`java.time.ZoneOffset`)
    - `Class<?> type()`
    - `static final Set<Field> PLACE_FIELDS`
  - `public enum MediaType { IMAGE, VIDEO }`
  - `public record CaptureTime(LocalDateTime dateTime, boolean utc)`
  - `public record Snapshot(CaptureTime captureTime, FileTime lastModified, Map<Field, Object> values)`
    - `captureTime` darf `null` sein.
    - `values` wird kopiert, ist danach unveränderlich und enthält keine `null`-Werte.
  - `public final class MediaFile`
    - `MediaFile(Path path, MediaType mediaType, Snapshot original)`
    - Zugriff: `path()`, `mediaType()`, `original()`, `captureTime()`
    - Werte: `Object value(Field)`, `Double latitude()`, `Double longitude()`, `Double altitude()`, `String text(Field)`, `ZoneOffset timeOffset()`
    - Ändern: `void set(Field, Object)`
    - Status: `Map<Field, Object> pendingChanges()` (darf `null` als „löschen“ enthalten), `isChanged()`, `hasCoordinates()`, `hasPlace()`, `hasAltitude()`
    - Abschluss: `discardChanges()`, `replaceOriginal(Snapshot)`

- [ ] **Step 1: Projekt anlegen**

```powershell
$app = 'D:\Thomas\Development\NetBeansProjects\GeoTagger'
$gpx = 'D:\Thomas\Development\NetBeansProjects\GPXEditor'
New-Item -ItemType Directory -Force "$app\gradle\wrapper", "$app\src\main\java\tf\geotagger\model", "$app\src\main\java\tf\geotagger\metadata", "$app\src\test\java\tf\geotagger\model", "$app\src\test\java\tf\geotagger\metadata", "$app\src\test\resources\samples" | Out-Null
Copy-Item "$gpx\gradlew", "$gpx\gradlew.bat" $app
Copy-Item "$gpx\gradle\wrapper\gradle-wrapper.jar", "$gpx\gradle\wrapper\gradle-wrapper.properties" "$app\gradle\wrapper"
```

`settings.gradle`:

```groovy
rootProject.name = 'GeoTagger'
```

`build.gradle`:

```groovy
// =====================================================================================================================
// GeoTagger build - JavaFX replacement for the parts of GeoSetter that are still needed
// =====================================================================================================================
//
// Requirements
//   - JDK 25 (Gradle toolchain) - Gradle 9 via gradlew
//   - in the local maven repo (~/.m2): tf.GeoHelper:GeoHelper (build it there with "gradlew build")
//   - ExifTool: tests look for it in EXIFTOOL_PATH and then on the PATH - without it they are skipped
//
// Main tasks
//   gradlew build -> compile + test
//   gradlew test  -> all tests
//
// JavaFX, JavaHelper and the application setup follow with plan 3 (main window).
// =====================================================================================================================

plugins {
    id 'java'
}

group = 'tf.GeoTagger'
version = '0.1'
description = 'GeoTagger - JavaFX replacement for the parts of GeoSetter that are still needed'

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenLocal() {
        // only our own libraries come from the local repo - everything else from remote repos
        content {
            includeGroup 'tf.GeoHelper'
        }
    }
    mavenCentral()
}

dependencies {
    implementation 'tf.GeoHelper:GeoHelper:0.1'

    testImplementation platform('org.junit:junit-bom:5.13.0')
    testImplementation 'org.junit.jupiter:junit-jupiter'
    testImplementation 'org.junit.jupiter:junit-jupiter-params'
    // Gradle 9 doesn't provide the launcher automatically anymore
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

tasks.withType(JavaCompile).configureEach {
    options.encoding = 'UTF-8'
    options.compilerArgs += ['-Xlint:unchecked', '-Xlint:deprecation']
}

tasks.named('test', Test) {
    useJUnitPlatform()
    systemProperty 'java.awt.headless', 'true'
    testLogging.showStandardStreams = true
}
```

`.gitignore`: gleicher Inhalt wie in GeoHelper (Task 1, Step 2).

- [ ] **Step 2: Fehlschlagenden Test schreiben**

`src\test\java\tf\geotagger\model\MediaFileTest.java`:

```java
package tf.geotagger.model;

import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MediaFileTest {
    private static MediaFile file(Map<Field, Object> values) {
        final Snapshot snapshot = new Snapshot(new CaptureTime(LocalDateTime.of(2026, 9, 2, 11, 8), false),
                FileTime.fromMillis(0), values);
        return new MediaFile(Path.of("C:/fotos/bild.jpg"), MediaType.IMAGE, snapshot);
    }

    private static Map<Field, Object> values(Object... fieldsAndValues) {
        final Map<Field, Object> values = new EnumMap<>(Field.class);
        for (int i = 0; i < fieldsAndValues.length; i += 2) {
            values.put((Field) fieldsAndValues[i], fieldsAndValues[i + 1]);
        }
        return values;
    }

    @Test
    void showsOriginalValuesWithoutChanges() {
        final MediaFile media = file(values(Field.LATITUDE, 46.4, Field.LONGITUDE, 11.6, Field.CITY, "Meran"));
        assertEquals(46.4, media.latitude());
        assertEquals("Meran", media.text(Field.CITY));
        assertTrue(media.hasCoordinates());
        assertTrue(media.hasPlace());
        assertFalse(media.hasAltitude());
        assertFalse(media.isChanged());
    }

    @Test
    void pendingValueOverridesOriginal() {
        final MediaFile media = file(values(Field.CITY, "Meran"));
        media.set(Field.CITY, "Bozen");
        assertEquals("Bozen", media.text(Field.CITY));
        assertEquals("Meran", media.original().values().get(Field.CITY));
        assertTrue(media.isChanged());
        assertEquals(Map.of(Field.CITY, "Bozen"), media.pendingChanges());
    }

    @Test
    void settingTheOriginalValueAgainRemovesTheChange() {
        final MediaFile media = file(values(Field.CITY, "Meran"));
        media.set(Field.CITY, "Bozen");
        media.set(Field.CITY, "Meran");
        assertFalse(media.isChanged());
    }

    @Test
    void clearingAValueIsAPendingDelete() {
        final MediaFile media = file(values(Field.CITY, "Meran"));
        media.set(Field.CITY, null);
        assertNull(media.text(Field.CITY));
        assertFalse(media.hasPlace());
        assertTrue(media.pendingChanges().containsKey(Field.CITY));
        assertNull(media.pendingChanges().get(Field.CITY));
    }

    @Test
    void blankTextIsTreatedAsEmptyAndLineBreaksAreRemoved() {
        final MediaFile media = file(values());
        media.set(Field.CITY, "   ");
        assertFalse(media.isChanged());
        media.set(Field.SUBLOCATION, " Piazza\r\nDuomo ");
        assertEquals("Piazza Duomo", media.text(Field.SUBLOCATION));
    }

    @Test
    void wrongValueTypeIsRejected() {
        final MediaFile media = file(values());
        assertThrows(IllegalArgumentException.class, () -> media.set(Field.LATITUDE, "46.4"));
        assertThrows(IllegalArgumentException.class, () -> media.set(Field.TIME_OFFSET, "+02:00"));
    }

    @Test
    void replaceOriginalClearsPendingChanges() {
        final MediaFile media = file(values());
        media.set(Field.TIME_OFFSET, ZoneOffset.ofHours(2));
        media.replaceOriginal(new Snapshot(null, FileTime.fromMillis(1), values(Field.TIME_OFFSET, ZoneOffset.ofHours(2))));
        assertFalse(media.isChanged());
        assertEquals(ZoneOffset.ofHours(2), media.timeOffset());
        assertNull(media.captureTime());
    }

    @Test
    void discardChangesRestoresOriginal() {
        final MediaFile media = file(values(Field.ALTITUDE, 1200.0));
        media.set(Field.ALTITUDE, 1300.0);
        media.discardChanges();
        assertEquals(1200.0, media.altitude());
        assertFalse(media.isChanged());
    }

    @Test
    void snapshotDropsNullValuesAndIsImmutable() {
        final Map<Field, Object> values = values(Field.CITY, "Meran");
        values.put(Field.COUNTRY, null);
        final Snapshot snapshot = new Snapshot(null, FileTime.fromMillis(0), values);
        assertFalse(snapshot.values().containsKey(Field.COUNTRY));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.values().put(Field.STATE, "x"));
    }
}
```

- [ ] **Step 3: Test laufen lassen, Fehler erwarten**

Run: `.\gradlew.bat test` (im Ordner `D:\Thomas\Development\NetBeansProjects\GeoTagger`)
Expected: FAIL, Kompilierfehler „cannot find symbol: class MediaFile“.

- [ ] **Step 4: Modell implementieren**

`src\main\java\tf\geotagger\model\Field.java`:

```java
package tf.geotagger.model;

import java.time.ZoneOffset;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * The metadata fields GeoTagger reads and writes - see spec, section "Felder".
 */
public enum Field {
    LATITUDE(Double.class),
    LONGITUDE(Double.class),
    ALTITUDE(Double.class),
    COUNTRY_CODE(String.class),
    COUNTRY(String.class),
    STATE(String.class),
    CITY(String.class),
    SUBLOCATION(String.class),
    TIME_OFFSET(ZoneOffset.class);

    public static final Set<Field> PLACE_FIELDS =
            Collections.unmodifiableSet(EnumSet.of(COUNTRY_CODE, COUNTRY, STATE, CITY, SUBLOCATION));

    private final Class<?> type;

    Field(Class<?> type) {
        this.type = type;
    }

    public Class<?> type() {
        return type;
    }
}
```

`src\main\java\tf\geotagger\model\MediaType.java`:

```java
package tf.geotagger.model;

public enum MediaType {
    IMAGE,
    VIDEO
}
```

`src\main\java\tf\geotagger\model\CaptureTime.java`:

```java
package tf.geotagger.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Capture time as stored in the file: camera local time (EXIF / XMP) or UTC (QuickTime CreateDate of videos).
 */
public record CaptureTime(LocalDateTime dateTime, boolean utc) {
    public CaptureTime {
        Objects.requireNonNull(dateTime);
    }
}
```

`src\main\java\tf\geotagger\model\Snapshot.java`:

```java
package tf.geotagger.model;

import java.nio.file.attribute.FileTime;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Values of a file as last read from disk. A missing field means "not set in the file".
 *
 * @param captureTime may be null if the file has no capture time
 * @param lastModified modification time of the file when it was read - used to detect changes from outside
 */
public record Snapshot(CaptureTime captureTime, FileTime lastModified, Map<Field, Object> values) {
    public Snapshot {
        Objects.requireNonNull(lastModified);
        final EnumMap<Field, Object> copy = new EnumMap<>(Field.class);
        values.forEach((field, value) -> {
            if (value != null) {
                copy.put(field, value);
            }
        });
        values = Collections.unmodifiableMap(copy);
    }
}
```

`src\main\java\tf\geotagger\model\MediaFile.java`:

```java
package tf.geotagger.model;

import java.nio.file.Path;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * One photo or video: the values last read from disk plus the changes that are not saved yet.
 * Shown values are the pending ones where present, otherwise the original ones.
 */
public final class MediaFile {
    private final Path path;
    private final MediaType mediaType;
    private Snapshot original;
    // a null value means "delete this field on save" - EnumMap allows null values
    private final EnumMap<Field, Object> pending = new EnumMap<>(Field.class);

    public MediaFile(Path path, MediaType mediaType, Snapshot original) {
        this.path = Objects.requireNonNull(path);
        this.mediaType = Objects.requireNonNull(mediaType);
        this.original = Objects.requireNonNull(original);
    }

    public Path path() {
        return path;
    }

    public MediaType mediaType() {
        return mediaType;
    }

    public Snapshot original() {
        return original;
    }

    public CaptureTime captureTime() {
        return original.captureTime();
    }

    public Object value(Field field) {
        return pending.containsKey(field) ? pending.get(field) : original.values().get(field);
    }

    public Double latitude() {
        return (Double) value(Field.LATITUDE);
    }

    public Double longitude() {
        return (Double) value(Field.LONGITUDE);
    }

    public Double altitude() {
        return (Double) value(Field.ALTITUDE);
    }

    public String text(Field field) {
        return (String) value(field);
    }

    public ZoneOffset timeOffset() {
        return (ZoneOffset) value(Field.TIME_OFFSET);
    }

    /**
     * Sets a value as pending change - null deletes the field on save.
     * Setting the original value again removes the pending change.
     */
    public void set(Field field, Object value) {
        final Object normalized = normalize(field, value);
        if (Objects.equals(normalized, original.values().get(field))) {
            pending.remove(field);
        } else {
            pending.put(field, normalized);
        }
    }

    public Map<Field, Object> pendingChanges() {
        return Collections.unmodifiableMap(new EnumMap<>(pending));
    }

    public boolean isChanged() {
        return !pending.isEmpty();
    }

    public boolean hasCoordinates() {
        return latitude() != null && longitude() != null;
    }

    public boolean hasAltitude() {
        return altitude() != null;
    }

    public boolean hasPlace() {
        return Field.PLACE_FIELDS.stream().anyMatch(field -> value(field) != null);
    }

    public void discardChanges() {
        pending.clear();
    }

    /**
     * After saving: the file was read again, the pending changes are now part of the original.
     */
    public void replaceOriginal(Snapshot fresh) {
        original = Objects.requireNonNull(fresh);
        pending.clear();
    }

    private static Object normalize(Field field, Object value) {
        if (value == null) {
            return null;
        }
        if (!field.type().isInstance(value)) {
            throw new IllegalArgumentException(field + " expects " + field.type().getSimpleName()
                    + " but got " + value.getClass().getSimpleName());
        }
        if (value instanceof String text) {
            // ExifTool gets one argument per line - no line breaks in values
            final String clean = text.replaceAll("[\\r\\n]+", " ").trim();
            return clean.isEmpty() ? null : clean;
        }
        return value;
    }

    @Override
    public String toString() {
        return path.toString();
    }
}
```

- [ ] **Step 5: Tests laufen lassen**

Run: `.\gradlew.bat test`
Expected: BUILD SUCCESSFUL, 9 Tests grün.

- [ ] **Step 6: Commit**

```powershell
& $git -C $app add .gitignore build.gradle settings.gradle gradlew gradlew.bat gradle src
& $git -C $app commit -m "Add GeoTagger project with media file model"
```

---

### Task 4: Formatprofile und Parsen der ExifTool-Antwort

**Files:**
- Create: `src\main\java\tf\geotagger\metadata\FormatProfile.java`, `ReadTags.java`, `MetadataParser.java`
- Test: `src\test\java\tf\geotagger\metadata\FormatProfileTest.java`, `MetadataParserTest.java`

**Interfaces:**
- Consumes: `Field`, `MediaType`, `CaptureTime` aus Task 3
- Produces:
  - `public enum FormatProfile`
    - Konstanten: `EXIF_IPTC_XMP`, `EXIF_XMP`, `QUICKTIME`, `SIDECAR`
    - Methoden: `MediaType mediaType()`, `boolean writesExif()`, `boolean writesIptc()`
    - `static Optional<FormatProfile> of(Path)`
    - `static Path sidecarFor(Path)`
  - `final class ReadTags`: Konstanten für die Tag-Namen, `static final List<String> ALL`
  - `final class MetadataParser`
    - `static Map<Field, Object> values(Map<String, Object> entry)`
    - `static CaptureTime captureTime(Map<String, Object> entry)` (darf `null` liefern)

- [ ] **Step 1: Fehlschlagende Tests schreiben**

`src\test\java\tf\geotagger\metadata\FormatProfileTest.java`:

```java
package tf.geotagger.metadata;

import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tf.geotagger.model.MediaType;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FormatProfileTest {
    @Test
    void profileIsChosenByExtensionIgnoringCase() {
        assertEquals(Optional.of(FormatProfile.EXIF_IPTC_XMP), FormatProfile.of(Path.of("a/IMG_1.JPG")));
        assertEquals(Optional.of(FormatProfile.EXIF_IPTC_XMP), FormatProfile.of(Path.of("a/scan.tiff")));
        assertEquals(Optional.of(FormatProfile.EXIF_XMP), FormatProfile.of(Path.of("a/IMG_1.CR3")));
        assertEquals(Optional.of(FormatProfile.EXIF_XMP), FormatProfile.of(Path.of("a/PXL_1.heic")));
        assertEquals(Optional.of(FormatProfile.EXIF_XMP), FormatProfile.of(Path.of("a/screen.png")));
        assertEquals(Optional.of(FormatProfile.QUICKTIME), FormatProfile.of(Path.of("a/MVI_1.MOV")));
        assertEquals(Optional.of(FormatProfile.SIDECAR), FormatProfile.of(Path.of("a/clip.avi")));
    }

    @Test
    void unknownFilesHaveNoProfile() {
        assertTrue(FormatProfile.of(Path.of("a/track.gpx")).isEmpty());
        assertTrue(FormatProfile.of(Path.of("a/clip.xmp")).isEmpty());
        assertTrue(FormatProfile.of(Path.of("a/noextension")).isEmpty());
    }

    @Test
    void tagFamiliesPerProfile() {
        assertTrue(FormatProfile.EXIF_IPTC_XMP.writesIptc());
        assertFalse(FormatProfile.EXIF_XMP.writesIptc());
        assertTrue(FormatProfile.EXIF_XMP.writesExif());
        assertFalse(FormatProfile.QUICKTIME.writesExif());
        assertFalse(FormatProfile.SIDECAR.writesExif());
        assertEquals(MediaType.VIDEO, FormatProfile.QUICKTIME.mediaType());
        assertEquals(MediaType.IMAGE, FormatProfile.EXIF_XMP.mediaType());
    }

    @Test
    void sidecarReplacesTheExtension() {
        assertEquals(Path.of("C:/fotos/clip.xmp"), FormatProfile.sidecarFor(Path.of("C:/fotos/clip.avi")));
        assertEquals(Path.of("C:/fotos/my.clip.xmp"), FormatProfile.sidecarFor(Path.of("C:/fotos/my.clip.mts")));
    }
}
```

`src\test\java\tf\geotagger\metadata\MetadataParserTest.java`:

```java
package tf.geotagger.metadata;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tf.geotagger.model.CaptureTime;
import tf.geotagger.model.Field;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetadataParserTest {
    private static Map<String, Object> entry(Object... keysAndValues) {
        final Map<String, Object> entry = new HashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            entry.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return entry;
    }

    @Test
    void compositeGpsHasPriorityOverXmp() {
        final Map<Field, Object> values = MetadataParser.values(entry(
                "Composite:GPSLatitude", 46.4071, "Composite:GPSLongitude", -11.5932, "Composite:GPSAltitude", -3.5,
                "XMP-exif:GPSLatitude", 1.0, "XMP-exif:GPSLongitude", 2.0));
        assertEquals(46.4071, values.get(Field.LATITUDE));
        assertEquals(-11.5932, values.get(Field.LONGITUDE));
        assertEquals(-3.5, values.get(Field.ALTITUDE));
    }

    @Test
    void xmpGpsIsUsedWithoutCompositeAndAltitudeRefIsApplied() {
        final Map<Field, Object> values = MetadataParser.values(entry(
                "XMP-exif:GPSLatitude", -33.5, "XMP-exif:GPSLongitude", "151.25",
                "XMP-exif:GPSAltitude", 12.5, "XMP-exif:GPSAltitudeRef", 1));
        assertEquals(-33.5, values.get(Field.LATITUDE));
        assertEquals(151.25, values.get(Field.LONGITUDE));
        assertEquals(-12.5, values.get(Field.ALTITUDE));
    }

    @Test
    void xmpPlaceHasPriorityOverIptc() {
        final Map<Field, Object> values = MetadataParser.values(entry(
                "XMP-photoshop:City", "Völs", "IPTC:City", "Voels",
                "IPTC:Country-PrimaryLocationCode", "IT", "IPTC:Country-PrimaryLocationName", "Italien",
                "IPTC:Province-State", "Südtirol", "IPTC:Sub-location", " Seiser Alm "));
        assertEquals("Völs", values.get(Field.CITY));
        assertEquals("IT", values.get(Field.COUNTRY_CODE));
        assertEquals("Italien", values.get(Field.COUNTRY));
        assertEquals("Südtirol", values.get(Field.STATE));
        assertEquals("Seiser Alm", values.get(Field.SUBLOCATION));
    }

    @Test
    void numericLookingTextIsKeptAsText() {
        assertEquals("1234", MetadataParser.values(entry("XMP-photoshop:City", 1234)).get(Field.CITY));
    }

    @Test
    void offsetIsParsedAndInvalidValuesAreIgnored() {
        assertEquals(ZoneOffset.ofHours(2), MetadataParser.values(entry("ExifIFD:OffsetTimeOriginal", "+02:00")).get(Field.TIME_OFFSET));
        final Map<Field, Object> invalid = MetadataParser.values(entry(
                "ExifIFD:OffsetTimeOriginal", "   ", "Composite:GPSLatitude", "n/a", "XMP-photoshop:City", ""));
        assertTrue(invalid.isEmpty(), invalid.toString());
    }

    @Test
    void captureTimeFromExifThenXmpThenQuickTime() {
        assertEquals(new CaptureTime(LocalDateTime.of(2026, 9, 2, 11, 8, 0), false),
                MetadataParser.captureTime(entry("ExifIFD:DateTimeOriginal", "2026:09:02 11:08:00",
                        "QuickTime:CreateDate", "2026:09:02 09:08:00")));
        assertEquals(new CaptureTime(LocalDateTime.of(2026, 9, 2, 11, 8, 0), false),
                MetadataParser.captureTime(entry("XMP-exif:DateTimeOriginal", "2026:09:02 11:08:00.123+02:00")));
        assertEquals(new CaptureTime(LocalDateTime.of(2026, 9, 2, 9, 8, 0), true),
                MetadataParser.captureTime(entry("QuickTime:CreateDate", "2026:09:02 09:08:00")));
    }

    @Test
    void invalidCaptureTimeIsNull() {
        assertNull(MetadataParser.captureTime(entry("QuickTime:CreateDate", "0000:00:00 00:00:00")));
        assertNull(MetadataParser.captureTime(entry()));
    }
}
```

- [ ] **Step 2: Tests laufen lassen, Fehler erwarten**

Run: `.\gradlew.bat test`
Expected: FAIL, Kompilierfehler „cannot find symbol: class FormatProfile“.

- [ ] **Step 3: Implementierung schreiben**

`src\main\java\tf\geotagger\metadata\FormatProfile.java`:

```java
package tf.geotagger.metadata;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import tf.geotagger.model.MediaType;

/**
 * Which tag families GeoTagger writes for a file extension - see spec, section "Schreibregeln".
 */
public enum FormatProfile {
    /** EXIF + IPTC + XMP directly in the file. */
    EXIF_IPTC_XMP(MediaType.IMAGE, true, true, "jpg", "jpeg", "tif", "tiff"),
    /** EXIF + XMP directly in the file - IPTC isn't standard here. */
    EXIF_XMP(MediaType.IMAGE, true, false, "png", "heic", "heif", "dng", "cr2", "cr3", "nef", "arw", "orf", "rw2", "raf"),
    /** QuickTime GPS (Keys:GPSCoordinates) + XMP directly in the file. */
    QUICKTIME(MediaType.VIDEO, false, false, "mov", "mp4", "m4v"),
    /** ExifTool can't write these - XMP goes into <name without extension>.xmp next to the file. */
    SIDECAR(MediaType.VIDEO, false, false, "avi", "mts", "m2ts");

    private final MediaType mediaType;
    private final boolean exif;
    private final boolean iptc;
    private final List<String> extensions;

    FormatProfile(MediaType mediaType, boolean exif, boolean iptc, String... extensions) {
        this.mediaType = mediaType;
        this.exif = exif;
        this.iptc = iptc;
        this.extensions = List.of(extensions);
    }

    public MediaType mediaType() {
        return mediaType;
    }

    public boolean writesExif() {
        return exif;
    }

    public boolean writesIptc() {
        return iptc;
    }

    public static Optional<FormatProfile> of(Path file) {
        final String extension = extension(file);
        for (FormatProfile profile : values()) {
            if (profile.extensions.contains(extension)) {
                return Optional.of(profile);
            }
        }
        return Optional.empty();
    }

    public static Path sidecarFor(Path file) {
        final String name = file.getFileName().toString();
        final int dot = name.lastIndexOf('.');
        return file.resolveSibling((dot > 0 ? name.substring(0, dot) : name) + ".xmp");
    }

    private static String extension(Path file) {
        final String name = file.getFileName().toString();
        final int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }
}
```

`src\main\java\tf\geotagger\metadata\ReadTags.java`:

```java
package tf.geotagger.metadata;

import java.util.List;

/**
 * Tags requested from ExifTool ("-json -n -G1": key = family 1 group + tag name).
 */
final class ReadTags {
    static final String SOURCE_FILE = "SourceFile";

    static final String COMPOSITE_LATITUDE = "Composite:GPSLatitude";
    static final String COMPOSITE_LONGITUDE = "Composite:GPSLongitude";
    static final String COMPOSITE_ALTITUDE = "Composite:GPSAltitude";
    static final String XMP_LATITUDE = "XMP-exif:GPSLatitude";
    static final String XMP_LONGITUDE = "XMP-exif:GPSLongitude";
    static final String XMP_ALTITUDE = "XMP-exif:GPSAltitude";
    static final String XMP_ALTITUDE_REF = "XMP-exif:GPSAltitudeRef";

    static final String XMP_COUNTRY_CODE = "XMP-iptcCore:CountryCode";
    static final String XMP_COUNTRY = "XMP-photoshop:Country";
    static final String XMP_STATE = "XMP-photoshop:State";
    static final String XMP_CITY = "XMP-photoshop:City";
    static final String XMP_SUBLOCATION = "XMP-iptcCore:Location";
    static final String IPTC_COUNTRY_CODE = "IPTC:Country-PrimaryLocationCode";
    static final String IPTC_COUNTRY = "IPTC:Country-PrimaryLocationName";
    static final String IPTC_STATE = "IPTC:Province-State";
    static final String IPTC_CITY = "IPTC:City";
    static final String IPTC_SUBLOCATION = "IPTC:Sub-location";

    static final String EXIF_DATE_TIME_ORIGINAL = "ExifIFD:DateTimeOriginal";
    static final String EXIF_OFFSET_TIME_ORIGINAL = "ExifIFD:OffsetTimeOriginal";
    static final String XMP_DATE_TIME_ORIGINAL = "XMP-exif:DateTimeOriginal";
    static final String QUICKTIME_CREATE_DATE = "QuickTime:CreateDate";

    static final List<String> ALL = List.of(
            COMPOSITE_LATITUDE, COMPOSITE_LONGITUDE, COMPOSITE_ALTITUDE,
            XMP_LATITUDE, XMP_LONGITUDE, XMP_ALTITUDE, XMP_ALTITUDE_REF,
            XMP_COUNTRY_CODE, XMP_COUNTRY, XMP_STATE, XMP_CITY, XMP_SUBLOCATION,
            IPTC_COUNTRY_CODE, IPTC_COUNTRY, IPTC_STATE, IPTC_CITY, IPTC_SUBLOCATION,
            EXIF_DATE_TIME_ORIGINAL, EXIF_OFFSET_TIME_ORIGINAL, XMP_DATE_TIME_ORIGINAL, QUICKTIME_CREATE_DATE);

    private ReadTags() {
    }
}
```

`src\main\java\tf\geotagger\metadata\MetadataParser.java`:

```java
package tf.geotagger.metadata;

import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.EnumMap;
import java.util.Map;
import tf.geotagger.model.CaptureTime;
import tf.geotagger.model.Field;

/**
 * Converts one entry of ExifTool's JSON output into GeoTagger values.
 * Priorities: composite GPS (EXIF / QuickTime) before XMP, XMP place before IPTC.
 */
final class MetadataParser {
    private static final DateTimeFormatter EXIF_DATE = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss");

    private MetadataParser() {
    }

    static Map<Field, Object> values(Map<String, Object> entry) {
        final Map<Field, Object> values = new EnumMap<>(Field.class);
        put(values, Field.LATITUDE, firstDouble(entry, ReadTags.COMPOSITE_LATITUDE, ReadTags.XMP_LATITUDE));
        put(values, Field.LONGITUDE, firstDouble(entry, ReadTags.COMPOSITE_LONGITUDE, ReadTags.XMP_LONGITUDE));
        put(values, Field.ALTITUDE, altitude(entry));
        put(values, Field.COUNTRY_CODE, firstText(entry, ReadTags.XMP_COUNTRY_CODE, ReadTags.IPTC_COUNTRY_CODE));
        put(values, Field.COUNTRY, firstText(entry, ReadTags.XMP_COUNTRY, ReadTags.IPTC_COUNTRY));
        put(values, Field.STATE, firstText(entry, ReadTags.XMP_STATE, ReadTags.IPTC_STATE));
        put(values, Field.CITY, firstText(entry, ReadTags.XMP_CITY, ReadTags.IPTC_CITY));
        put(values, Field.SUBLOCATION, firstText(entry, ReadTags.XMP_SUBLOCATION, ReadTags.IPTC_SUBLOCATION));
        put(values, Field.TIME_OFFSET, offset(entry.get(ReadTags.EXIF_OFFSET_TIME_ORIGINAL)));
        return values;
    }

    static CaptureTime captureTime(Map<String, Object> entry) {
        LocalDateTime local = dateTime(entry.get(ReadTags.EXIF_DATE_TIME_ORIGINAL));
        if (local == null) {
            local = dateTime(entry.get(ReadTags.XMP_DATE_TIME_ORIGINAL));
        }
        if (local != null) {
            return new CaptureTime(local, false);
        }
        // QuickTime stores UTC
        final LocalDateTime utc = dateTime(entry.get(ReadTags.QUICKTIME_CREATE_DATE));
        return utc != null ? new CaptureTime(utc, true) : null;
    }

    private static void put(Map<Field, Object> values, Field field, Object value) {
        if (value != null) {
            values.put(field, value);
        }
    }

    private static Double altitude(Map<String, Object> entry) {
        final Double composite = toDouble(entry.get(ReadTags.COMPOSITE_ALTITUDE));
        if (composite != null) {
            return composite;
        }
        final Double xmp = toDouble(entry.get(ReadTags.XMP_ALTITUDE));
        if (xmp == null) {
            return null;
        }
        final Double ref = toDouble(entry.get(ReadTags.XMP_ALTITUDE_REF));
        return ref != null && ref.intValue() == 1 ? -xmp : xmp;
    }

    private static Double firstDouble(Map<String, Object> entry, String... tags) {
        for (String tag : tags) {
            final Double value = toDouble(entry.get(tag));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String firstText(Map<String, Object> entry, String... tags) {
        for (String tag : tags) {
            final String value = toText(entry.get(tag));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static Double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Double.valueOf(text.trim());
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }

    private static String toText(Object value) {
        if (value == null) {
            return null;
        }
        final String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private static LocalDateTime dateTime(Object value) {
        final String text = toText(value);
        if (text == null || text.length() < 19) {
            return null;
        }
        try {
            // ignore sub seconds and offset ("2026:09:02 11:08:00.123+02:00")
            return LocalDateTime.parse(text.substring(0, 19), EXIF_DATE);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private static ZoneOffset offset(Object value) {
        final String text = toText(value);
        if (text == null) {
            return null;
        }
        try {
            return ZoneOffset.of(text);
        } catch (DateTimeException ex) {
            return null;
        }
    }
}
```

- [ ] **Step 4: Tests laufen lassen**

Run: `.\gradlew.bat test`
Expected: BUILD SUCCESSFUL, 20 Tests grün (9 + 4 + 7).

- [ ] **Step 5: Commit**

```powershell
& $git -C $app add src
& $git -C $app commit -m "Add format profiles and ExifTool JSON parsing"
```

---

### Task 5: Schreib-Argumente bauen

**Files:**
- Create: `src\main\java\tf\geotagger\metadata\WriteArgsBuilder.java`
- Test: `src\test\java\tf\geotagger\metadata\WriteArgsBuilderTest.java`

**Interfaces:**
- Consumes: `MediaFile` mit `pendingChanges()`, `latitude()`, `longitude()` und `altitude()` aus Task 3, außerdem `FormatProfile` aus Task 4
- Produces: `final class WriteArgsBuilder` mit `static List<String> assignments(FormatProfile profile, MediaFile file)`
  - Rückgabe: nur ExifTool-Zuweisungen, ohne `-P` und ohne Dateipfad.
  - Leere Liste, wenn nichts geändert ist.
  - Numerische Werte werden mit `#=` geschrieben (ohne Print-Konvertierung), Löschen mit `-TAG=`.

- [ ] **Step 1: Fehlschlagenden Test schreiben**

`src\test\java\tf\geotagger\metadata\WriteArgsBuilderTest.java`:

```java
package tf.geotagger.metadata;

import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tf.geotagger.model.Field;
import tf.geotagger.model.MediaFile;
import tf.geotagger.model.Snapshot;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WriteArgsBuilderTest {
    private static MediaFile file(FormatProfile profile, Map<Field, Object> original) {
        return new MediaFile(Path.of("C:/fotos/x"), profile.mediaType(), new Snapshot(null, FileTime.fromMillis(0), original));
    }

    private static MediaFile file(FormatProfile profile) {
        return file(profile, Map.of());
    }

    @Test
    void noChangesGiveNoArguments() {
        assertTrue(WriteArgsBuilder.assignments(FormatProfile.EXIF_IPTC_XMP, file(FormatProfile.EXIF_IPTC_XMP)).isEmpty());
    }

    @Test
    void coordinatesForExifProfiles() {
        final MediaFile media = file(FormatProfile.EXIF_XMP);
        media.set(Field.LATITUDE, 46.5);
        media.set(Field.LONGITUDE, -0.25);
        assertEquals(List.of(
                "-GPS:GPSLatitude#=46.5", "-GPS:GPSLatitudeRef#=N", "-XMP-exif:GPSLatitude#=46.5",
                "-GPS:GPSLongitude#=0.25", "-GPS:GPSLongitudeRef#=W", "-XMP-exif:GPSLongitude#=-0.25"),
                WriteArgsBuilder.assignments(FormatProfile.EXIF_XMP, media));
    }

    @Test
    void clearedCoordinatesAreDeleted() {
        final Map<Field, Object> original = new EnumMap<>(Field.class);
        original.put(Field.LATITUDE, 46.5);
        final MediaFile media = file(FormatProfile.EXIF_IPTC_XMP, original);
        media.set(Field.LATITUDE, null);
        assertEquals(List.of("-GPS:GPSLatitude=", "-GPS:GPSLatitudeRef=", "-XMP-exif:GPSLatitude="),
                WriteArgsBuilder.assignments(FormatProfile.EXIF_IPTC_XMP, media));
    }

    @Test
    void altitudeBelowSeaLevel() {
        final MediaFile media = file(FormatProfile.EXIF_XMP);
        media.set(Field.ALTITUDE, -12.5);
        assertEquals(List.of(
                "-GPS:GPSAltitude#=12.5", "-GPS:GPSAltitudeRef#=1",
                "-XMP-exif:GPSAltitude#=12.5", "-XMP-exif:GPSAltitudeRef#=1"),
                WriteArgsBuilder.assignments(FormatProfile.EXIF_XMP, media));
    }

    @Test
    void quickTimeGetsCombinedCoordinatesAndXmp() {
        final Map<Field, Object> original = new EnumMap<>(Field.class);
        original.put(Field.ALTITUDE, 100.0);
        final MediaFile media = file(FormatProfile.QUICKTIME, original);
        media.set(Field.LATITUDE, 46.5);
        media.set(Field.LONGITUDE, 11.25);
        assertEquals(List.of(
                "-XMP-exif:GPSLatitude#=46.5", "-XMP-exif:GPSLongitude#=11.25",
                "-Keys:GPSCoordinates#=46.5 11.25 100"),
                WriteArgsBuilder.assignments(FormatProfile.QUICKTIME, media));
    }

    @Test
    void quickTimeCoordinatesAreDeletedWithoutLatitude() {
        final Map<Field, Object> original = new EnumMap<>(Field.class);
        original.put(Field.LATITUDE, 46.5);
        original.put(Field.LONGITUDE, 11.25);
        final MediaFile media = file(FormatProfile.QUICKTIME, original);
        media.set(Field.LATITUDE, null);
        assertTrue(WriteArgsBuilder.assignments(FormatProfile.QUICKTIME, media).contains("-Keys:GPSCoordinates="));
    }

    @Test
    void placeGoesToIptcAndXmpForJpeg() {
        final MediaFile media = file(FormatProfile.EXIF_IPTC_XMP);
        media.set(Field.CITY, "Völs");
        media.set(Field.SUBLOCATION, "Seiser Alm");
        assertEquals(List.of(
                "-IPTC:City=Völs", "-XMP-photoshop:City=Völs",
                "-IPTC:Sub-location=Seiser Alm", "-XMP-iptcCore:Location=Seiser Alm",
                "-IPTC:CodedCharacterSet=UTF8", "-charset", "iptc=UTF8"),
                WriteArgsBuilder.assignments(FormatProfile.EXIF_IPTC_XMP, media));
    }

    @Test
    void placeOnlyToXmpWithoutIptc() {
        final MediaFile media = file(FormatProfile.EXIF_XMP);
        media.set(Field.COUNTRY_CODE, "IT");
        media.set(Field.COUNTRY, "Italien");
        media.set(Field.STATE, "Südtirol");
        assertEquals(List.of(
                "-XMP-iptcCore:CountryCode=IT", "-XMP-photoshop:Country=Italien", "-XMP-photoshop:State=Südtirol"),
                WriteArgsBuilder.assignments(FormatProfile.EXIF_XMP, media));
    }

    @Test
    void timeOffsetOnlyWithExif() {
        final MediaFile jpeg = file(FormatProfile.EXIF_IPTC_XMP);
        jpeg.set(Field.TIME_OFFSET, ZoneOffset.ofHours(2));
        assertEquals(List.of("-ExifIFD:OffsetTimeOriginal=+02:00"), WriteArgsBuilder.assignments(FormatProfile.EXIF_IPTC_XMP, jpeg));

        final MediaFile utc = file(FormatProfile.EXIF_XMP);
        utc.set(Field.TIME_OFFSET, ZoneOffset.UTC);
        assertEquals(List.of("-ExifIFD:OffsetTimeOriginal=+00:00"), WriteArgsBuilder.assignments(FormatProfile.EXIF_XMP, utc));

        final MediaFile video = file(FormatProfile.QUICKTIME);
        video.set(Field.TIME_OFFSET, ZoneOffset.ofHours(2));
        assertTrue(WriteArgsBuilder.assignments(FormatProfile.QUICKTIME, video).isEmpty());
    }

    @Test
    void sidecarGetsOnlyXmp() {
        final MediaFile media = file(FormatProfile.SIDECAR);
        media.set(Field.LATITUDE, 46.5);
        media.set(Field.CITY, "Rom");
        final List<String> args = WriteArgsBuilder.assignments(FormatProfile.SIDECAR, media);
        assertEquals(List.of("-XMP-exif:GPSLatitude#=46.5", "-XMP-photoshop:City=Rom"), args);
        assertFalse(args.stream().anyMatch(arg -> arg.startsWith("-GPS:") || arg.startsWith("-IPTC:") || arg.startsWith("-Keys:")));
    }
}
```

- [ ] **Step 2: Test laufen lassen, Fehler erwarten**

Run: `.\gradlew.bat test --tests "tf.geotagger.metadata.WriteArgsBuilderTest"`
Expected: FAIL, Kompilierfehler „cannot find symbol: class WriteArgsBuilder“.

- [ ] **Step 3: Implementierung schreiben**

`src\main\java\tf\geotagger\metadata\WriteArgsBuilder.java`:

```java
package tf.geotagger.metadata;

import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import tf.geotagger.model.Field;
import tf.geotagger.model.MediaFile;

/**
 * Builds the ExifTool assignments for the pending changes of a file - see spec, sections "Felder" and "Schreibregeln".
 * Numeric values are written with "#=" (no print conversion), a missing value deletes the tag ("-TAG=").
 * Only tags of changed fields are touched, all other tags of the file stay as they are.
 */
final class WriteArgsBuilder {
    private static final Map<Field, String> IPTC_TAGS = new EnumMap<>(Map.of(
            Field.COUNTRY_CODE, "IPTC:Country-PrimaryLocationCode",
            Field.COUNTRY, "IPTC:Country-PrimaryLocationName",
            Field.STATE, "IPTC:Province-State",
            Field.CITY, "IPTC:City",
            Field.SUBLOCATION, "IPTC:Sub-location"));
    private static final Map<Field, String> XMP_TAGS = new EnumMap<>(Map.of(
            Field.COUNTRY_CODE, "XMP-iptcCore:CountryCode",
            Field.COUNTRY, "XMP-photoshop:Country",
            Field.STATE, "XMP-photoshop:State",
            Field.CITY, "XMP-photoshop:City",
            Field.SUBLOCATION, "XMP-iptcCore:Location"));

    private WriteArgsBuilder() {
    }

    static List<String> assignments(FormatProfile profile, MediaFile file) {
        final Map<Field, Object> changes = file.pendingChanges();
        final List<String> args = new ArrayList<>();
        if (changes.containsKey(Field.LATITUDE)) {
            coordinate(args, profile, "Latitude", file.latitude(), "N", "S");
        }
        if (changes.containsKey(Field.LONGITUDE)) {
            coordinate(args, profile, "Longitude", file.longitude(), "E", "W");
        }
        if (changes.containsKey(Field.ALTITUDE)) {
            altitude(args, profile, file.altitude());
        }
        if (profile == FormatProfile.QUICKTIME && (changes.containsKey(Field.LATITUDE)
                || changes.containsKey(Field.LONGITUDE) || changes.containsKey(Field.ALTITUDE))) {
            quickTimeCoordinates(args, file);
        }
        place(args, profile, changes);
        if (profile.writesExif() && changes.containsKey(Field.TIME_OFFSET)) {
            final ZoneOffset offset = (ZoneOffset) changes.get(Field.TIME_OFFSET);
            // ZoneOffset.UTC.getId() is "Z" - EXIF wants "+00:00"
            final String value = offset == null ? "" : ZoneOffset.UTC.equals(offset) ? "+00:00" : offset.getId();
            args.add("-ExifIFD:OffsetTimeOriginal=" + value);
        }
        return args;
    }

    private static void coordinate(List<String> args, FormatProfile profile, String name, Double value,
            String positiveRef, String negativeRef) {
        if (profile.writesExif()) {
            assignNumeric(args, "GPS:GPS" + name, value == null ? null : number(Math.abs(value)));
            assignNumeric(args, "GPS:GPS" + name + "Ref", value == null ? null : value < 0 ? negativeRef : positiveRef);
        }
        assignNumeric(args, "XMP-exif:GPS" + name, value == null ? null : number(value));
    }

    private static void altitude(List<String> args, FormatProfile profile, Double value) {
        final String absolute = value == null ? null : number(Math.abs(value));
        // AltitudeRef: 0 = above, 1 = below sea level
        final String ref = value == null ? null : value < 0 ? "1" : "0";
        if (profile.writesExif()) {
            assignNumeric(args, "GPS:GPSAltitude", absolute);
            assignNumeric(args, "GPS:GPSAltitudeRef", ref);
        }
        assignNumeric(args, "XMP-exif:GPSAltitude", absolute);
        assignNumeric(args, "XMP-exif:GPSAltitudeRef", ref);
    }

    private static void quickTimeCoordinates(List<String> args, MediaFile file) {
        if (!file.hasCoordinates()) {
            assignNumeric(args, "Keys:GPSCoordinates", null);
            return;
        }
        final Double altitude = file.altitude();
        assignNumeric(args, "Keys:GPSCoordinates", number(file.latitude()) + " " + number(file.longitude())
                + (altitude == null ? "" : " " + number(altitude)));
    }

    private static void place(List<String> args, FormatProfile profile, Map<Field, Object> changes) {
        boolean iptcChanged = false;
        for (Field field : Field.PLACE_FIELDS) {
            if (!changes.containsKey(field)) {
                continue;
            }
            final Object value = changes.get(field);
            final String text = value == null ? "" : value.toString();
            if (profile.writesIptc()) {
                args.add("-" + IPTC_TAGS.get(field) + "=" + text);
                iptcChanged = true;
            }
            args.add("-" + XMP_TAGS.get(field) + "=" + text);
        }
        if (iptcChanged) {
            // write IPTC as UTF-8 - umlauts in place names
            args.add("-IPTC:CodedCharacterSet=UTF8");
            args.add("-charset");
            args.add("iptc=UTF8");
        }
    }

    private static void assignNumeric(List<String> args, String tag, String value) {
        args.add(value == null ? "-" + tag + "=" : "-" + tag + "#=" + value);
    }

    private static String number(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }
}
```

Hinweis zur Reihenfolge: `Field.PLACE_FIELDS` ist ein `EnumSet` und wird in Deklarationsreihenfolge durchlaufen (Code, Land, Region, Ort, Sublocation). Darauf verlassen sich die Tests.

- [ ] **Step 4: Tests laufen lassen**

Run: `.\gradlew.bat test`
Expected: BUILD SUCCESSFUL, 30 Tests grün.

- [ ] **Step 5: Commit**

```powershell
& $git -C $app add src
& $git -C $app commit -m "Build ExifTool assignments from pending changes"
```

---

### Task 6: Metadaten lesen

**Files:**
- Create: `src\main\java\tf\geotagger\metadata\MetadataReader.java`
- Test:
  - `src\test\java\tf\geotagger\metadata\ExifToolTestSupport.java`
  - `src\test\java\tf\geotagger\metadata\TestFiles.java`
  - `src\test\java\tf\geotagger\metadata\MetadataReaderTest.java`

**Interfaces:**
- Consumes: `ExifTool.readJson` aus Task 2, `MetadataParser`, `FormatProfile` und `ReadTags` aus Task 4, `MediaFile` und `Snapshot` aus Task 3
- Produces: `public class MetadataReader`
  - `MetadataReader(ExifTool exifTool)`
  - `List<MediaFile> read(List<Path> files)`
  - `List<MediaFile> read(List<Path> files, IntConsumer progress)`
  - Verhalten: Dateien ohne Profil werden ignoriert, die Reihenfolge bleibt erhalten. `progress` bekommt nach jedem Block die Anzahl der bisher gelesenen Dateien.
  - `static final int CHUNK_SIZE = 100`

- [ ] **Step 1: Test-Hilfen schreiben**

`src\test\java\tf\geotagger\metadata\ExifToolTestSupport.java`: gleicher Inhalt wie `tf.geo.exiftool.ExifToolTestSupport` aus Task 1, Step 3. Nur die erste Zeile ändert sich in `package tf.geotagger.metadata;`.

`src\test\java\tf\geotagger\metadata\TestFiles.java`:

```java
package tf.geotagger.metadata;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Locale;
import javax.imageio.ImageIO;

/**
 * Small images for tests - format from the file extension (jpg, png, tif).
 */
final class TestFiles {
    private TestFiles() {
    }

    static Path image(Path dir, String name) {
        final Path file = dir.resolve(name);
        final String extension = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        final String format = switch (extension) {
            case "jpg", "jpeg" -> "jpg";
            case "png" -> "png";
            case "tif", "tiff" -> "tiff";
            default -> throw new IllegalArgumentException("No test image for " + name);
        };
        try {
            if (!ImageIO.write(new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB), format, file.toFile())) {
                throw new IllegalStateException("No ImageIO writer for " + format);
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        return file;
    }
}
```

- [ ] **Step 2: Fehlschlagenden Test schreiben**

`src\test\java\tf\geotagger\metadata\MetadataReaderTest.java`:

```java
package tf.geotagger.metadata;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tf.geo.exiftool.ExifTool;
import tf.geotagger.model.CaptureTime;
import tf.geotagger.model.Field;
import tf.geotagger.model.MediaFile;
import tf.geotagger.model.MediaType;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetadataReaderTest {
    private static ExifTool exifTool;
    private static MetadataReader reader;

    @TempDir
    Path dir;

    @BeforeAll
    static void start() {
        exifTool = new ExifTool(ExifToolTestSupport.require());
        reader = new MetadataReader(exifTool);
    }

    @AfterAll
    static void stop() {
        if (exifTool != null) {
            exifTool.close();
        }
    }

    @Test
    void readsGpsPlaceOffsetAndCaptureTime() {
        final Path file = TestFiles.image(dir, "bild.jpg");
        exifTool.execute(List.of("-overwrite_original",
                "-GPS:GPSLatitude#=46.4071", "-GPS:GPSLatitudeRef#=N",
                "-GPS:GPSLongitude#=11.5932", "-GPS:GPSLongitudeRef#=E",
                "-GPS:GPSAltitude#=1200", "-GPS:GPSAltitudeRef#=0",
                "-XMP-photoshop:City=Völs", "-ExifIFD:DateTimeOriginal=2026:09:02 11:08:00",
                "-ExifIFD:OffsetTimeOriginal=+02:00", file.toString()));

        final MediaFile media = reader.read(List.of(file)).get(0);

        assertEquals(MediaType.IMAGE, media.mediaType());
        assertEquals(46.4071, media.latitude(), 1e-6);
        assertEquals(11.5932, media.longitude(), 1e-6);
        assertEquals(1200.0, media.altitude(), 1e-6);
        assertEquals("Völs", media.text(Field.CITY));
        assertEquals(ZoneOffset.ofHours(2), media.timeOffset());
        assertEquals(new CaptureTime(LocalDateTime.of(2026, 9, 2, 11, 8, 0), false), media.captureTime());
        assertFalse(media.isChanged());
    }

    @Test
    void iptcIsUsedWhenXmpIsMissing() {
        final Path file = TestFiles.image(dir, "iptc.jpg");
        exifTool.execute(List.of("-overwrite_original", "-charset", "iptc=UTF8", "-IPTC:CodedCharacterSet=UTF8",
                "-IPTC:City=Brixen", file.toString()));
        assertEquals("Brixen", reader.read(List.of(file)).get(0).text(Field.CITY));
    }

    @Test
    void unsupportedFilesAreIgnored() throws IOException {
        final Path text = Files.writeString(dir.resolve("notiz.txt"), "x");
        final Path image = TestFiles.image(dir, "a.png");
        final List<MediaFile> files = reader.read(List.of(text, image));
        assertEquals(1, files.size());
        assertEquals(image, files.get(0).path());
    }

    @Test
    void unreadableFileIsStillListedWithoutValues() throws IOException {
        final Path broken = Files.write(dir.resolve("kaputt.jpg"), new byte[] {1, 2, 3, 4});
        final MediaFile media = reader.read(List.of(broken)).get(0);
        assertTrue(media.original().values().isEmpty());
        assertNull(media.captureTime());
    }

    @Test
    void readsInChunksKeepsOrderAndReportsProgress() throws IOException {
        final Path first = TestFiles.image(dir, "bild_000.jpg");
        final List<Path> files = new ArrayList<>(List.of(first));
        for (int i = 1; i < 105; i++) {
            files.add(Files.copy(first, dir.resolve(String.format("bild_%03d.jpg", i))));
        }
        final List<Integer> progress = new ArrayList<>();

        final List<MediaFile> media = reader.read(files, progress::add);

        assertEquals(105, media.size());
        assertEquals(files, media.stream().map(MediaFile::path).toList());
        assertEquals(List.of(100, 105), progress);
    }

    @Test
    void sidecarValuesOverlayTheMediaFile() throws IOException {
        final Path video = Files.write(dir.resolve("clip.avi"), new byte[] {1, 2, 3, 4});
        exifTool.execute(List.of("-XMP-photoshop:City=Rom", "-XMP-exif:GPSLatitude#=41.9", "-o",
                dir.resolve("clip.xmp").toString()));

        final MediaFile media = reader.read(List.of(video)).get(0);

        assertEquals(MediaType.VIDEO, media.mediaType());
        assertEquals("Rom", media.text(Field.CITY));
        assertEquals(41.9, media.latitude(), 1e-6);
    }
}
```

- [ ] **Step 3: Test laufen lassen, Fehler erwarten**

Run: `.\gradlew.bat test --tests "tf.geotagger.metadata.MetadataReaderTest"`
Expected: FAIL, Kompilierfehler „cannot find symbol: class MetadataReader“.

- [ ] **Step 4: Implementierung schreiben**

`src\main\java\tf\geotagger\metadata\MetadataReader.java`:

```java
package tf.geotagger.metadata;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.IntConsumer;
import tf.geo.exiftool.ExifTool;
import tf.geotagger.model.Field;
import tf.geotagger.model.MediaFile;
import tf.geotagger.model.Snapshot;

/**
 * Reads the metadata of many files with as few ExifTool calls as possible (chunks of CHUNK_SIZE files).
 * For SIDECAR files the values of an existing <name>.xmp take precedence over the values of the file itself.
 */
public class MetadataReader {
    static final int CHUNK_SIZE = 100;

    private final ExifTool exifTool;

    public MetadataReader(ExifTool exifTool) {
        this.exifTool = Objects.requireNonNull(exifTool);
    }

    public List<MediaFile> read(List<Path> files) {
        return read(files, count -> {});
    }

    /**
     * @param progress gets the number of files read so far after each chunk
     */
    public List<MediaFile> read(List<Path> files, IntConsumer progress) {
        final List<Path> supported = files.stream().filter(file -> FormatProfile.of(file).isPresent()).toList();
        final List<MediaFile> result = new ArrayList<>(supported.size());
        for (int start = 0; start < supported.size(); start += CHUNK_SIZE) {
            result.addAll(readChunk(supported.subList(start, Math.min(start + CHUNK_SIZE, supported.size()))));
            progress.accept(result.size());
        }
        return result;
    }

    private List<MediaFile> readChunk(List<Path> chunk) {
        final Map<Path, Map<String, Object>> entries = byPath(exifTool.readJson(chunk, ReadTags.ALL));
        final List<Path> sidecars = chunk.stream()
                .filter(file -> FormatProfile.of(file).orElseThrow() == FormatProfile.SIDECAR)
                .map(FormatProfile::sidecarFor)
                .filter(Files::isRegularFile)
                .toList();
        final Map<Path, Map<String, Object>> sidecarEntries = byPath(exifTool.readJson(sidecars, ReadTags.ALL));

        final List<MediaFile> files = new ArrayList<>(chunk.size());
        for (Path file : chunk) {
            final FormatProfile profile = FormatProfile.of(file).orElseThrow();
            final Map<String, Object> entry = entries.getOrDefault(key(file), Map.of());
            final Map<Field, Object> values = new EnumMap<>(Field.class);
            values.putAll(MetadataParser.values(entry));
            if (profile == FormatProfile.SIDECAR) {
                values.putAll(MetadataParser.values(sidecarEntries.getOrDefault(key(FormatProfile.sidecarFor(file)), Map.of())));
            }
            final Snapshot snapshot = new Snapshot(MetadataParser.captureTime(entry), lastModified(file), values);
            files.add(new MediaFile(file, profile.mediaType(), snapshot));
        }
        return files;
    }

    private static Map<Path, Map<String, Object>> byPath(List<Map<String, Object>> entries) {
        final Map<Path, Map<String, Object>> result = new HashMap<>();
        for (Map<String, Object> entry : entries) {
            final Object source = entry.get(ReadTags.SOURCE_FILE);
            if (source != null) {
                result.put(key(Path.of(source.toString())), entry);
            }
        }
        return result;
    }

    private static Path key(Path path) {
        return path.toAbsolutePath().normalize();
    }

    private static FileTime lastModified(Path file) {
        try {
            return Files.getLastModifiedTime(file);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
```

- [ ] **Step 5: Tests laufen lassen**

Run: `.\gradlew.bat test`
Expected: BUILD SUCCESSFUL, 36 Tests grün.

- [ ] **Step 6: Commit**

```powershell
& $git -C $app add src
& $git -C $app commit -m "Read metadata in chunks with sidecar overlay"
```

---

### Task 7: Metadaten schreiben, mit allen Zusicherungen der Spec

**Files:**
- Create: `src\main\java\tf\geotagger\metadata\SaveResult.java`, `MetadataWriter.java`
- Create: `src\test\resources\samples\README.md`
- Test: `src\test\java\tf\geotagger\metadata\MetadataWriterTest.java`

**Interfaces:**
- Consumes:
  - `ExifTool.execute` und `readJson` (Tasks 1 und 2)
  - `WriteArgsBuilder.assignments` (Task 5)
  - `MetadataReader.read` (Task 6)
  - `MediaFile.isChanged`, `original()` und `replaceOriginal` (Task 3)
- Produces:
  - `public record SaveResult(List<FileResult> files)` mit
    - `enum Status { SAVED, SKIPPED_MODIFIED, FAILED }`
    - `record FileResult(MediaFile file, Status status, String message)`
    - `List<FileResult> with(Status)`
  - `public class MetadataWriter`
    - `MetadataWriter(ExifTool, MetadataReader)`
    - `SaveResult save(List<MediaFile> files)`
    - `SaveResult save(List<MediaFile> files, IntConsumer progress)`
    - Nur geänderte Dateien erscheinen im Ergebnis. `progress` zählt alle übergebenen Dateien.

- [ ] **Step 1: README für Musterdateien anlegen**

`src\test\resources\samples\README.md`:

```markdown
# Musterdateien echter Kameras

Hier je eine kleine Kopie pro Quelle ablegen (Inhalt egal), z. B.:

- DSLR-RAW (`.cr3`, `.nef`, `.arw`, …)
- Smartphone-Foto (`.jpg`, `.heic`)
- Videos (`.mov`, `.mp4`, ggf. `.mts`)

`MetadataWriterTest.samplesRoundTrip` schreibt alle GeoTagger-Felder in eine Kopie jeder Datei und liest sie wieder.
Ohne Dateien hier entfällt dieser Test.
```

- [ ] **Step 2: Fehlschlagenden Test schreiben**

`src\test\java\tf\geotagger\metadata\MetadataWriterTest.java`:

```java
package tf.geotagger.metadata;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tf.geo.exiftool.ExifTool;
import tf.geotagger.model.CaptureTime;
import tf.geotagger.model.Field;
import tf.geotagger.model.MediaFile;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetadataWriterTest {
    private static ExifTool exifTool;
    private static MetadataReader reader;
    private static MetadataWriter writer;

    @TempDir
    Path dir;

    @BeforeAll
    static void start() {
        exifTool = new ExifTool(ExifToolTestSupport.require());
        reader = new MetadataReader(exifTool);
        writer = new MetadataWriter(exifTool, reader);
    }

    @AfterAll
    static void stop() {
        if (exifTool != null) {
            exifTool.close();
        }
    }

    private static MediaFile load(Path file) {
        return reader.read(List.of(file)).get(0);
    }

    private static void setAll(MediaFile media) {
        media.set(Field.LATITUDE, -33.856784);
        media.set(Field.LONGITUDE, 151.215297);
        media.set(Field.ALTITUDE, -12.5);
        media.set(Field.COUNTRY_CODE, "AU");
        media.set(Field.COUNTRY, "Australien");
        media.set(Field.STATE, "New South Wales");
        media.set(Field.CITY, "Sydney");
        media.set(Field.SUBLOCATION, "Opernhaus Bühne");
        media.set(Field.TIME_OFFSET, ZoneOffset.ofHours(10));
    }

    private static void assertAll(MediaFile media, boolean withOffset) {
        assertEquals(-33.856784, media.latitude(), 1e-5);
        assertEquals(151.215297, media.longitude(), 1e-5);
        assertEquals(-12.5, media.altitude(), 0.01);
        assertEquals("AU", media.text(Field.COUNTRY_CODE));
        assertEquals("Australien", media.text(Field.COUNTRY));
        assertEquals("New South Wales", media.text(Field.STATE));
        assertEquals("Sydney", media.text(Field.CITY));
        assertEquals("Opernhaus Bühne", media.text(Field.SUBLOCATION));
        assertEquals(withOffset ? ZoneOffset.ofHours(10) : null, media.timeOffset());
    }

    private static void assertSaved(SaveResult result) {
        assertEquals(1, result.files().size());
        final SaveResult.FileResult file = result.files().get(0);
        assertEquals(SaveResult.Status.SAVED, file.status(), file.message());
    }

    private static Map<String, Object> tags(Path file, String... tags) {
        return exifTool.readJson(List.of(file), List.of(tags)).get(0);
    }

    @ParameterizedTest
    @ValueSource(strings = {"jpg", "png", "tif"})
    void allFieldsRoundTrip(String extension) {
        final Path file = TestFiles.image(dir, "bild." + extension);
        final MediaFile media = load(file);
        setAll(media);

        assertSaved(writer.save(List.of(media)));

        assertFalse(media.isChanged());
        assertAll(media, true);
        assertAll(load(file), true);
    }

    @Test
    void iptcOnlyForJpeg() {
        final Path jpeg = TestFiles.image(dir, "bild.jpg");
        final Path png = TestFiles.image(dir, "bild.png");
        final MediaFile jpegMedia = load(jpeg);
        final MediaFile pngMedia = load(png);
        jpegMedia.set(Field.CITY, "Völs");
        pngMedia.set(Field.CITY, "Völs");

        writer.save(List.of(jpegMedia, pngMedia));

        assertEquals("Völs", tags(jpeg, "IPTC:City").get("IPTC:City"));
        assertNull(tags(png, "IPTC:City").get("IPTC:City"));
        assertEquals("Völs", tags(png, "XMP-photoshop:City").get("XMP-photoshop:City"));
    }

    @Test
    void originalIsKept() throws IOException {
        final Path file = TestFiles.image(dir, "bild.jpg");
        final byte[] before = Files.readAllBytes(file);
        final MediaFile media = load(file);
        media.set(Field.CITY, "Meran");

        assertSaved(writer.save(List.of(media)));

        final Path original = dir.resolve("bild.jpg_original");
        assertTrue(Files.exists(original));
        assertArrayEquals(before, Files.readAllBytes(original));
    }

    @Test
    void fileTimesArePreserved() throws IOException {
        final Path file = TestFiles.image(dir, "zeit.jpg");
        final FileTime old = FileTime.from(Instant.parse("2020-01-02T03:04:05Z"));
        Files.getFileAttributeView(file, BasicFileAttributeView.class).setTimes(old, null, old);
        final MediaFile media = load(file);
        media.set(Field.CITY, "Meran");

        assertSaved(writer.save(List.of(media)));

        final BasicFileAttributes after = Files.readAttributes(file, BasicFileAttributes.class);
        assertEquals(old, after.lastModifiedTime());
        assertEquals(old, after.creationTime());
    }

    @Test
    void captureTimeIsNotChanged() {
        final Path file = TestFiles.image(dir, "bild.jpg");
        exifTool.execute(List.of("-overwrite_original", "-ExifIFD:DateTimeOriginal=2026:09:02 11:08:00", file.toString()));
        final MediaFile media = load(file);
        final CaptureTime before = media.captureTime();
        assertNotNull(before);
        setAll(media);

        assertSaved(writer.save(List.of(media)));

        assertEquals(before, load(file).captureTime());
        assertEquals("2026:09:02 11:08:00", tags(file, "ExifIFD:DateTimeOriginal").get("ExifIFD:DateTimeOriginal"));
    }

    @Test
    void imageDirectionIsPreserved() {
        final Path file = TestFiles.image(dir, "handy.jpg");
        exifTool.execute(List.of("-overwrite_original", "-GPS:GPSImgDirection#=74", "-GPS:GPSImgDirectionRef#=M",
                "-GPS:GPSDestBearing#=80", file.toString()));
        final MediaFile media = load(file);
        media.set(Field.LATITUDE, 46.4);
        media.set(Field.LONGITUDE, 11.6);
        media.set(Field.ALTITUDE, 1700.0);
        media.set(Field.CITY, "Karersee");

        assertSaved(writer.save(List.of(media)));

        final Map<String, Object> direction = tags(file, "GPS:GPSImgDirection", "GPS:GPSImgDirectionRef", "GPS:GPSDestBearing");
        assertEquals(74.0, ((Number) direction.get("GPS:GPSImgDirection")).doubleValue(), 1e-9);
        assertEquals("M", direction.get("GPS:GPSImgDirectionRef"));
        assertEquals(80.0, ((Number) direction.get("GPS:GPSDestBearing")).doubleValue(), 1e-9);
    }

    @Test
    void clearedFieldIsDeletedInIptcAndXmp() {
        final Path file = TestFiles.image(dir, "bild.jpg");
        final MediaFile media = load(file);
        media.set(Field.CITY, "Meran");
        assertSaved(writer.save(List.of(media)));

        media.set(Field.CITY, null);
        assertSaved(writer.save(List.of(media)));

        assertNull(load(file).text(Field.CITY));
        final Map<String, Object> city = tags(file, "IPTC:City", "XMP-photoshop:City");
        assertNull(city.get("IPTC:City"));
        assertNull(city.get("XMP-photoshop:City"));
    }

    @Test
    void fileChangedOutsideIsSkipped() throws IOException {
        final Path file = TestFiles.image(dir, "bild.jpg");
        final MediaFile media = load(file);
        Files.setLastModifiedTime(file, FileTime.from(Instant.now().plusSeconds(60)));
        media.set(Field.CITY, "Meran");

        final SaveResult result = writer.save(List.of(media));

        assertEquals(SaveResult.Status.SKIPPED_MODIFIED, result.files().get(0).status());
        assertTrue(media.isChanged());
        assertNull(tags(file, "XMP-photoshop:City").get("XMP-photoshop:City"));
    }

    @Test
    void writeErrorKeepsPendingChanges() throws IOException {
        final Path broken = Files.write(dir.resolve("kaputt.jpg"), new byte[] {1, 2, 3, 4});
        final MediaFile media = load(broken);
        media.set(Field.CITY, "Meran");

        final SaveResult result = writer.save(List.of(media));

        assertEquals(SaveResult.Status.FAILED, result.files().get(0).status());
        assertFalse(result.files().get(0).message().isBlank());
        assertTrue(media.isChanged());
    }

    @Test
    void unchangedFilesAreNotWrittenButCounted() {
        final Path file = TestFiles.image(dir, "bild.jpg");
        final MediaFile media = load(file);
        final List<Integer> progress = new ArrayList<>();

        final SaveResult result = writer.save(List.of(media), progress::add);

        assertTrue(result.files().isEmpty());
        assertEquals(List.of(1), progress);
        assertFalse(Files.exists(dir.resolve("bild.jpg_original")));
    }

    @Test
    void sidecarIsCreatedAndUpdated() throws IOException {
        final byte[] content = {1, 2, 3, 4};
        final Path video = Files.write(dir.resolve("clip.avi"), content);
        final MediaFile media = load(video);
        media.set(Field.LATITUDE, 41.9);
        media.set(Field.LONGITUDE, 12.5);
        media.set(Field.CITY, "Rom");

        assertSaved(writer.save(List.of(media)));
        assertTrue(Files.exists(dir.resolve("clip.xmp")));
        assertArrayEquals(content, Files.readAllBytes(video));
        assertEquals("Rom", load(video).text(Field.CITY));

        media.set(Field.CITY, "Bozen");
        assertSaved(writer.save(List.of(media)));
        final MediaFile reloaded = load(video);
        assertEquals("Bozen", reloaded.text(Field.CITY));
        assertEquals(41.9, reloaded.latitude(), 1e-6);
    }

    @TestFactory
    Stream<DynamicTest> samplesRoundTrip() throws IOException {
        final Path samples = Path.of("src/test/resources/samples");
        if (!Files.isDirectory(samples)) {
            return Stream.empty();
        }
        final List<Path> files;
        try (Stream<Path> list = Files.list(samples)) {
            files = list.filter(file -> FormatProfile.of(file).isPresent()).sorted().toList();
        }
        return files.stream().map(sample -> DynamicTest.dynamicTest(sample.getFileName().toString(), () -> {
            final Path copy = Files.copy(sample, dir.resolve(sample.getFileName()));
            final MediaFile media = load(copy);
            setAll(media);

            assertSaved(writer.save(List.of(media)));

            assertAll(load(copy), FormatProfile.of(copy).orElseThrow().writesExif());
        }));
    }
}
```

- [ ] **Step 3: Test laufen lassen, Fehler erwarten**

Run: `.\gradlew.bat test --tests "tf.geotagger.metadata.MetadataWriterTest"`
Expected: FAIL, Kompilierfehler „cannot find symbol: class MetadataWriter“.

- [ ] **Step 4: Implementierung schreiben**

`src\main\java\tf\geotagger\metadata\SaveResult.java`:

```java
package tf.geotagger.metadata;

import java.util.List;
import tf.geotagger.model.MediaFile;

/**
 * Outcome of a save run - one entry per changed file.
 */
public record SaveResult(List<FileResult> files) {
    public enum Status {
        SAVED,
        /** file was changed outside GeoTagger since it was read - pending changes are kept */
        SKIPPED_MODIFIED,
        /** ExifTool reported an error - pending changes are kept */
        FAILED
    }

    /**
     * @param message ExifTool errors for FAILED, warnings for SAVED (may be empty)
     */
    public record FileResult(MediaFile file, Status status, String message) {
    }

    public SaveResult {
        files = List.copyOf(files);
    }

    public List<FileResult> with(Status status) {
        return files.stream().filter(file -> file.status() == status).toList();
    }
}
```

`src\main\java\tf\geotagger\metadata\MetadataWriter.java`:

```java
package tf.geotagger.metadata;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.IntConsumer;
import java.util.regex.Pattern;
import tf.geo.exiftool.ExecResult;
import tf.geo.exiftool.ExifTool;
import tf.geo.exiftool.ExifToolException;
import tf.geotagger.metadata.SaveResult.FileResult;
import tf.geotagger.metadata.SaveResult.Status;
import tf.geotagger.model.MediaFile;

/**
 * Writes the pending changes of files - see spec, section "Schreibregeln":
 * ExifTool keeps the original as *_original, -P keeps the modification date and the creation date is restored here
 * as well. After writing, the file is read again and becomes the new original.
 */
public class MetadataWriter {
    private static final Pattern SUCCESS = Pattern.compile("\\b[1-9]\\d* image files (updated|created)\\b");

    private final ExifTool exifTool;
    private final MetadataReader reader;

    public MetadataWriter(ExifTool exifTool, MetadataReader reader) {
        this.exifTool = Objects.requireNonNull(exifTool);
        this.reader = Objects.requireNonNull(reader);
    }

    public SaveResult save(List<MediaFile> files) {
        return save(files, count -> {});
    }

    /**
     * @param progress gets the number of files handled so far (changed or not)
     */
    public SaveResult save(List<MediaFile> files, IntConsumer progress) {
        final List<FileResult> results = new ArrayList<>();
        int done = 0;
        for (MediaFile file : files) {
            if (file.isChanged()) {
                results.add(saveOne(file));
            }
            progress.accept(++done);
        }
        return new SaveResult(results);
    }

    private FileResult saveOne(MediaFile file) {
        final Path path = file.path();
        final FormatProfile profile = FormatProfile.of(path).orElseThrow();
        try {
            final BasicFileAttributes before = Files.readAttributes(path, BasicFileAttributes.class);
            if (!before.lastModifiedTime().equals(file.original().lastModified())) {
                return new FileResult(file, Status.SKIPPED_MODIFIED, "File was changed outside GeoTagger since it was loaded");
            }

            final List<String> args = new ArrayList<>(List.of("-P"));
            args.addAll(WriteArgsBuilder.assignments(profile, file));
            if (profile == FormatProfile.SIDECAR) {
                final Path sidecar = FormatProfile.sidecarFor(path).toAbsolutePath();
                if (Files.exists(sidecar)) {
                    args.add(sidecar.toString());
                } else {
                    // ExifTool creates a new XMP file from the assignments alone
                    args.add("-o");
                    args.add(sidecar.toString());
                }
            } else {
                args.add(path.toAbsolutePath().toString());
            }

            final ExecResult result = exifTool.execute(args);
            if (result.hasErrors() || !SUCCESS.matcher(result.stdoutText()).find()) {
                final List<String> details = result.hasErrors() ? result.errors() : result.stdout();
                return new FileResult(file, Status.FAILED, String.join("; ", details));
            }

            restoreTimes(path, before);
            file.replaceOriginal(reader.read(List.of(path)).get(0).original());
            return new FileResult(file, Status.SAVED, String.join("; ", result.warnings()));
        } catch (IOException | UncheckedIOException | ExifToolException ex) {
            return new FileResult(file, Status.FAILED, ex.getMessage());
        }
    }

    private static void restoreTimes(Path path, BasicFileAttributes before) throws IOException {
        // -P keeps the modification date - the creation date is restored here in case ExifTool doesn't
        Files.getFileAttributeView(path, BasicFileAttributeView.class)
                .setTimes(before.lastModifiedTime(), null, before.creationTime());
    }
}
```

- [ ] **Step 5: Tests laufen lassen**

Run: `.\gradlew.bat test`
Expected:
- BUILD SUCCESSFUL, 49 Tests grün (36 + 3 parametrisierte + 10). Die `samplesRoundTrip`-Fälle kommen hinzu, sobald Musterdateien vorliegen.
- Schlägt `allFieldsRoundTrip` für `png` oder `tif` fehl, zuerst mit `exiftool -a -G1 -n <datei>` nachsehen, welche Tags tatsächlich geschrieben wurden. Die Regeln in `WriteArgsBuilder` werden **nur** korrigiert, wenn ExifTool ein Tag nachweislich nicht schreibt. Die Tests werden nicht abgeschwächt.

- [ ] **Step 6: Commit**

```powershell
& $git -C $app add src
& $git -C $app commit -m "Save pending changes with ExifTool, keep originals, file times and foreign tags"
```

- [ ] **Step 7: Nutzer um Musterdateien bitten**

Den Nutzer bitten, je eine Kopie einer DSLR-RAW-Datei, eines Smartphone-JPEG/HEIC und eines Videos in `src\test\resources\samples` abzulegen. Danach `.\gradlew.bat test --tests "tf.geotagger.metadata.MetadataWriterTest"` erneut ausführen und das Ergebnis je Format berichten. Fehler bei echten Formaten werden wie in Step 5 untersucht.

---

## Abschluss von Plan 1

- Beide Repos bauen mit `.\gradlew.bat build`, und alle Tests sind grün.
- Den Nutzer daran erinnern, beide Repos zu pushen, sobald sie ein Remote haben.
- Danach Plan 2 (Track-Sync-Logik) auf Basis des tatsächlich entstandenen Codes schreiben.

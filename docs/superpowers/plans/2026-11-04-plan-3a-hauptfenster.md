# GeoTagger Plan 3a: Hauptfenster (Gerüst, Ordner, Liste, Vorschau) – Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use anthropic-skills:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** GeoTagger startet als JavaFX-Anwendung. Es öffnet einen Ordner (wahlweise mit Unterordnern) im Hintergrund und zeigt die Dateien in einer Liste mit Statusspalten und Filtern. Die ausgewählte Datei erscheint in einer großen Vorschau. Der ExifTool-Pfad wird in den Einstellungen gepflegt.

**Architecture:** Der Aufbau folgt GPXEditor:
- `GeoTaggerManager extends Application` lädt `GeoTagger.fxml` mit dem Controller `GeoTagger`.
- `GeoTaggerPreferences` ist ein Enum, der `IPreferencesStore` von JavaHelper implementiert und `java.util.prefs` nutzt.
- Die Einstellungen sind ein eigenes Fenster `PreferenceEditor extends AbstractStage`.
- Das Theme ist JMetro, hell.

Abweichend von GPXEditor läuft das Laden in einem echten Hintergrund-Task (`javafx.concurrent.Task` auf einem eigenen Thread). GPXEditors `TaskExecutor` führt die Arbeit auf dem FX-Thread aus, bei 3.000 Dateien würde das Fenster einfrieren.

Die Logik liegt in UI-freien, getesteten Klassen:
- `files.FolderScanner` (Ordnersuche)
- `model.MediaFilter` (Filter)
- `metadata.ExifToolLocator` (ExifTool finden und prüfen)
- `preview.PreviewProvider` (Vorschau-Quelle)

**Tech Stack:** Java 25, JavaFX 25.0.1 (base, graphics, controls, fxml), JavaHelper 1.17, JMetro 11.6.16, ExifToolHelper 0.1, JUnit 5.13.0.

Spec: [2026-11-03-geotagger-design.md](../specs/2026-11-03-geotagger-design.md), Abschnitte „Hauptfenster“, „Datenfluss“ und „Fehlerbehandlung“

**Einordnung:** Plan 3 ist geteilt.
- **3a (dieser Plan):** Gerüst, Ordner laden, Liste mit Filtern, Vorschau, Einstellungen.
- **3b:** Reiter „Details“ (editierbar, Mehrfachauswahl), Reiter „Bild-Info (ExifTool)“, Undo/Redo, Speichern mit Zusammenfassung, Rückfrage bei ungespeicherten Änderungen. Dafür bekommt das Modell dann auch Beobachtbarkeit, denn in 3a ändert noch nichts eine `MediaFile`.
- Die rechte Fensterhälfte bleibt in 3a ein Platzhalter. Plan 4 füllt sie mit Karte und Track-Liste.

## Global Constraints

- **Oberflächensprache: Englisch**, wie GPXEditor (Entscheidung des Nutzers). Die deutschen Beschriftungen in Spec und Mockups sind nur Illustration.
- **Keine automatisierten UI-Tests**, wie in GPXEditor. Die Logik wird mit JUnit getestet, die Oberfläche je Task manuell nach einer Checkliste.
- Java 25 über die Gradle-Toolchain, Gradle 9.2.1 über den Wrapper. Gearbeitet wird direkt auf `master`.
- **Lizenz-Header:** Jede `.java`-Datei, auch in `src/test`, beginnt mit dem BSD-Header aus GPXEditor („Copyright (c) 2014ff Thomas Feuster“, 25 Zeilen, wörtlich übernommen, z. B. von `GeoTagger\src\main\java\tf\geotagger\model\Field.java`). Die Codeblöcke unten lassen ihn der Kürze halber weg. Für FXML gilt das nicht.
- **Javadoc:**
  - Hat eine Methode, ein Konstruktor oder ein Record einen Javadoc-Kommentar, dann mit allen `@param`/`@return`/`@throws`.
  - Text wie `<name>` steht in `{@code …}`.
  - Methoden ohne Javadoc sind in Ordnung.
- Code und Kommentare sind auf Englisch. Compilerwarnungen in `src/main` und `src/test` werden behoben.
- **Git und Ordner** in jeder PowerShell-Sitzung:
  ```powershell
  $git = 'C:\Users\thomas\AppData\Local\GitHubDesktop\app-3.6.7\resources\app\git\cmd\git.exe'
  $app = 'D:\Thomas\Development\NetBeansProjects\GeoTagger'
  ```
  Gradle-Befehle laufen in `$app` (`Set-Location $app`).
- **ExifTool:** Die Tests finden es über `EXIFTOOL_PATH` oder den PATH. Auf dem Entwicklungsrechner ist das 13.38 unter `C:\Users\Thomas\AppData\Roaming\GeoSetter_beta\tools\exiftool.exe`. Die App selbst nimmt den Pfad aus den Einstellungen. Ist dort nichts gesetzt, sucht sie einmalig im PATH.
- **Fachliche Regeln aus der Spec:**
  - Formate gemäß `FormatProfile`. GPX-Dateien werden mitgefunden (für Plan 4), `*_original` und `.xmp` nicht.
  - Gelesen wird in Blöcken von 100 Dateien mit Fortschrittsanzeige.
  - Ohne ExifTool ist kein Ordner ladbar. Beim Start öffnet sich dann der Einstellungsdialog.
  - Vorschau:
    - JPEG/PNG/GIF/BMP werden direkt angezeigt.
    - Bei allen anderen Formaten wird das größte eingebettete Bild genommen, in der Reihenfolge `JpgFromRaw`, `PreviewImage`, `ThumbnailImage`. Videos ohne eingebettetes Bild zeigen „No preview“. Hinweis: Das Canon-MOV hat ein kleines `ThumbnailImage` (160×120), das damit ohne Mehraufwand angezeigt wird.
    - Die EXIF-Orientierung (1/3/6/8) wird als Drehung berücksichtigt.
    - Das vorherige und das nächste Bild werden vorab geladen, ein kleiner Speicher-Cache hält sie.
    - „100 %“ lädt das Bild in voller Auflösung.
  - Filter: All, Without coordinates, Without place, Without altitude, Changed, jeweils mit Anzahl.

## Dateistruktur

```
build.gradle                                          -- + JavaFX, JavaHelper, JMetro, application/run (like GPXEditor)
src\main\resources\GeoTagger.fxml                     -- main window layout
src\main\java\tf\geotagger\main\
  GeoTaggerManager.java     -- Application: load FXML, restore / save window bounds, JMetro
  GeoTagger.java            -- controller: ExifTool start, open folder, background loading, table, filters, preview wiring
  GeoTaggerPreferences.java -- enum implements IPreferencesStore (java.util.prefs), like GPXEditorPreferences
  PreferenceEditor.java     -- AbstractStage: ExifTool path with browse / check
  PreviewPane.java          -- large preview with navigation, fit / 100 %, rotation, LRU cache, prefetch
src\main\java\tf\geotagger\files\FolderScanner.java   -- media + GPX files of a folder (optionally with sub folders)
src\main\java\tf\geotagger\model\MediaFilter.java     -- list filters with counts
src\main\java\tf\geotagger\metadata\ExifToolLocator.java -- find ExifTool on the PATH, check version
src\main\java\tf\geotagger\preview\
  Preview.java              -- image file to show + rotation
  PreviewProvider.java      -- file itself or extracted embedded JPEG, orientation, cache folder
src\main\java\tf\geotagger\track\GpxReader.java       -- + isGpxFile(Path)
src\test\java\tf\geotagger\files\FolderScannerTest.java
src\test\java\tf\geotagger\model\MediaFilterTest.java
src\test\java\tf\geotagger\metadata\ExifToolLocatorTest.java
src\test\java\tf\geotagger\metadata\TestFiles.java, ExifToolTestSupport.java -- become public (used by preview tests)
src\test\java\tf\geotagger\preview\PreviewProviderTest.java
```

---

### Task 1: Build für JavaFX und Anwendungsgerüst

**Files:**
- Modify: `build.gradle` (komplett ersetzen)
- Create:
  - `src\main\java\tf\geotagger\main\GeoTaggerPreferences.java`
  - `src\main\java\tf\geotagger\main\GeoTaggerManager.java`
  - `src\main\java\tf\geotagger\main\GeoTagger.java` (Gerüst, Task 6 ersetzt ihn)
  - `src\main\resources\GeoTagger.fxml` (Gerüst, Task 6 ersetzt es)

**Interfaces:**
- Consumes: JavaHelper `tf.helper.general.IPreferencesStore`, `GeneralParser.parse(String, Class<T>)`, `ObjectsHelper.uncheckedCast(Object)`
- Produces:
  - `public enum GeoTaggerPreferences implements IPreferencesStore`
    - Konstanten: `INSTANCE`, `RECENT_WINDOW_WIDTH`, `RECENT_WINDOW_HEIGHT`, `RECENT_WINDOW_LEFT`, `RECENT_WINDOW_TOP`, `RECENT_LEFT_DIVIDER_POS`, `RECENT_CENTRAL_DIVIDER_POS`, `EXIFTOOL_PATH`, `RECENT_FOLDER`, `INCLUDE_SUBFOLDERS`
    - Methoden: `String getAsString()`, `<T> T getAsType()`, `<T> T getDefaultAsType()`, `<T> void put(T value)`
  - `public class GeoTaggerManager extends Application` mit `public static final String TITLE = "GeoTagger"`
  - Controller `GeoTagger` mit `void lateInitialize(Stage stage)` und `void stop()`

- [ ] **Step 1: `build.gradle` ersetzen**

```groovy
// =====================================================================================================================
// GeoTagger build - JavaFX replacement for the parts of GeoSetter that are still needed
// =====================================================================================================================
//
// Requirements
//   - JDK 25 (Gradle toolchain) - Gradle 9 via gradlew
//   - in the local maven repo (~/.m2): tf.ExifToolHelper:ExifToolHelper and tf.JavaHelper:JavaHelper
//     (build them in their projects with "gradlew build"); tf.GeoHelper:GeoHelper follows with plan 4 (map)
//   - ExifTool: the application asks for it in the preferences; tests look for it in EXIFTOOL_PATH and then on the
//     PATH - without it they are skipped
//
// Main tasks
//   gradlew build -> compile + test
//   gradlew run   -> start GeoTagger
//
// How JavaFX is wired - same approach as GPXEditor (GeoTagger is non-modular, i.e. runs from the classpath)
//   - compile / test: JavaFX jars on the classpath are sufficient (tests don't start the JavaFX toolkit)
//   - run: the JavaFX jars of the current platform are put on the module path, all other jars stay on the classpath
//   - start scripts (installDist) and jpackage follow later, once there is something to install
// =====================================================================================================================

plugins {
    id 'java'
    id 'application'
    id 'com.google.osdetector' version '1.7.3'
}

group = 'tf.GeoTagger'
version = '0.1'
description = 'GeoTagger - JavaFX replacement for the parts of GeoSetter that are still needed'

// classifier of the platform specific JavaFX jars (win / mac / linux)
def fxPlatform = osdetector.os == 'osx' ? 'mac' : osdetector.os == 'windows' ? 'win' : osdetector.os
def javafxVersion = '25.0.1'

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenLocal() {
        // breaking change in gradle 6.*: doesn't find jar files automatically when no pom.xml is present...
        metadataSources {
            mavenPom()
            artifact()
        }
        // only our own libraries come from the local repo - everything else from remote repos
        content {
            includeGroup 'tf.ExifToolHelper'
            includeGroup 'tf.GeoHelper'
            includeGroup 'tf.JavaHelper'
        }
    }
    mavenCentral()
}

dependencies {
    // JavaFX explicit dependencies
    implementation "org.openjfx:javafx-base:$javafxVersion:$fxPlatform"
    implementation "org.openjfx:javafx-graphics:$javafxVersion:$fxPlatform"
    implementation "org.openjfx:javafx-controls:$javafxVersion:$fxPlatform"
    implementation "org.openjfx:javafx-fxml:$javafxVersion:$fxPlatform"

    // our own helper libraries
    implementation 'tf.JavaHelper:JavaHelper:1.17'
    implementation 'tf.ExifToolHelper:ExifToolHelper:0.1'

    // same look as GPXEditor
    implementation 'org.jfxtras:jmetro:11.6.16'

    // GPX is read with Jackson XML - the same data binding the other applications (e.g. GPXEditor) use
    // BOM: one version for all jackson modules - same as GPXEditor
    implementation platform('com.fasterxml.jackson:jackson-bom:2.21.2')
    implementation 'com.fasterxml.jackson.dataformat:jackson-dataformat-xml'

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

// JavaFX needs to be on the module path at runtime - only the platform specific jars
// maven delivers each JavaFX module twice: an empty javafx-base-25.0.1.jar and the real javafx-base-25.0.1-win.jar - keep only the latter
def javafxJars = configurations.runtimeClasspath.filter { it.name.startsWith('javafx-') && !(it.name ==~ /javafx-[a-z]+-[\d.]+\.jar/) }

// adds "--module-path <files>" to the JVM of run - evaluated lazily when the task executes (same as GPXEditor)
abstract class ModulePathArgumentProvider implements CommandLineArgumentProvider {
    @InputFiles
    @PathSensitive(PathSensitivity.NAME_ONLY)
    abstract ConfigurableFileCollection getModulePath()

    @Override
    Iterable<String> asArguments() {
        return ['--module-path', modulePath.asPath]
    }
}

// JVM options needed to run GeoTagger - JMetro and JavaHelper use JavaFX internals (exports as in GPXEditor)
def jvmFlags = ['--add-modules', 'javafx.controls,javafx.graphics,javafx.base,javafx.fxml',
                '--add-exports', 'javafx.graphics/com.sun.javafx.util=ALL-UNNAMED',
                '--add-exports', 'javafx.base/com.sun.javafx.reflect=ALL-UNNAMED',
                '--add-exports', 'javafx.base/com.sun.javafx.beans=ALL-UNNAMED',
                '--add-exports', 'javafx.base/com.sun.javafx.logging=ALL-UNNAMED',
                '--add-exports', 'javafx.base/com.sun.javafx.runtime=ALL-UNNAMED',
                '--add-exports', 'javafx.base/com.sun.javafx.collections=ALL-UNNAMED',
                '--add-exports', 'javafx.base/com.sun.javafx.event=ALL-UNNAMED',
                '--add-exports', 'javafx.graphics/com.sun.prism=ALL-UNNAMED',
                '--add-exports', 'javafx.graphics/com.sun.javafx.sg.prism=ALL-UNNAMED',
                '--add-exports', 'javafx.graphics/com.sun.javafx.scene=ALL-UNNAMED',
                '--add-exports', 'javafx.controls/com.sun.javafx.scene.control=ALL-UNNAMED',
                '--add-exports', 'javafx.controls/com.sun.javafx.scene.control.behavior=ALL-UNNAMED',
                '--add-exports', 'javafx.controls/com.sun.javafx.scene.control.inputmap=ALL-UNNAMED',
                '--add-exports', 'javafx.graphics/com.sun.javafx.scene.traversal=ALL-UNNAMED',
                '--add-exports', 'javafx.graphics/com.sun.javafx.tk=ALL-UNNAMED',
                '--add-exports', 'javafx.graphics/com.sun.javafx.scene.input=ALL-UNNAMED',
                '--add-opens', 'javafx.graphics/javafx.scene.layout=ALL-UNNAMED',
                '--enable-native-access', 'javafx.graphics',
                '--enable-native-access', 'ALL-UNNAMED',
    ]

application {
    mainClass = 'tf.geotagger.main.GeoTaggerManager'
    applicationDefaultJvmArgs = jvmFlags + ['-enableassertions']
}

tasks.named('run', JavaExec) {
    jvmArgumentProviders.add(objects.newInstance(ModulePathArgumentProvider).tap { modulePath.from(javafxJars) })
    enableAssertions = true
}
```

- [ ] **Step 2: Preferences schreiben**

`src\main\java\tf\geotagger\main\GeoTaggerPreferences.java`:

```java
package tf.geotagger.main;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.InvalidPreferencesFormatException;
import java.util.prefs.Preferences;
import tf.helper.general.GeneralParser;
import tf.helper.general.IPreferencesStore;
import tf.helper.general.ObjectsHelper;

/**
 * Preferences of GeoTagger, stored with java.util.prefs - same pattern as GPXEditorPreferences.
 */
public enum GeoTaggerPreferences implements IPreferencesStore {
    INSTANCE("instance", "", String.class),
    RECENT_WINDOW_WIDTH("recentWindowWidth", Double.toString(1400), Double.class),
    RECENT_WINDOW_HEIGHT("recentWindowHeight", Double.toString(900), Double.class),
    RECENT_WINDOW_LEFT("recentWindowLeft", Double.toString(-1), Double.class),
    RECENT_WINDOW_TOP("recentWindowTop", Double.toString(-1), Double.class),
    // list vs. preview
    RECENT_LEFT_DIVIDER_POS("recentLeftDividerPos", Double.toString(0.55), Double.class),
    // left side vs. map / details
    RECENT_CENTRAL_DIVIDER_POS("recentCentralDividerPos", Double.toString(0.5), Double.class),

    EXIFTOOL_PATH("exifToolPath", "", String.class),
    RECENT_FOLDER("recentFolder", System.getProperty("user.home"), String.class),
    INCLUDE_SUBFOLDERS("includeSubfolders", Boolean.toString(false), Boolean.class);

    // additional preferences not handled here as enums
    // tableview settings: ColumnOrder, ColumnWidth, ColumnVisibility, SortOrder - see tf.helper.javafx.TableViewPreferences

    private static final Preferences MYPREFERENCES = Preferences.userNodeForPackage(GeoTaggerManager.class);

    private final String myPrefKey;
    private final String myDefaultValue;
    private final Class<?> myClass;

    GeoTaggerPreferences(final String key, final String defaultValue, final Class<?> classP) {
        myPrefKey = key;
        myDefaultValue = defaultValue;
        myClass = classP;
    }

    public String getAsString() {
        return get(myPrefKey, myDefaultValue);
    }

    public <T> T getAsType() {
        try {
            return ObjectsHelper.uncheckedCast(GeneralParser.parse(getAsString(), myClass));
        } catch (Exception ex) {
            return getDefaultAsType();
        }
    }

    public <T> void put(final T value) {
        put(myPrefKey, value.toString());
    }

    public <T> T getDefaultAsType() {
        return ObjectsHelper.uncheckedCast(GeneralParser.parse(myDefaultValue, myClass));
    }

    @Override
    public String get(final String key, final String defaultValue) {
        String result = defaultValue;
        try {
            result = MYPREFERENCES.get(key, defaultValue);
        } catch (SecurityException ex) {
            Logger.getLogger(GeoTaggerPreferences.class.getName()).log(Level.SEVERE, null, ex);
        }
        return result;
    }

    @Override
    public void put(final String key, final String value) {
        MYPREFERENCES.put(key, value);
    }

    @Override
    public void clear() {
        try {
            MYPREFERENCES.clear();
        } catch (BackingStoreException ex) {
            Logger.getLogger(GeoTaggerPreferences.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void remove(final String key) {
        MYPREFERENCES.remove(key);
    }

    @Override
    public void exportPreferences(final OutputStream os) {
        try {
            MYPREFERENCES.exportSubtree(os);
        } catch (BackingStoreException | IOException ex) {
            Logger.getLogger(GeoTaggerPreferences.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void importPreferences(final InputStream is) {
        try {
            Preferences.importPreferences(is);
        } catch (InvalidPreferencesFormatException | IOException ex) {
            Logger.getLogger(GeoTaggerPreferences.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
```

- [ ] **Step 3: Application, Controller-Gerüst und FXML-Gerüst schreiben**

`src\main\java\tf\geotagger\main\GeoTaggerManager.java`:

```java
package tf.geotagger.main;

import java.io.IOException;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Screen;
import javafx.stage.Stage;
import jfxtras.styles.jmetro.JMetro;
import jfxtras.styles.jmetro.Style;

/**
 * Starts GeoTagger: loads the main window from GeoTagger.fxml and restores its size and position.
 */
public class GeoTaggerManager extends Application {
    public static final String TITLE = "GeoTagger";

    private Stage myStage;
    private GeoTagger myGeoTagger;

    public static void main(String[] args) {
        launch(GeoTaggerManager.class, args);
    }

    @Override
    public void start(Stage primaryStage) throws IOException {
        myStage = primaryStage;

        final FXMLLoader fxmlLoader = new FXMLLoader(GeoTaggerManager.class.getResource("/GeoTagger.fxml"));
        final Parent root = fxmlLoader.load();
        myGeoTagger = fxmlLoader.getController();

        // restore size and position of the last session - same as GPXEditor
        Double width = GeoTaggerPreferences.RECENT_WINDOW_WIDTH.getAsType();
        Double height = GeoTaggerPreferences.RECENT_WINDOW_HEIGHT.getAsType();
        final Rectangle2D screenBounds = Screen.getPrimary().getVisualBounds();
        Double left = GeoTaggerPreferences.RECENT_WINDOW_LEFT.getAsType();
        if (left < 0.0) {
            left = (screenBounds.getWidth() - width) / 2.0;
        }
        Double top = GeoTaggerPreferences.RECENT_WINDOW_TOP.getAsType();
        if (top < 0.0) {
            top = (screenBounds.getHeight() - height) / 2.0;
        }
        // the last position might be on a monitor that isn't there anymore
        if (Screen.getScreensForRectangle(left, top, width, height).isEmpty()) {
            width = GeoTaggerPreferences.RECENT_WINDOW_WIDTH.getDefaultAsType();
            height = GeoTaggerPreferences.RECENT_WINDOW_HEIGHT.getDefaultAsType();
            left = (screenBounds.getWidth() - width) / 2.0;
            top = (screenBounds.getHeight() - height) / 2.0;
        }

        myStage.setScene(new Scene(root, width, height));
        myStage.setX(left);
        myStage.setY(top);
        myStage.setTitle(TITLE);
        new JMetro(Style.LIGHT).setScene(myStage.getScene());
        myStage.show();

        myGeoTagger.lateInitialize(myStage);
    }

    @Override
    public void stop() {
        if (myStage != null && !myStage.isMaximized() && !myStage.isIconified()) {
            GeoTaggerPreferences.RECENT_WINDOW_WIDTH.put(myStage.getScene().getWidth());
            GeoTaggerPreferences.RECENT_WINDOW_HEIGHT.put(myStage.getScene().getHeight());
            GeoTaggerPreferences.RECENT_WINDOW_LEFT.put(myStage.getX());
            GeoTaggerPreferences.RECENT_WINDOW_TOP.put(myStage.getY());
        }
        if (myGeoTagger != null) {
            myGeoTagger.stop();
        }
    }
}
```

`src\main\java\tf\geotagger\main\GeoTagger.java` (Gerüst, Task 6 ersetzt ihn):

```java
package tf.geotagger.main;

import java.net.URL;
import java.util.ResourceBundle;
import javafx.fxml.Initializable;
import javafx.stage.Stage;

/**
 * Controller of the main window (GeoTagger.fxml).
 */
public class GeoTagger implements Initializable {
    private Stage stage;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
    }

    /**
     * Called once the stage is shown.
     *
     * @param stage the main window
     */
    public void lateInitialize(Stage stage) {
        this.stage = stage;
        this.stage.setTitle(GeoTaggerManager.TITLE);
    }

    /**
     * Saves the layout and stops background work - called when GeoTagger ends.
     */
    public void stop() {
    }
}
```

`src\main\resources\GeoTagger.fxml` (Gerüst, Task 6 ersetzt es):

```xml
<?xml version="1.0" encoding="UTF-8"?>

<?import javafx.scene.control.Label?>
<?import javafx.scene.layout.BorderPane?>

<BorderPane xmlns="http://javafx.com/javafx" xmlns:fx="http://javafx.com/fxml" fx:controller="tf.geotagger.main.GeoTagger">
    <center>
        <Label text="GeoTagger"/>
    </center>
</BorderPane>
```

- [ ] **Step 4: Bauen und testen**

Run: `.\gradlew.bat build`
Expected: BUILD SUCCESSFUL, weiterhin 86 Tests grün, keine Compilerwarnungen.

- [ ] **Step 5: Manuell prüfen**

Run: `.\gradlew.bat run`
Prüfen:
1. Ein Fenster „GeoTagger“ im JMetro-Look öffnet sich mit dem Text „GeoTagger“.
2. Fenster verschieben, Größe ändern, schließen und erneut starten: Größe und Position sind wie zuvor.
3. Die Konsole zeigt keine Exceptions. JavaFX-Warnungen zu „unnamed module“ dürfen nicht erscheinen, denn JavaFX läuft über den Module-Path.

- [ ] **Step 6: Commit**

```powershell
& $git -C $app add build.gradle src
& $git -C $app commit -m "Add JavaFX application skeleton with preferences"
```

---

### Task 2: Ordner durchsuchen

**Files:**
- Create: `src\main\java\tf\geotagger\files\FolderScanner.java`
- Modify: `src\main\java\tf\geotagger\track\GpxReader.java` (neue Methode `isGpxFile`, von `findGpxFiles` genutzt)
- Test: `src\test\java\tf\geotagger\files\FolderScannerTest.java`

**Interfaces:**
- Consumes: `FormatProfile.of(Path)` (Plan 1), `GpxReader` (Plan 2)
- Produces:
  - `public static boolean GpxReader.isGpxFile(Path file)`
  - `public final class FolderScanner` mit
    - `public record ScanResult(List<Path> mediaFiles, List<Path> gpxFiles)`
    - `static ScanResult scan(Path folder, boolean includeSubfolders) throws IOException`
    - Beide Listen sind nach Pfad sortiert. Andere Dateien (`*_original`, `.xmp`, `.txt`) werden ignoriert.

- [ ] **Step 1: Fehlschlagenden Test schreiben**

`src\test\java\tf\geotagger\files\FolderScannerTest.java`:

```java
package tf.geotagger.files;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FolderScannerTest {
    @TempDir
    Path dir;

    @BeforeEach
    void createFiles() throws IOException {
        for (String name : List.of("a.jpg", "b.CR3", "c.mov", "d.gpx", "e.txt", "f.jpg_original", "g.xmp")) {
            Files.createFile(dir.resolve(name));
        }
        Files.createDirectory(dir.resolve("sub"));
        Files.createFile(dir.resolve("sub").resolve("h.jpg"));
        Files.createFile(dir.resolve("sub").resolve("i.GPX"));
    }

    @Test
    void findsMediaAndGpxFilesOfTheFolder() throws IOException {
        final FolderScanner.ScanResult result = FolderScanner.scan(dir, false);

        assertEquals(List.of(dir.resolve("a.jpg"), dir.resolve("b.CR3"), dir.resolve("c.mov")), result.mediaFiles());
        assertEquals(List.of(dir.resolve("d.gpx")), result.gpxFiles());
    }

    @Test
    void includesSubfoldersOnRequest() throws IOException {
        final FolderScanner.ScanResult result = FolderScanner.scan(dir, true);

        assertEquals(List.of(dir.resolve("a.jpg"), dir.resolve("b.CR3"), dir.resolve("c.mov"),
                dir.resolve("sub").resolve("h.jpg")), result.mediaFiles());
        assertEquals(List.of(dir.resolve("d.gpx"), dir.resolve("sub").resolve("i.GPX")), result.gpxFiles());
    }

    @Test
    void missingFolderFails() {
        assertThrows(IOException.class, () -> FolderScanner.scan(dir.resolve("gibt-es-nicht"), false));
    }
}
```

- [ ] **Step 2: Test laufen lassen, Fehler erwarten**

Run: `.\gradlew.bat test --tests "tf.geotagger.files.FolderScannerTest"`
Expected: FAIL, Kompilierfehler „cannot find symbol: class FolderScanner“.

- [ ] **Step 3: `GpxReader.isGpxFile` ergänzen**

In `src\main\java\tf\geotagger\track\GpxReader.java` ersetzen:

```java
                    .filter(file -> file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".gpx"))
```

durch:

```java
                    .filter(GpxReader::isGpxFile)
```

Diese Methode kommt direkt nach `findGpxFiles` dazu:

```java
    /**
     * @param file file to check
     * @return true if the file name ends with .gpx (case insensitive)
     */
    public static boolean isGpxFile(Path file) {
        return file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".gpx");
    }
```

- [ ] **Step 4: `FolderScanner` schreiben**

`src\main\java\tf\geotagger\files\FolderScanner.java`:

```java
package tf.geotagger.files;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import tf.geotagger.metadata.FormatProfile;
import tf.geotagger.track.GpxReader;

/**
 * Finds the files GeoTagger works with in a folder: photos / videos (see FormatProfile) and GPX tracks.
 */
public final class FolderScanner {
    /**
     * @param mediaFiles photos and videos, sorted by path
     * @param gpxFiles GPX files, sorted by path
     */
    public record ScanResult(List<Path> mediaFiles, List<Path> gpxFiles) {
        public ScanResult {
            mediaFiles = List.copyOf(mediaFiles);
            gpxFiles = List.copyOf(gpxFiles);
        }
    }

    private FolderScanner() {
    }

    /**
     * @param folder folder to search
     * @param includeSubfolders also search all sub folders
     * @return media and GPX files - other files (e.g. *_original, .xmp) are ignored
     * @throws IOException if the folder or one of its sub folders can't be read
     */
    public static ScanResult scan(Path folder, boolean includeSubfolders) throws IOException {
        final List<Path> mediaFiles = new ArrayList<>();
        final List<Path> gpxFiles = new ArrayList<>();
        try (Stream<Path> files = includeSubfolders ? Files.walk(folder) : Files.list(folder)) {
            files.filter(Files::isRegularFile).sorted().forEach(file -> {
                if (FormatProfile.of(file).isPresent()) {
                    mediaFiles.add(file);
                } else if (GpxReader.isGpxFile(file)) {
                    gpxFiles.add(file);
                }
            });
        } catch (UncheckedIOException ex) {
            // Files.walk reports unreadable sub folders this way
            throw ex.getCause();
        }
        return new ScanResult(mediaFiles, gpxFiles);
    }
}
```

- [ ] **Step 5: Tests laufen lassen**

Run: `.\gradlew.bat test`
Expected: BUILD SUCCESSFUL, 89 Tests grün.

- [ ] **Step 6: Commit**

```powershell
& $git -C $app add src
& $git -C $app commit -m "Find media and GPX files of a folder"
```

---

### Task 3: Filter der Dateiliste

**Files:**
- Create: `src\main\java\tf\geotagger\model\MediaFilter.java`
- Test: `src\test\java\tf\geotagger\model\MediaFilterTest.java`

**Interfaces:**
- Consumes: `MediaFile.hasCoordinates()`, `hasPlace()`, `hasAltitude()` und `isChanged()` (Plan 1)
- Produces: `public enum MediaFilter implements Predicate<MediaFile>`
  - Konstanten: `ALL`, `WITHOUT_COORDINATES`, `WITHOUT_PLACE`, `WITHOUT_ALTITUDE`, `CHANGED`
  - `String label()`
  - `static Map<MediaFilter, Integer> counts(Collection<MediaFile> files)`

- [ ] **Step 1: Fehlschlagenden Test schreiben**

`src\test\java\tf\geotagger\model\MediaFilterTest.java`:

```java
package tf.geotagger.model;

import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MediaFilterTest {
    private static MediaFile file(String name, Map<Field, Object> values) {
        return new MediaFile(Path.of("C:/fotos/" + name), MediaType.IMAGE, new Snapshot(null, FileTime.fromMillis(0), values));
    }

    private static final MediaFile COMPLETE = file("complete.jpg",
            Map.of(Field.LATITUDE, 46.4, Field.LONGITUDE, 11.6, Field.ALTITUDE, 1200.0, Field.CITY, "Meran"));
    private static final MediaFile EMPTY = file("empty.jpg", Map.of());

    @Test
    void filtersTestTheStatusOfAFile() {
        assertTrue(MediaFilter.ALL.test(COMPLETE));
        assertFalse(MediaFilter.WITHOUT_COORDINATES.test(COMPLETE));
        assertTrue(MediaFilter.WITHOUT_COORDINATES.test(EMPTY));
        assertTrue(MediaFilter.WITHOUT_PLACE.test(EMPTY));
        assertTrue(MediaFilter.WITHOUT_ALTITUDE.test(EMPTY));
        assertFalse(MediaFilter.CHANGED.test(EMPTY));
    }

    @Test
    void countsPerFilterIncludePendingChanges() {
        final MediaFile changed = file("changed.jpg", Map.of(Field.LATITUDE, 46.4, Field.LONGITUDE, 11.6));
        changed.set(Field.CITY, "Bozen");

        final Map<MediaFilter, Integer> counts = MediaFilter.counts(List.of(COMPLETE, EMPTY, changed));

        assertEquals(Map.of(MediaFilter.ALL, 3, MediaFilter.WITHOUT_COORDINATES, 1, MediaFilter.WITHOUT_PLACE, 1,
                MediaFilter.WITHOUT_ALTITUDE, 2, MediaFilter.CHANGED, 1), counts);
    }
}
```

- [ ] **Step 2: Test laufen lassen, Fehler erwarten**

Run: `.\gradlew.bat test --tests "tf.geotagger.model.MediaFilterTest"`
Expected: FAIL, Kompilierfehler „cannot find symbol: class MediaFilter“.

- [ ] **Step 3: Implementierung schreiben**

`src\main\java\tf\geotagger\model\MediaFilter.java`:

```java
package tf.geotagger.model;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Filters of the file list - they follow the workflow: coordinates first, then place and altitude.
 */
public enum MediaFilter implements Predicate<MediaFile> {
    ALL("All") {
        @Override
        public boolean test(MediaFile file) {
            return true;
        }
    },
    WITHOUT_COORDINATES("Without coordinates") {
        @Override
        public boolean test(MediaFile file) {
            return !file.hasCoordinates();
        }
    },
    WITHOUT_PLACE("Without place") {
        @Override
        public boolean test(MediaFile file) {
            return !file.hasPlace();
        }
    },
    WITHOUT_ALTITUDE("Without altitude") {
        @Override
        public boolean test(MediaFile file) {
            return !file.hasAltitude();
        }
    },
    CHANGED("Changed") {
        @Override
        public boolean test(MediaFile file) {
            return file.isChanged();
        }
    };

    private final String label;

    MediaFilter(String label) {
        this.label = label;
    }

    /**
     * @return text for the filter buttons
     */
    public String label() {
        return label;
    }

    /**
     * @param files files to count
     * @return number of matching files per filter
     */
    public static Map<MediaFilter, Integer> counts(Collection<MediaFile> files) {
        final Map<MediaFilter, Integer> counts = new EnumMap<>(MediaFilter.class);
        for (MediaFilter filter : values()) {
            counts.put(filter, (int) files.stream().filter(filter).count());
        }
        return counts;
    }
}
```

- [ ] **Step 4: Tests laufen lassen**

Run: `.\gradlew.bat test`
Expected: BUILD SUCCESSFUL, 91 Tests grün.

- [ ] **Step 5: Commit**

```powershell
& $git -C $app add src
& $git -C $app commit -m "Add file list filters with counts"
```

---

### Task 4: ExifTool finden und prüfen

**Files:**
- Create: `src\main\java\tf\geotagger\metadata\ExifToolLocator.java`
- Test: `src\test\java\tf\geotagger\metadata\ExifToolLocatorTest.java`

**Interfaces:**
- Consumes: `tf.exiftool.ExifTool(Path, Duration)`, `version()`, `ExifToolException` (ExifToolHelper)
- Produces: `public final class ExifToolLocator`
  - `static Optional<Path> findOnPath()`
  - package-private `static Optional<Path> findOnPath(String pathVariable)`
  - `static String version(Path executable)`, wirft `ExifToolException`, wenn die Datei fehlt oder kein ExifTool ist

- [ ] **Step 1: Fehlschlagenden Test schreiben**

`src\test\java\tf\geotagger\metadata\ExifToolLocatorTest.java`:

```java
package tf.geotagger.metadata;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tf.exiftool.ExifToolException;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExifToolLocatorTest {
    @TempDir
    Path dir;

    @Test
    void findsExecutableOnPath() throws IOException {
        final Path exifTool = Files.createFile(dir.resolve("exiftool.exe"));
        final String path = "C:\\does\\not\\exist" + File.pathSeparator + " " + File.pathSeparator + dir;

        assertEquals(Optional.of(exifTool), ExifToolLocator.findOnPath(path));
    }

    @Test
    void nothingFoundWithoutExecutable() {
        assertTrue(ExifToolLocator.findOnPath(dir.toString()).isEmpty());
        assertTrue(ExifToolLocator.findOnPath(null).isEmpty());
    }

    @Test
    void versionOfRealExifTool() {
        assertTrue(ExifToolLocator.version(ExifToolTestSupport.require()).matches("\\d+\\.\\d+.*"));
    }

    @Test
    void missingExecutableFails() {
        assertThrows(ExifToolException.class, () -> ExifToolLocator.version(dir.resolve("exiftool.exe")));
    }

    @Test
    void otherFileFails() throws IOException {
        final Path text = Files.writeString(dir.resolve("notiz.txt"), "no program");
        assertThrows(ExifToolException.class, () -> ExifToolLocator.version(text));
    }
}
```

- [ ] **Step 2: Test laufen lassen, Fehler erwarten**

Run: `.\gradlew.bat test --tests "tf.geotagger.metadata.ExifToolLocatorTest"`
Expected: FAIL, Kompilierfehler „cannot find symbol: class ExifToolLocator“.

- [ ] **Step 3: Implementierung schreiben**

`src\main\java\tf\geotagger\metadata\ExifToolLocator.java`:

```java
package tf.geotagger.metadata;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import tf.exiftool.ExifTool;
import tf.exiftool.ExifToolException;

/**
 * Finds ExifTool and checks that a configured executable really is ExifTool.
 */
public final class ExifToolLocator {
    // a wrong executable (e.g. any other program) must not block the preferences dialog for long
    private static final Duration CHECK_TIMEOUT = Duration.ofSeconds(20);

    private ExifToolLocator() {
    }

    /**
     * @return exiftool(.exe) from the first PATH entry that contains it
     */
    public static Optional<Path> findOnPath() {
        return findOnPath(System.getenv("PATH"));
    }

    static Optional<Path> findOnPath(String pathVariable) {
        if (pathVariable == null) {
            return Optional.empty();
        }
        for (String dir : pathVariable.split(File.pathSeparator)) {
            if (dir.isBlank()) {
                continue;
            }
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

    /**
     * @param executable ExifTool executable to check
     * @return the version reported by ExifTool, e.g. "13.38"
     * @throws ExifToolException if the file doesn't exist or doesn't answer like ExifTool
     */
    public static String version(Path executable) {
        if (!Files.isRegularFile(executable)) {
            throw new ExifToolException("ExifTool not found: " + executable);
        }
        try (ExifTool exifTool = new ExifTool(executable, CHECK_TIMEOUT)) {
            return exifTool.version();
        }
    }
}
```

- [ ] **Step 4: Tests laufen lassen**

Run: `.\gradlew.bat test`
Expected: BUILD SUCCESSFUL, 96 Tests grün.

- [ ] **Step 5: Commit**

```powershell
& $git -C $app add src
& $git -C $app commit -m "Find ExifTool on the PATH and check its version"
```

---

### Task 5: Vorschau-Quelle

**Files:**
- Create: `src\main\java\tf\geotagger\preview\Preview.java`, `PreviewProvider.java`
- Modify:
  - `src\test\java\tf\geotagger\metadata\TestFiles.java`: Klasse und `image(...)` werden `public`
  - `src\test\java\tf\geotagger\metadata\ExifToolTestSupport.java`: Klasse, `locate()` und `require()` werden `public`
- Test: `src\test\java\tf\geotagger\preview\PreviewProviderTest.java`

**Interfaces:**
- Consumes: `ExifTool.readJson`, `extractBinary` (ExifToolHelper); Test: `TestFiles.image`, `ExifToolTestSupport.require`
- Produces:
  - `public record Preview(Path image, int rotation)`. `rotation` ist 0, 90, 180 oder 270 Grad im Uhrzeigersinn.
  - `public class PreviewProvider`
    - `PreviewProvider(ExifTool exifTool, Path cacheDir)`
    - `Optional<Preview> preview(Path file)`
    - `void clearCache()`

- [ ] **Step 1: Test-Hilfen öffentlich machen**

In `src\test\java\tf\geotagger\metadata\TestFiles.java`:
- `final class TestFiles {` → `public final class TestFiles {`
- `    static Path image(Path dir, String name) {` → `    public static Path image(Path dir, String name) {`

In `src\test\java\tf\geotagger\metadata\ExifToolTestSupport.java`:
- `final class ExifToolTestSupport {` → `public final class ExifToolTestSupport {`
- `    static Optional<Path> locate() {` → `    public static Optional<Path> locate() {`
- `    static Path require() {` → `    public static Path require() {`

- [ ] **Step 2: Fehlschlagenden Test schreiben**

`src\test\java\tf\geotagger\preview\PreviewProviderTest.java`:

```java
package tf.geotagger.preview;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tf.exiftool.ExifTool;
import tf.geotagger.metadata.ExifToolTestSupport;
import tf.geotagger.metadata.TestFiles;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreviewProviderTest {
    private static ExifTool exifTool;

    @TempDir
    Path dir;

    private Path cache;
    private PreviewProvider provider;

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

    @BeforeEach
    void createProvider() {
        cache = dir.resolve("cache");
        provider = new PreviewProvider(exifTool, cache);
    }

    // ExifTool recognizes formats by content: a JPEG named like a format JavaFX can't show stands in for HEIC / RAW
    private Path withEmbeddedImage(String name) throws IOException {
        final Path thumbnail = TestFiles.image(dir, "thumb.jpg");
        final Path base = TestFiles.image(dir, "base.jpg");
        exifTool.execute(List.of("-overwrite_original", "-ThumbnailImage<=" + thumbnail, base.toString()));
        return Files.copy(base, dir.resolve(name));
    }

    @Test
    void jpegIsShownDirectly() {
        final Path jpeg = TestFiles.image(dir, "bild.jpg");

        assertEquals(Optional.of(new Preview(jpeg, 0)), provider.preview(jpeg));
    }

    @Test
    void orientationGivesTheRotation() {
        final Path jpeg = TestFiles.image(dir, "hochkant.jpg");
        exifTool.execute(List.of("-overwrite_original", "-IFD0:Orientation#=6", jpeg.toString()));

        assertEquals(90, provider.preview(jpeg).orElseThrow().rotation());
    }

    @Test
    void embeddedImageIsExtractedOnce() throws IOException {
        final Path heic = withEmbeddedImage("kamera.heic");

        final Preview first = provider.preview(heic).orElseThrow();
        assertTrue(first.image().startsWith(cache));
        assertArrayEquals(Files.readAllBytes(dir.resolve("thumb.jpg")), Files.readAllBytes(first.image()));
        final FileTime extracted = Files.getLastModifiedTime(first.image());

        assertEquals(first, provider.preview(heic).orElseThrow());
        assertEquals(extracted, Files.getLastModifiedTime(first.image()));
    }

    @Test
    void nothingToShowWithoutEmbeddedImage() {
        final Path tiff = TestFiles.image(dir, "scan.tif");

        assertTrue(provider.preview(tiff).isEmpty());
    }

    @Test
    void clearCacheRemovesExtractedImages() throws IOException {
        final Preview preview = provider.preview(withEmbeddedImage("kamera.heic")).orElseThrow();

        provider.clearCache();

        assertFalse(Files.exists(preview.image()));
    }
}
```

- [ ] **Step 3: Test laufen lassen, Fehler erwarten**

Run: `.\gradlew.bat test --tests "tf.geotagger.preview.PreviewProviderTest"`
Expected: FAIL, Kompilierfehler „cannot find symbol: class PreviewProvider“.

- [ ] **Step 4: Implementierung schreiben**

`src\main\java\tf\geotagger\preview\Preview.java`:

```java
package tf.geotagger.preview;

import java.nio.file.Path;
import java.util.Objects;

/**
 * What to show for a file.
 *
 * @param image file JavaFX can show - the media file itself or an extracted embedded JPEG
 * @param rotation clockwise rotation in degrees from the EXIF orientation: 0, 90, 180 or 270
 */
public record Preview(Path image, int rotation) {
    public Preview {
        Objects.requireNonNull(image);
    }
}
```

`src\main\java\tf\geotagger\preview\PreviewProvider.java`:

```java
package tf.geotagger.preview;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import tf.exiftool.ExifTool;

/**
 * Finds the image to show for a photo or video - see spec, section "GeoTagger-Pakete", preview.
 * Formats JavaFX can't show (RAW, HEIC, TIFF, videos) are shown via their largest embedded JPEG, which is extracted
 * once into the cache folder.
 */
public class PreviewProvider {
    // largest first - RAW files usually have a full size JpgFromRaw or PreviewImage
    static final List<String> EMBEDDED_IMAGES = List.of("JpgFromRaw", "PreviewImage", "ThumbnailImage");
    // formats JavaFX can show directly
    private static final Set<String> JAVAFX_FORMATS = Set.of("jpg", "jpeg", "png", "gif", "bmp");
    private static final String ORIENTATION = "IFD0:Orientation";

    private final ExifTool exifTool;
    private final Path cacheDir;

    /**
     * @param exifTool used to read the orientation and to extract embedded images
     * @param cacheDir folder for extracted images - created when needed
     */
    public PreviewProvider(ExifTool exifTool, Path cacheDir) {
        this.exifTool = Objects.requireNonNull(exifTool);
        this.cacheDir = Objects.requireNonNull(cacheDir);
    }

    /**
     * @param file photo or video
     * @return the preview, empty if there is nothing to show (e.g. a video without thumbnail)
     */
    public Optional<Preview> preview(Path file) {
        final int rotation = rotation(file);
        if (JAVAFX_FORMATS.contains(extension(file))) {
            return Optional.of(new Preview(file, rotation));
        }
        try {
            Files.createDirectories(cacheDir);
            final Path cached = cacheDir.resolve(cacheName(file));
            if (!Files.isRegularFile(cached) && !extract(file, cached)) {
                return Optional.empty();
            }
            return Optional.of(new Preview(cached, rotation));
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    /**
     * Deletes the extracted images - called when GeoTagger ends.
     */
    public void clearCache() {
        if (!Files.isDirectory(cacheDir)) {
            return;
        }
        try (Stream<Path> files = Files.list(cacheDir)) {
            for (Path file : files.toList()) {
                Files.deleteIfExists(file);
            }
        } catch (IOException ex) {
            // best effort - it's a temp folder
        }
    }

    private boolean extract(Path file, Path target) {
        for (String tag : EMBEDDED_IMAGES) {
            if (exifTool.extractBinary(file, tag, target)) {
                return true;
            }
        }
        return false;
    }

    private int rotation(Path file) {
        final List<Map<String, Object>> entries = exifTool.readJson(List.of(file), List.of(ORIENTATION));
        final Object orientation = entries.isEmpty() ? null : entries.get(0).get(ORIENTATION);
        if (!(orientation instanceof Number number)) {
            return 0;
        }
        // 3 / 6 / 8 are rotations - the mirrored variants (2, 4, 5, 7) are practically never used by cameras
        return switch (number.intValue()) {
            case 3 -> 180;
            case 6 -> 90;
            case 8 -> 270;
            default -> 0;
        };
    }

    private static String extension(Path file) {
        final String name = file.getFileName().toString();
        final int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }

    // unique per file and modification time - a changed file gets a new extract
    private static String cacheName(Path file) throws IOException {
        final String key = file.toAbsolutePath().normalize() + "|" + Files.getLastModifiedTime(file).toMillis();
        try {
            final byte[] hash = MessageDigest.getInstance("SHA-1").digest(key.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash) + ".jpg";
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-1 is part of every JDK", ex);
        }
    }
}
```

- [ ] **Step 5: Tests laufen lassen**

Run: `.\gradlew.bat test`
Expected: BUILD SUCCESSFUL, 101 Tests grün.

- [ ] **Step 6: Commit**

```powershell
& $git -C $app add src
& $git -C $app commit -m "Provide previews: file itself or extracted embedded JPEG, with rotation"
```

---

### Task 6: Einstellungen und Hauptfenster mit Ordner, Liste und Filtern

**Files:**
- Create: `src\main\java\tf\geotagger\main\PreferenceEditor.java`
- Modify (komplett ersetzen): `src\main\java\tf\geotagger\main\GeoTagger.java`, `src\main\resources\GeoTagger.fxml`

**Interfaces:**
- Consumes:
  - `GeoTaggerPreferences` (Task 1), `FolderScanner` (Task 2), `MediaFilter` (Task 3), `ExifToolLocator` (Task 4)
  - `MetadataReader` (Plan 1)
  - JavaHelper `AbstractStage`, `ShowAlerts`, `TableViewPreferences`
- Produces:
  - `public class PreferenceEditor extends AbstractStage` mit `static PreferenceEditor getInstance()` und `boolean editPreferences()`. Rückgabe `true`, wenn mit „Save“ geschlossen.
  - Controller `GeoTagger` mit den Methoden `select(int)`, `applyExifTool()` und `stop()`, an die Task 7 anknüpft.

- [ ] **Step 1: Einstellungsfenster schreiben**

`src\main\java\tf\geotagger\main\PreferenceEditor.java`:

```java
package tf.geotagger.main;

import java.io.File;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import jfxtras.styles.jmetro.JMetro;
import jfxtras.styles.jmetro.Style;
import tf.exiftool.ExifToolException;
import tf.geotagger.metadata.ExifToolLocator;
import tf.helper.javafx.AbstractStage;

/**
 * Preferences of GeoTagger - for now the ExifTool executable. Built in code like GPXEditor's PreferenceEditor.
 */
public class PreferenceEditor extends AbstractStage {
    private static final PreferenceEditor INSTANCE = new PreferenceEditor();

    private final TextField exifToolPath = new TextField();
    private final Label exifToolCheck = new Label();

    private PreferenceEditor() {
        initViewer();
    }

    /**
     * @return the preferences window - created on first use, must be called on the JavaFX thread
     */
    public static PreferenceEditor getInstance() {
        return INSTANCE;
    }

    private void initViewer() {
        setTitle(GeoTaggerManager.TITLE + " - Preferences");
        initModality(Modality.APPLICATION_MODAL);
        new JMetro(Style.LIGHT).setScene(getScene());

        final GridPane grid = getGridPane();
        final ColumnConstraints growing = new ColumnConstraints();
        growing.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(new ColumnConstraints(), growing, new ColumnConstraints());

        final Label exifToolLabel = new Label("ExifTool:");
        grid.add(exifToolLabel, 0, 0);
        GridPane.setMargin(exifToolLabel, INSET_TOP);
        exifToolPath.setPrefColumnCount(45);
        grid.add(exifToolPath, 1, 0);
        GridPane.setMargin(exifToolPath, INSET_TOP);
        final Button browseButton = new Button("Browse...");
        browseButton.setOnAction(event -> browse());
        grid.add(browseButton, 2, 0);
        GridPane.setMargin(browseButton, INSET_TOP);

        final Button checkButton = new Button("Check");
        checkButton.setOnAction(event -> check());
        grid.add(checkButton, 0, 1);
        GridPane.setMargin(checkButton, INSET_TOP);
        grid.add(exifToolCheck, 1, 1, 2, 1);
        GridPane.setMargin(exifToolCheck, INSET_TOP);

        final Button saveButton = new Button("Save");
        saveButton.setOnAction(event -> {
            GeoTaggerPreferences.EXIFTOOL_PATH.put(exifToolPath.getText().trim());
            close();
        });
        setActionAccelerator(saveButton);
        final Button cancelButton = new Button("Cancel");
        cancelButton.setOnAction(event -> close());
        setCancelAccelerator(cancelButton);

        final HBox buttons = new HBox(8, saveButton, cancelButton);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        buttons.setPadding(INSET_TOP_BOTTOM);
        getRootPane().getChildren().add(buttons);
    }

    /**
     * Shows the preferences and waits until the window is closed.
     *
     * @return true if the preferences were saved
     */
    public boolean editPreferences() {
        exifToolPath.setText(GeoTaggerPreferences.EXIFTOOL_PATH.getAsString());
        exifToolCheck.setText("");
        showAndWait();
        return wasActionButtonPressed();
    }

    private void browse() {
        final FileChooser chooser = new FileChooser();
        chooser.setTitle("Select ExifTool");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Programs", "*.exe"),
                new FileChooser.ExtensionFilter("All files", "*.*"));
        final File current = new File(exifToolPath.getText().trim());
        if (current.getParentFile() != null && current.getParentFile().isDirectory()) {
            chooser.setInitialDirectory(current.getParentFile());
        }
        final File selected = chooser.showOpenDialog(this);
        if (selected != null) {
            exifToolPath.setText(selected.getAbsolutePath());
            check();
        }
    }

    private void check() {
        try {
            exifToolCheck.setText("ExifTool " + ExifToolLocator.version(Path.of(exifToolPath.getText().trim())));
        } catch (ExifToolException | InvalidPathException ex) {
            exifToolCheck.setText("Not usable: " + ex.getMessage());
        }
    }
}
```

- [ ] **Step 2: FXML ersetzen**

`src\main\resources\GeoTagger.fxml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>

<?import javafx.geometry.Insets?>
<?import javafx.scene.control.Button?>
<?import javafx.scene.control.CheckBox?>
<?import javafx.scene.control.Label?>
<?import javafx.scene.control.ProgressBar?>
<?import javafx.scene.control.Separator?>
<?import javafx.scene.control.SplitPane?>
<?import javafx.scene.control.TableColumn?>
<?import javafx.scene.control.TableView?>
<?import javafx.scene.control.ToolBar?>
<?import javafx.scene.layout.BorderPane?>
<?import javafx.scene.layout.HBox?>
<?import javafx.scene.layout.StackPane?>
<?import javafx.scene.layout.VBox?>

<BorderPane xmlns="http://javafx.com/javafx" xmlns:fx="http://javafx.com/fxml" fx:controller="tf.geotagger.main.GeoTagger">
    <top>
        <ToolBar>
            <Button fx:id="openFolderButton" text="Open folder..." onAction="#openFolder" disable="true"/>
            <CheckBox fx:id="includeSubfoldersCheck" text="Include subfolders"/>
            <Separator orientation="VERTICAL"/>
            <Button fx:id="preferencesButton" text="Preferences..." onAction="#showPreferences"/>
        </ToolBar>
    </top>
    <center>
        <SplitPane fx:id="centralSplit">
            <SplitPane fx:id="leftSplit" orientation="VERTICAL">
                <VBox>
                    <HBox fx:id="filterBox" spacing="4">
                        <padding>
                            <Insets top="4" right="4" bottom="4" left="4"/>
                        </padding>
                    </HBox>
                    <TableView fx:id="mediaTable" VBox.vgrow="ALWAYS">
                        <columns>
                            <TableColumn fx:id="nameColumn" text="Name" prefWidth="260"/>
                            <TableColumn fx:id="captureTimeColumn" text="Capture time" prefWidth="150"/>
                            <TableColumn fx:id="placeColumn" text="Place" prefWidth="140"/>
                            <TableColumn fx:id="coordinatesStatusColumn" text="GPS" prefWidth="45"/>
                            <TableColumn fx:id="placeStatusColumn" text="Loc." prefWidth="45"/>
                            <TableColumn fx:id="altitudeStatusColumn" text="Alt." prefWidth="45"/>
                        </columns>
                        <placeholder>
                            <Label text="Open a folder to see its photos and videos"/>
                        </placeholder>
                    </TableView>
                </VBox>
                <StackPane>
                    <Label text="The preview follows in the next step"/>
                </StackPane>
            </SplitPane>
            <StackPane>
                <Label text="Map, details and tracks follow in the next steps" wrapText="true"/>
            </StackPane>
        </SplitPane>
    </center>
    <bottom>
        <HBox spacing="8" alignment="CENTER_LEFT">
            <padding>
                <Insets top="4" right="8" bottom="4" left="8"/>
            </padding>
            <Label fx:id="statusLabel" maxWidth="Infinity" HBox.hgrow="ALWAYS"/>
            <ProgressBar fx:id="progressBar" prefWidth="200" visible="false"/>
        </HBox>
    </bottom>
</BorderPane>
```

- [ ] **Step 3: Controller ersetzen**

`src\main\java\tf\geotagger\main\GeoTagger.java`:

```java
package tf.geotagger.main;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import tf.exiftool.ExifTool;
import tf.exiftool.ExifToolException;
import tf.geotagger.files.FolderScanner;
import tf.geotagger.metadata.ExifToolLocator;
import tf.geotagger.metadata.MetadataReader;
import tf.geotagger.model.CaptureTime;
import tf.geotagger.model.Field;
import tf.geotagger.model.MediaFile;
import tf.geotagger.model.MediaFilter;
import tf.helper.javafx.ShowAlerts;
import tf.helper.javafx.TableViewPreferences;

/**
 * Controller of the main window (GeoTagger.fxml): ExifTool start, folder loading, file list with filters.
 */
public class GeoTagger implements Initializable {
    private static final String TABLE_PREFERENCES = "mediaTable";
    private static final DateTimeFormatter CAPTURE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String YES = "\u2713";
    private static final String NO = "\u2013";

    @FXML
    private Button openFolderButton;
    @FXML
    private CheckBox includeSubfoldersCheck;
    @FXML
    private Button preferencesButton;
    @FXML
    private SplitPane centralSplit;
    @FXML
    private SplitPane leftSplit;
    @FXML
    private HBox filterBox;
    @FXML
    private TableView<MediaFile> mediaTable;
    @FXML
    private TableColumn<MediaFile, String> nameColumn;
    @FXML
    private TableColumn<MediaFile, String> captureTimeColumn;
    @FXML
    private TableColumn<MediaFile, String> placeColumn;
    @FXML
    private TableColumn<MediaFile, String> coordinatesStatusColumn;
    @FXML
    private TableColumn<MediaFile, String> placeStatusColumn;
    @FXML
    private TableColumn<MediaFile, String> altitudeStatusColumn;
    @FXML
    private Label statusLabel;
    @FXML
    private ProgressBar progressBar;

    private final ObservableList<MediaFile> files = FXCollections.observableArrayList();
    private final FilteredList<MediaFile> filteredFiles = new FilteredList<>(files);
    private final ToggleGroup filterGroup = new ToggleGroup();
    private final Map<MediaFilter, ToggleButton> filterButtons = new EnumMap<>(MediaFilter.class);
    // one loader thread - ExifTool handles one command at a time anyway
    private final ExecutorService loader = Executors.newSingleThreadExecutor(runnable -> {
        final Thread thread = new Thread(runnable, "GeoTagger-loader");
        thread.setDaemon(true);
        return thread;
    });

    private Stage stage;
    // folder the files were loaded from - names are shown relative to it
    private Path folder;
    private ExifTool exifTool;
    private MetadataReader reader;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        initTable();
        initFilters();
    }

    /**
     * Called once the stage is shown: restores the layout and starts ExifTool.
     *
     * @param stage the main window
     */
    public void lateInitialize(Stage stage) {
        this.stage = stage;
        final Boolean includeSubfolders = GeoTaggerPreferences.INCLUDE_SUBFOLDERS.getAsType();
        includeSubfoldersCheck.setSelected(includeSubfolders);
        final Double centralDivider = GeoTaggerPreferences.RECENT_CENTRAL_DIVIDER_POS.getAsType();
        centralSplit.setDividerPositions(centralDivider);
        final Double leftDivider = GeoTaggerPreferences.RECENT_LEFT_DIVIDER_POS.getAsType();
        leftSplit.setDividerPositions(leftDivider);
        TableViewPreferences.loadTableViewPreferences(mediaTable, TABLE_PREFERENCES, GeoTaggerPreferences.INSTANCE);
        initExifTool();
    }

    /**
     * Saves the layout and stops background work - called when GeoTagger ends.
     */
    public void stop() {
        GeoTaggerPreferences.RECENT_CENTRAL_DIVIDER_POS.put(centralSplit.getDividerPositions()[0]);
        GeoTaggerPreferences.RECENT_LEFT_DIVIDER_POS.put(leftSplit.getDividerPositions()[0]);
        GeoTaggerPreferences.INCLUDE_SUBFOLDERS.put(includeSubfoldersCheck.isSelected());
        TableViewPreferences.saveTableViewPreferences(mediaTable, TABLE_PREFERENCES, GeoTaggerPreferences.INSTANCE);
        loader.shutdownNow();
        closeExifTool();
    }

    private void initTable() {
        final SortedList<MediaFile> sortedFiles = new SortedList<>(filteredFiles);
        sortedFiles.comparatorProperty().bind(mediaTable.comparatorProperty());
        mediaTable.setItems(sortedFiles);
        mediaTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        nameColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(displayName(cell.getValue())));
        captureTimeColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(captureTime(cell.getValue())));
        placeColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(place(cell.getValue())));
        coordinatesStatusColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(status(cell.getValue().hasCoordinates())));
        placeStatusColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(status(cell.getValue().hasPlace())));
        altitudeStatusColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(status(cell.getValue().hasAltitude())));
    }

    private void initFilters() {
        for (MediaFilter filter : MediaFilter.values()) {
            final ToggleButton button = new ToggleButton(filter.label());
            button.setToggleGroup(filterGroup);
            button.setUserData(filter);
            filterButtons.put(filter, button);
            filterBox.getChildren().add(button);
        }
        filterGroup.selectedToggleProperty().addListener((observable, oldToggle, newToggle) -> {
            if (newToggle == null) {
                // one filter is always active
                oldToggle.setSelected(true);
                return;
            }
            filteredFiles.setPredicate((MediaFilter) newToggle.getUserData());
        });
        filterButtons.get(MediaFilter.ALL).setSelected(true);
        updateFilterCounts();
    }

    private void updateFilterCounts() {
        MediaFilter.counts(files).forEach((filter, count) -> filterButtons.get(filter).setText(filter.label() + " (" + count + ")"));
    }

    private void initExifTool() {
        if (GeoTaggerPreferences.EXIFTOOL_PATH.getAsString().isBlank()) {
            ExifToolLocator.findOnPath().ifPresent(found -> GeoTaggerPreferences.EXIFTOOL_PATH.put(found.toString()));
        }
        if (!applyExifTool()) {
            ShowAlerts.getInstance().showAlert(Alert.AlertType.WARNING, GeoTaggerManager.TITLE, "ExifTool not found",
                    "GeoTagger reads and writes metadata with ExifTool. Please select the ExifTool executable.");
            showPreferences();
        }
    }

    /**
     * (Re)starts ExifTool with the executable from the preferences.
     *
     * @return true if ExifTool is usable - otherwise no folder can be opened
     */
    private boolean applyExifTool() {
        closeExifTool();
        final String configured = GeoTaggerPreferences.EXIFTOOL_PATH.getAsString();
        try {
            final Path executable = Path.of(configured);
            final String version = ExifToolLocator.version(executable);
            exifTool = new ExifTool(executable);
            reader = new MetadataReader(exifTool);
            openFolderButton.setDisable(false);
            statusLabel.setText("ExifTool " + version + " - " + executable);
            return true;
        } catch (ExifToolException | InvalidPathException ex) {
            openFolderButton.setDisable(true);
            statusLabel.setText("ExifTool not usable - please check the preferences");
            return false;
        }
    }

    private void closeExifTool() {
        if (exifTool != null) {
            exifTool.close();
            exifTool = null;
            reader = null;
        }
    }

    @FXML
    private void showPreferences() {
        if (PreferenceEditor.getInstance().editPreferences()) {
            applyExifTool();
        }
    }

    @FXML
    private void openFolder() {
        final DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Open folder");
        final File recent = new File(GeoTaggerPreferences.RECENT_FOLDER.getAsString());
        if (recent.isDirectory()) {
            chooser.setInitialDirectory(recent);
        }
        final File selected = chooser.showDialog(stage);
        if (selected != null) {
            GeoTaggerPreferences.RECENT_FOLDER.put(selected.getAbsolutePath());
            GeoTaggerPreferences.INCLUDE_SUBFOLDERS.put(includeSubfoldersCheck.isSelected());
            loadFolder(selected.toPath(), includeSubfoldersCheck.isSelected());
        }
    }

    private void loadFolder(Path newFolder, boolean includeSubfolders) {
        final MetadataReader folderReader = reader;
        final Task<List<MediaFile>> task = new Task<>() {
            @Override
            protected List<MediaFile> call() throws IOException {
                updateMessage("Searching " + newFolder + " ...");
                final List<Path> mediaFiles = FolderScanner.scan(newFolder, includeSubfolders).mediaFiles();
                final int total = mediaFiles.size();
                updateProgress(0, total);
                return folderReader.read(mediaFiles, done -> {
                    updateProgress(done, total);
                    updateMessage("Reading metadata: " + done + " / " + total);
                });
            }
        };
        task.setOnSucceeded(event -> {
            finishLoading();
            folder = newFolder;
            files.setAll(task.getValue());
            updateFilterCounts();
            stage.setTitle(GeoTaggerManager.TITLE + " - " + newFolder);
            statusLabel.setText(files.size() + " files in " + newFolder);
            select(0);
        });
        task.setOnFailed(event -> {
            finishLoading();
            statusLabel.setText("Loading " + newFolder + " failed");
            ShowAlerts.getInstance().showAlert(Alert.AlertType.ERROR, GeoTaggerManager.TITLE, "Can't load " + newFolder,
                    String.valueOf(task.getException().getMessage()));
        });

        statusLabel.textProperty().bind(task.messageProperty());
        progressBar.progressProperty().bind(task.progressProperty());
        progressBar.setVisible(true);
        setBusy(true);
        loader.submit(task);
    }

    private void finishLoading() {
        statusLabel.textProperty().unbind();
        progressBar.progressProperty().unbind();
        progressBar.setVisible(false);
        setBusy(false);
    }

    private void setBusy(boolean busy) {
        openFolderButton.setDisable(busy);
        includeSubfoldersCheck.setDisable(busy);
        preferencesButton.setDisable(busy);
    }

    private void select(int index) {
        if (index >= 0 && index < mediaTable.getItems().size()) {
            mediaTable.getSelectionModel().clearAndSelect(index);
            mediaTable.scrollTo(index);
        }
    }

    private String displayName(MediaFile file) {
        // with sub folders the path below the opened folder tells the files apart
        return folder == null ? file.path().getFileName().toString() : folder.relativize(file.path()).toString();
    }

    private static String captureTime(MediaFile file) {
        final CaptureTime time = file.captureTime();
        if (time == null) {
            return "";
        }
        return CAPTURE_TIME.format(time.dateTime()) + (time.utc() ? " UTC" : "");
    }

    private static String place(MediaFile file) {
        final String city = file.text(Field.CITY);
        return city != null ? city : Objects.requireNonNullElse(file.text(Field.COUNTRY), "");
    }

    private static String status(boolean present) {
        return present ? YES : NO;
    }
}
```

Hinweis: `applyExifTool()` hat einen Javadoc-Kommentar, obwohl die Methode privat ist. Er ist vollständig (`@return`). Das ist in Ordnung.

- [ ] **Step 4: Bauen und testen**

Run: `.\gradlew.bat build`
Expected: BUILD SUCCESSFUL, 101 Tests grün, keine Compilerwarnungen.

- [ ] **Step 5: Manuell prüfen**

Run: `.\gradlew.bat run`
Prüfen:
1. **Erster Start:** Der ExifTool-Pfad wird aus dem PATH übernommen. Die Statuszeile zeigt „ExifTool 13.38 - …\exiftool.exe“, und „Open folder...“ ist aktiv.
2. **Einstellungen:**
   - „Preferences...“ öffnen, einen falschen Pfad eintragen und „Check“ drücken: „Not usable: …“ erscheint.
   - Mit „Save“ speichern: Die Statuszeile zeigt „ExifTool not usable …“, „Open folder...“ ist gesperrt.
   - Erneut öffnen, mit „Browse...“ `exiftool.exe` wählen: „ExifTool 13.38“ erscheint, Speichern gibt „Open folder...“ wieder frei.
3. **Ordner laden:** „Open folder...“ auf `src\test\resources\samples`.
   - Fortschrittsbalken und Statustext laufen, danach stehen 4 Dateien in der Liste. Die Aufnahmezeit des MOV ist Ortszeit, also ohne „UTC“, weil es `DateTimeOriginal` hat.
   - Die Status-Spalten zeigen ✓ bzw. –.
   - Die Filter zeigen Zahlen. „Without coordinates“ blendet entsprechend aus, es bleibt immer ein Filter aktiv.
   - Sortieren per Spaltenkopf funktioniert.
4. **Unterordner:** „Include subfolders“ an und einen Ordner mit Unterordnern laden. Die Namen erscheinen dann mit relativem Pfad, z. B. `MOV\clip.mov`.
5. **Großer Ordner:** einen echten Urlaubsordner laden. Das Fenster bleibt während des Ladens bedienbar und verschiebbar, und der Fortschritt springt in Hunderterschritten.
6. **Neustart:** Spaltenbreiten und -reihenfolge, die Teilungen und das Häkchen „Include subfolders“ sind erhalten. „Open folder...“ startet im zuletzt geöffneten Ordner.

- [ ] **Step 6: Commit**

```powershell
& $git -C $app add src
& $git -C $app commit -m "Main window: preferences, open folder in background, file list with filters"
```

---

### Task 7: Große Vorschau

**Files:**
- Create: `src\main\java\tf\geotagger\main\PreviewPane.java`
- Modify: `src\main\resources\GeoTagger.fxml`, `src\main\java\tf\geotagger\main\GeoTagger.java`

**Interfaces:**
- Consumes: `PreviewProvider`, `Preview` (Task 5); im Controller `select(int)` und `applyExifTool()` (Task 6)
- Produces: `public class PreviewPane extends BorderPane`
  - `void setPreviewProvider(PreviewProvider)`
  - `void show(MediaFile, List<MediaFile> neighbours)`
  - `void clear()`
  - `void dispose()`
  - `Button getFirstButton()`, `getPreviousButton()`, `getNextButton()`, `getLastButton()`

- [ ] **Step 1: `PreviewPane` schreiben**

`src\main\java\tf\geotagger\main\PreviewPane.java`:

```java
package tf.geotagger.main;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import tf.geotagger.model.MediaFile;
import tf.geotagger.preview.Preview;
import tf.geotagger.preview.PreviewProvider;

/**
 * Large preview of the selected file with navigation buttons - like GeoSetter's image preview.
 * Fitted previews are scaled down to PREVIEW_SIZE and cached, "100 %" loads the image in full resolution.
 */
public class PreviewPane extends BorderPane {
    // scaled previews keep the memory bounded - a 50 MP image needs 200 MB in full resolution
    private static final double PREVIEW_SIZE = 2560;
    private static final int CACHE_SIZE = 8;

    private final Button firstButton = new Button("|<");
    private final Button previousButton = new Button("<");
    private final Button nextButton = new Button(">");
    private final Button lastButton = new Button(">|");
    private final ToggleButton actualSizeButton = new ToggleButton("100 %");
    private final ImageView imageView = new ImageView();
    private final Label placeholder = new Label();
    private final ScrollPane scrollPane = new ScrollPane();
    // least recently used previews - the neighbours of the selected file are put here in advance
    private final Map<Path, Image> cache = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Path, Image> eldest) {
            return size() > CACHE_SIZE;
        }
    };
    // previews are extracted with ExifTool - one at a time, in the order of the requests
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        final Thread thread = new Thread(runnable, "GeoTagger-preview");
        thread.setDaemon(true);
        return thread;
    });

    private PreviewProvider provider;
    // file requested last - answers for older requests are dropped
    private Path current;
    private Preview currentPreview;

    public PreviewPane() {
        final HBox toolbar = new HBox(4, firstButton, previousButton, nextButton, lastButton,
                new Separator(Orientation.VERTICAL), actualSizeButton);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(4));
        setTop(toolbar);

        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        // a Group takes the rotation of the image into account for its size
        final StackPane content = new StackPane(new Group(imageView), placeholder);
        content.minWidthProperty().bind(Bindings.createDoubleBinding(
                () -> scrollPane.getViewportBounds().getWidth(), scrollPane.viewportBoundsProperty()));
        content.minHeightProperty().bind(Bindings.createDoubleBinding(
                () -> scrollPane.getViewportBounds().getHeight(), scrollPane.viewportBoundsProperty()));
        scrollPane.setContent(content);
        setCenter(scrollPane);

        scrollPane.viewportBoundsProperty().addListener((observable, oldValue, newValue) -> updateFit());
        actualSizeButton.selectedProperty().addListener((observable, oldValue, newValue) -> {
            if (currentPreview != null) {
                display(currentPreview);
            }
        });
        clear();
    }

    /**
     * @return button to show the first file of the list
     */
    public Button getFirstButton() {
        return firstButton;
    }

    /**
     * @return button to show the previous file of the list
     */
    public Button getPreviousButton() {
        return previousButton;
    }

    /**
     * @return button to show the next file of the list
     */
    public Button getNextButton() {
        return nextButton;
    }

    /**
     * @return button to show the last file of the list
     */
    public Button getLastButton() {
        return lastButton;
    }

    /**
     * @param provider source of the previews - null while ExifTool isn't usable
     */
    public void setPreviewProvider(PreviewProvider provider) {
        this.provider = provider;
    }

    /**
     * Shows the preview of a file - the extraction runs in the background, the neighbours are prepared afterwards.
     *
     * @param file file to show
     * @param neighbours files the user will probably look at next (previous / next in the list)
     */
    public void show(MediaFile file, List<MediaFile> neighbours) {
        final Path path = file.path();
        current = path;
        currentPreview = null;
        imageView.setImage(null);
        if (provider == null) {
            showMessage("No preview - ExifTool not usable");
            return;
        }
        showMessage("Loading " + path.getFileName() + " ...");
        final PreviewProvider requestProvider = provider;
        executor.submit(() -> {
            final Optional<Preview> preview = load(requestProvider, path);
            Platform.runLater(() -> {
                if (path.equals(current)) {
                    if (preview.isPresent()) {
                        display(preview.get());
                    } else {
                        showMessage("No preview for " + path.getFileName());
                    }
                }
            });
            for (MediaFile neighbour : neighbours) {
                load(requestProvider, neighbour.path()).ifPresent(this::scaled);
            }
        });
    }

    /**
     * Shows nothing - e.g. when no file is selected.
     */
    public void clear() {
        current = null;
        currentPreview = null;
        imageView.setImage(null);
        showMessage("No file selected");
    }

    /**
     * Stops the background thread - called when GeoTagger ends.
     */
    public void dispose() {
        executor.shutdownNow();
    }

    private static Optional<Preview> load(PreviewProvider provider, Path path) {
        try {
            return provider.preview(path);
        } catch (RuntimeException ex) {
            // ExifTool or file problems - then there is just no preview
            return Optional.empty();
        }
    }

    private void showMessage(String message) {
        placeholder.setText(message);
        placeholder.setVisible(true);
    }

    private void display(Preview preview) {
        currentPreview = preview;
        final Image image = actualSizeButton.isSelected()
                // full resolution only on request - not cached
                ? new Image(preview.image().toUri().toString(), true)
                : scaled(preview);
        imageView.setImage(image);
        imageView.setRotate(preview.rotation());
        placeholder.setVisible(false);
        if (image.getProgress() < 1.0) {
            image.errorProperty().addListener((observable, oldValue, error) -> {
                if (error && image == imageView.getImage()) {
                    showMessage("Can't show " + preview.image().getFileName());
                }
            });
        } else if (image.isError()) {
            showMessage("Can't show " + preview.image().getFileName());
        }
        updateFit();
    }

    private synchronized Image scaled(Preview preview) {
        return cache.computeIfAbsent(preview.image(),
                path -> new Image(path.toUri().toString(), PREVIEW_SIZE, PREVIEW_SIZE, true, true, true));
    }

    private void updateFit() {
        if (actualSizeButton.isSelected()) {
            imageView.setFitWidth(0);
            imageView.setFitHeight(0);
            return;
        }
        final Bounds viewport = scrollPane.getViewportBounds();
        // rotated by 90 / 270 degrees: width and height of the image swap
        final boolean quarterTurn = Math.abs(imageView.getRotate()) % 180 == 90;
        imageView.setFitWidth(quarterTurn ? viewport.getHeight() : viewport.getWidth());
        imageView.setFitHeight(quarterTurn ? viewport.getWidth() : viewport.getHeight());
    }
}
```

- [ ] **Step 2: FXML anpassen**

In `src\main\resources\GeoTagger.fxml` kommt nach `<?import javafx.scene.layout.VBox?>` diese Zeile dazu:

```xml
<?import tf.geotagger.main.PreviewPane?>
```

Und dieser Block wird ersetzt:

```xml
                <StackPane>
                    <Label text="The preview follows in the next step"/>
                </StackPane>
```

durch:

```xml
                <PreviewPane fx:id="previewPane"/>
```

- [ ] **Step 3: Controller anpassen**

In `src\main\java\tf\geotagger\main\GeoTagger.java`:

1. **Imports.** Nach `import java.time.format.DateTimeFormatter;` diese Zeile einfügen:
   ```java
   import java.util.ArrayList;
   ```
   Nach `import tf.geotagger.model.MediaFilter;` diese Zeile einfügen:
   ```java
   import tf.geotagger.preview.PreviewProvider;
   ```
2. **Konstante.** Nach `private static final String NO = "\u2013";` einfügen:
   ```java
       // extracted previews of RAW / HEIC / videos - emptied when GeoTagger ends
       private static final Path PREVIEW_CACHE = Path.of(System.getProperty("java.io.tmpdir"), "GeoTagger", "preview");
   ```
3. **Felder.** Nach
   ```java
       @FXML
       private ProgressBar progressBar;
   ```
   einfügen:
   ```java
       @FXML
       private PreviewPane previewPane;
   ```
   Und nach `private MetadataReader reader;` einfügen:
   ```java
       private PreviewProvider previewProvider;
   ```
4. **`initialize`.** `initFilters();` wird zu:
   ```java
           initFilters();
           initPreview();
   ```
5. **`stop`.** Diese beiden Zeilen am Ende von `stop()` (eindeutig, weil `closeExifTool();` direkt auf `loader.shutdownNow();` folgt):
   ```java
           loader.shutdownNow();
           closeExifTool();
   ```
   werden zu:
   ```java
           loader.shutdownNow();
           previewPane.dispose();
           closeExifTool();
           if (previewProvider != null) {
               previewProvider.clearCache();
           }
   ```
6. **`applyExifTool`.** Nach `reader = new MetadataReader(exifTool);` einfügen:
   ```java
               previewProvider = new PreviewProvider(exifTool, PREVIEW_CACHE);
               previewPane.setPreviewProvider(previewProvider);
   ```
   Im `catch`-Block vor `openFolderButton.setDisable(true);` einfügen:
   ```java
               previewPane.setPreviewProvider(null);
   ```
7. **Neue Methoden.** Diese Methoden kommen nach `updateFilterCounts()`:
   ```java
       private void initPreview() {
           mediaTable.getSelectionModel().selectedItemProperty().addListener((observable, oldFile, newFile) -> showPreview(newFile));
           previewPane.getFirstButton().setOnAction(event -> select(0));
           previewPane.getPreviousButton().setOnAction(event -> select(mediaTable.getSelectionModel().getSelectedIndex() - 1));
           previewPane.getNextButton().setOnAction(event -> select(mediaTable.getSelectionModel().getSelectedIndex() + 1));
           previewPane.getLastButton().setOnAction(event -> select(mediaTable.getItems().size() - 1));
       }

       private void showPreview(MediaFile file) {
           if (file == null) {
               previewPane.clear();
               return;
           }
           final List<MediaFile> items = mediaTable.getItems();
           final int index = items.indexOf(file);
           final List<MediaFile> neighbours = new ArrayList<>();
           if (index > 0) {
               neighbours.add(items.get(index - 1));
           }
           if (index >= 0 && index + 1 < items.size()) {
               neighbours.add(items.get(index + 1));
           }
           previewPane.show(file, neighbours);
       }
   ```

- [ ] **Step 4: Bauen und testen**

Run: `.\gradlew.bat build`
Expected: BUILD SUCCESSFUL, 101 Tests grün, keine Compilerwarnungen.

- [ ] **Step 5: Manuell prüfen**

Run: `.\gradlew.bat run`, dann `src\test\resources\samples` öffnen.
Prüfen:
1. Das erste Bild ist ausgewählt, und seine Vorschau füllt den Bereich unter der Liste, eingepasst und ohne Verzerrung.
2. Beim CR2 erscheint die Vorschau über das eingebettete `PreviewImage`, beim MOV das kleine Canon-Vorschaubild. Ein Video ohne eingebettetes Bild zeigt „No preview for …“.
3. Ein Hochformatfoto vom Smartphone mit Orientierung 6 steht aufrecht. Falls keins zur Hand ist, eine JPEG-Kopie mit `exiftool -IFD0:Orientation#=6` drehen.
4. Mit `|<` `<` `>` `>|` blättern. Die Auswahl in der Liste folgt, und das nächste Bild erscheint praktisch sofort, weil es vorab geladen wurde.
5. Fenster bzw. Teilung verändern: Das Bild passt sich an. Mit „100 %“ erscheint es in voller Auflösung mit Scrollbalken, ein erneuter Klick passt es wieder ein.
6. Mit der Pfeiltaste schnell durch die Liste laufen: Am Ende steht das Bild der zuletzt gewählten Datei, keine veraltete Vorschau.
7. GeoTagger beenden: `%TEMP%\GeoTagger\preview` ist leer.

- [ ] **Step 6: Commit**

```powershell
& $git -C $app add src
& $git -C $app commit -m "Large preview with navigation, fit / 100 %, rotation and prefetch"
```

---

## Abschluss von Plan 3a

- `.\gradlew.bat build` in `$app`: Alle 101 Tests sind grün, auch die mit den echten Musterdateien, und es gibt keine Compilerwarnungen.
- Die manuellen Checklisten der Tasks 1, 6 und 7 sind abgehakt.
- Danach Plan 3b (Details, Bild-Info, Undo/Redo, Speichern) auf Basis des entstandenen Codes schreiben.

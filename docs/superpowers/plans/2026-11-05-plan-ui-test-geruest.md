# GeoTagger: UI-Test-Gerüst – Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** GeoTagger bekommt automatisierte UI-Tests mit TestFX und getrennte Testsammlungen. `gradlew test` lässt nur die schnellen Tests ohne UI laufen, `gradlew uiTest` nur die UI-Tests. Die Suites `NonUITests` und `UITests` sind aus NetBeans startbar. Die Funktionen aus Plan 3a (Start, Ordner laden, Filter) sind damit abgedeckt.

**Architecture:**
- **Erkennung:** UI-Testklassen tragen `@Tag("UI")`.
- **Gradle:** Der Task `test` schließt ohne `--tests`-Filter den Tag `UI` und die Suite-Engine aus, damit nichts doppelt läuft. Mit Filter, also beim Start einer Klasse oder Suite aus NetBeans, läuft genau das Gewählte. Der neue Task `uiTest` lässt nur `UI` laufen.
- **Isolierte Preferences:** In allen Test-JVMs lenkt die Systemproperty `geotagger.preferences` die Preferences auf einen eigenen Knoten. Die echten Einstellungen bleiben unberührt.
- **UI-Tests:** Sie starten die echte Anwendung über `GeoTaggerManager.start(stage)`, finden Elemente über IDs und laden Ordner über eine paketinterne Methode statt über den nativen Dialog. Sie warten auf Zustände statt auf Zeit.

**Tech Stack:** TestFX 4.0.18 (`testfx-junit5`), Hamcrest 3.0 (wird von TestFX zum Kompilieren gebraucht), JUnit 5.13.0 inkl. `junit-platform-suite`, Java 25, JavaFX 25.0.1.

**Spec:** [2026-11-03-geotagger-design.md](../specs/2026-11-03-geotagger-design.md), Abschnitt „Tests“. Der Plan ändert dort den Punkt „UI: manueller Test“.

## Global Constraints

- **Lizenz-Header** in jeder `.java`-Datei, auch in den Suites und Testhilfen. **Javadoc** nur vollständig.
- Gearbeitet wird direkt auf `master` in `D:\Thomas\Development\NetBeansProjects\GeoTagger`. Git über `$git = 'C:\Users\thomas\AppData\Local\GitHubDesktop\app-3.6.7\resources\app\git\cmd\git.exe'`.
- **Festgelegt durch den Probelauf vom 2026-11-05** im Scratch-Projekt `uiprobe`, Windows mit Java 25 und JavaFX 25.0.1:
  - TestFX 4.0.18 läuft mit `ApplicationExtension`, `@Start` und `@Stop`. Jeder Test bekommt eine frische Stage, und `@Stop` wird nach jedem Test aufgerufen.
  - Ohne `org.hamcrest:hamcrest` kompilieren `lookup(...)`-Aufrufe nicht: „cannot access Matcher“.
  - Mit JavaFX auf dem Module-Path braucht TestFX `--add-opens javafx.graphics/com.sun.javafx.application=ALL-UNNAMED`. Das gilt nur für die Test-JVMs, nicht für `run`.
  - Das Testfenster muss während des Tests `setAlwaysOnTop(true)` bleiben. Sonst klickt der Robot ins Leere, weil Windows ein aus Gradle oder Claude gestartetes Fenster nicht nach vorne lässt.
  - `gradle.startParameter.taskRequests.any { it.args.contains('--tests') }` unterscheidet zuverlässig den ungefilterten Lauf vom Start einer Klasse oder Suite.
  - Gradle 9: Properties in Closures nur mit `=` zuweisen, z. B. `testLogging.showStandardStreams = true`. Die Leerzeichen-Syntax ist deprecated.
- **Regeln für UI-Tests**, damit sie nicht so pflegeaufwendig werden wie in OwnNoteEditor:
  - Ein Test pro Szenario, ohne Abhängigkeit von anderen Tests.
  - Elemente nur über IDs (`#...`) und Modelldaten finden, keine Koordinaten, keine Indizes, keine Tastenfolgen für Menüs.
  - Warten mit `WaitForAsyncUtils.waitFor(...)` auf einen Zustand, nie mit `sleep`.
  - Testdaten in `@TempDir`.
  - Native Dateidialoge werden nicht bedient, Ordner werden über `GeoTagger.loadFolder(Path, boolean)` geladen.
- **Während `uiTest` Maus und Tastatur nicht benutzen,** weil der Robot die echte Maus bewegt.

## Review Focus

1. **Echte Einstellungen bleiben unberührt:** Nach `uiTest` sind Fenstergröße, ExifTool-Pfad und die Liste der zuletzt geöffneten Ordner der echten Anwendung unverändert. Test: `GeoTaggerPreferencesTest.testsUseTheirOwnNode` in Task 2.
2. **Kein Doppellauf:** `gradlew test` führt jeden Test genau einmal aus, auch mit den Suite-Klassen im Klassenpfad. Prüfung: Verifikationsschritt in Task 1 (Anzahl gleich 101).
3. **UI-Tests ohne ExifTool:** Fehlt ExifTool, werden die UI-Tests übersprungen statt zu hängen. Ohne gültigen Pfad würde sonst der modale Dialog „ExifTool not found“ den Test blockieren. Prüfung: `assumeTrue` in `@BeforeAll`, Task 3.
4. **Einzelstart aus NetBeans:** Ein in NetBeans gestarteter einzelner UI-Test läuft mit Module-Path und `--add-opens`, nicht nur `uiTest`. Prüfung: Verifikationsschritt in Task 4 (`test --tests tf.geotagger.main.GeoTaggerUiTest`).
5. **Laden fertig, aber Liste noch alt:** Ein UI-Test sieht nach dem Laden eine Zwischenstand-Liste. Dagegen wartet `waitUntilIdle` erst auf `!isBusy()` und danach auf `WaitForAsyncUtils.waitForFxEvents()`. Test: Zeilenzahl-Prüfung in `loadingFolderFillsListAndCounts`, Task 4.

---

### Task 1: Gradle-Aufteilung und Suites

**Files:**
- Modify: `build.gradle`
- Create: `src\test\java\tf\geotagger\testsuites\NonUITests.java`, `UITests.java`

**Interfaces:**
- Produces:
  - `gradlew test`: ohne `--tests` nur Tests ohne `@Tag("UI")`, ohne Suite-Engine; mit `--tests` genau das Gewählte.
  - `gradlew uiTest`: nur `@Tag("UI")`.
  - Beide Test-JVMs: JavaFX auf dem Module-Path, `jvmFlags` wie `run`, dazu `--add-opens javafx.graphics/com.sun.javafx.application=ALL-UNNAMED` und `-Dgeotagger.preferences=tf/geotagger/test`.
  - Suites `tf.geotagger.testsuites.NonUITests` und `UITests`.

- [ ] **Step 1: Abhängigkeiten ergänzen**

Im Block `dependencies` nach `testImplementation 'org.junit.jupiter:junit-jupiter-params'`:

```groovy
    // test collections (NonUITests / UITests) - same approach as GPXEditor
    testImplementation 'org.junit.platform:junit-platform-suite'
    // UI tests - TestFX needs hamcrest to compile lookup(...) calls
    testImplementation 'org.testfx:testfx-junit5:4.0.18'
    testImplementation 'org.hamcrest:hamcrest:3.0'
```

- [ ] **Step 2: Test-Tasks konfigurieren**

Der bisherige Block `tasks.named('test', Test) { ... }` wird **gelöscht**. Am Ende von `build.gradle`, also nach `tasks.named('run', JavaExec) { ... }`, weil `jvmFlags` und `javafxJars` dort schon definiert sind, kommt Folgendes dazu:

```groovy
// ---------------------------------------------------------------------------------------------------------------------
// Tests - see docs/superpowers/plans/2026-11-05-plan-ui-test-geruest.md
//   gradlew test                      -> all tests without @Tag("UI") - fast, no windows
//   gradlew uiTest                    -> only @Tag("UI") - opens windows, the robot moves the mouse: hands off!
//   gradlew test --tests <class>      -> exactly that class or suite (e.g. from NetBeans), UI tests included
//   suites for NetBeans: tf.geotagger.testsuites.NonUITests / UITests
// ---------------------------------------------------------------------------------------------------------------------
// with --tests (a single class or suite) everything selected runs; without it the UI tests and the suites are left out
// - otherwise the suites would run all tests a second time
def testsFiltered = gradle.startParameter.taskRequests.any { it.args.contains('--tests') }
// TestFX reads the application parameters via JavaFX internals
def testJvmFlags = jvmFlags + ['--add-opens', 'javafx.graphics/com.sun.javafx.application=ALL-UNNAMED']

tasks.withType(Test).configureEach {
    jvmArgumentProviders.add(objects.newInstance(ModulePathArgumentProvider).tap { modulePath.from(javafxJars) })
    jvmArgs(testJvmFlags)
    // tests use their own preferences node - the real settings of GeoTagger stay untouched
    systemProperty 'geotagger.preferences', 'tf/geotagger/test'
    systemProperty 'java.awt.headless', 'false'
    testLogging.showStandardStreams = true
}

tasks.named('test', Test) {
    useJUnitPlatform {
        if (!testsFiltered) {
            excludeTags 'UI'
            excludeEngines 'junit-platform-suite'
        }
    }
}

tasks.register('uiTest', Test) {
    description = 'Runs the UI tests - opens windows, keep hands off mouse and keyboard.'
    group = 'verification'
    testClassesDirs = sourceSets.test.output.classesDirs
    classpath = sourceSets.test.runtimeClasspath
    useJUnitPlatform {
        includeTags 'UI'
        excludeEngines 'junit-platform-suite'
    }
}
```

Hinweis: `java.awt.headless` war bisher `true`. TestFX steuert den AWT-Robot, deshalb hier `false`. `ImageIO` in den Tests ohne UI funktioniert auch so.

Im Kopfkommentar von `build.gradle` den Abschnitt `Main tasks` erweitern um:

```groovy
//   gradlew uiTest -> UI tests (TestFX) - opens windows, keep hands off mouse and keyboard
```

- [ ] **Step 3: Suites anlegen**

`NonUITests` mit `@Suite`, `@SuiteDisplayName("All non-UI tests")`, `@SelectPackages("tf.geotagger")`, `@ExcludePackages("tf.geotagger.testsuites")` und `@ExcludeTags("UI")`. `UITests` gleich aufgebaut, aber mit `@SuiteDisplayName("All UI tests")` und `@IncludeTags("UI")` statt `@ExcludeTags`. Beide sind leere `public class` mit kurzem Javadoc, der sagt, wofür sie gedacht sind (Start aus NetBeans).

- [ ] **Step 4: Verifizieren**

Run: `.\gradlew.bat test --rerun-tasks`
Expected: BUILD SUCCESSFUL. Die Testberichte in `build\test-results\test` summieren sich auf **101** Tests, jeder genau einmal und ohne Suite-Einträge.

Run: `.\gradlew.bat test --tests tf.geotagger.testsuites.NonUITests --rerun-tasks`
Expected: BUILD SUCCESSFUL, wieder 101 Tests, diesmal unter „NonUITests > JUnit Jupiter > …“.

Run: `.\gradlew.bat uiTest`
Expected: BUILD SUCCESSFUL. Es gibt noch keine UI-Tests, daher keine Tests. Gradle 9 meldet bei 0 Tests keinen Fehler.

- [ ] **Step 5: Commit**

```powershell
& $git -C $app add build.gradle src
& $git -C $app commit -m "Test tasks: test without UI, uiTest for TestFX, suites NonUITests / UITests"
```

---

### Task 2: Eigener Preferences-Knoten für Tests

**Files:**
- Modify: `src\main\java\tf\geotagger\main\GeoTaggerPreferences.java`
- Test: `src\test\java\tf\geotagger\main\GeoTaggerPreferencesTest.java`

**Interfaces:**
- Produces: `GeoTaggerPreferences` speichert in `Preferences.userRoot().node(System.getProperty("geotagger.preferences"))`, wenn die Property gesetzt ist, sonst wie bisher in `Preferences.userNodeForPackage(GeoTaggerManager.class)`.

- [ ] **Step 1: Fehlschlagenden Test schreiben**

```java
class GeoTaggerPreferencesTest {
    @Test
    void testsUseTheirOwnNode() {
        final String key = "testKey-" + UUID.randomUUID();
        try {
            GeoTaggerPreferences.INSTANCE.put(key, "test value");

            assertEquals("test value", Preferences.userRoot().node("tf/geotagger/test").get(key, null));
            assertNull(Preferences.userNodeForPackage(GeoTaggerManager.class).get(key, null));
        } finally {
            GeoTaggerPreferences.INSTANCE.remove(key);
        }
    }
}
```

- [ ] **Step 2: Test laufen lassen, Fehler erwarten**

Run: `.\gradlew.bat test --tests tf.geotagger.main.GeoTaggerPreferencesTest`
Expected: FAIL. `get` im Test-Knoten liefert `null`, weil noch in den echten Knoten geschrieben wird.

**Achtung:** Dieser fehlschlagende Lauf schreibt den Testschlüssel einmal in die echten Preferences. Der `finally`-Block entfernt ihn wieder.

- [ ] **Step 3: Knoten aus der Systemproperty wählen**

`MYPREFERENCES` in `GeoTaggerPreferences` wird über eine private statische Methode `preferencesNode()` belegt:
- Mit gesetzter Property `geotagger.preferences` nimmt sie `Preferences.userRoot().node(value)`.
- Sonst wie bisher den Paketknoten.
- Kommentar: „tests use their own node (set by the Gradle test tasks) - the real settings stay untouched“.

`MYRECENTFOLDERS` bleibt danach, die Reihenfolge der statischen Felder ist wichtig.

- [ ] **Step 4: Tests laufen lassen**

Run: `.\gradlew.bat test --rerun-tasks`
Expected: BUILD SUCCESSFUL, 102 Tests.

- [ ] **Step 5: Commit**

```powershell
& $git -C $app add src
& $git -C $app commit -m "Preferences: own node for tests via system property geotagger.preferences"
```

---

### Task 3: Testzugänge, IDs und UI-Testhilfe

**Files:**
- Modify:
  - `src\main\java\tf\geotagger\main\GeoTaggerManager.java`
  - `src\main\java\tf\geotagger\main\GeoTagger.java`
- Create: `src\test\java\tf\geotagger\main\UiTestSupport.java`

**Interfaces:**
- Produces:
  - in `GeoTaggerManager`: paketintern `GeoTagger controller()`
  - in `GeoTagger`:
    - `loadFolder(Path, boolean)` wird paketintern (bisher privat)
    - neu paketintern `boolean isBusy()`
    - das Feld `busy` wird `volatile`, weil der Testthread es liest
  - IDs der Filter-Buttons: `"filter-" + filter.name()`, z. B. `#filter-WITHOUT_COORDINATES`. Gesetzt in `initFilters()` mit `button.setId(...)`.
  - `UiTestSupport` (Test, Paket `tf.geotagger.main`):
    - `static void preparePreferences(Path exifTool)`: leert den Test-Knoten (`GeoTaggerPreferences.INSTANCE.clear()`) und setzt `EXIFTOOL_PATH`.
    - `static GeoTaggerManager start(Stage stage) throws IOException`: startet `new GeoTaggerManager().start(stage)` und hält das Fenster mit `setAlwaysOnTop(true)` vorne.
    - `static void waitUntilIdle(GeoTagger controller) throws TimeoutException`: `WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS, () -> !controller.isBusy())`, danach `WaitForAsyncUtils.waitForFxEvents()`.

- [ ] **Step 1: Änderungen umsetzen**

Wie unter „Produces“ beschrieben. `isBusy()` und `controller()` bekommen einen kurzen Kommentar „for UI tests“, `loadFolder` einen Kommentar, dass Tests es statt des Ordnerdialogs nutzen.

- [ ] **Step 2: Bauen**

Run: `.\gradlew.bat build`
Expected: BUILD SUCCESSFUL, 102 Tests, keine Compilerwarnungen.

- [ ] **Step 3: Commit**

```powershell
& $git -C $app add src
& $git -C $app commit -m "Test access for UI tests: controller, loadFolder, isBusy, ids of the filter buttons"
```

---

### Task 4: UI-Tests für Start, Ordner laden und Filter

**Files:**
- Test: `src\test\java\tf\geotagger\main\GeoTaggerUiTest.java`

**Interfaces:**
- Consumes: `UiTestSupport` (Task 3), `TestFiles.image`, `ExifToolTestSupport.require` (öffentliche Testhilfen aus `tf.geotagger.metadata`), `tf.exiftool.ExifTool`

- [ ] **Step 1: Test schreiben**

Gerüst der Klasse:
- `@Tag("UI")` und `@ExtendWith(ApplicationExtension.class)`.
- `@BeforeAll static void requireExifTool()`: setzt mit `exifTool = ExifToolTestSupport.require()` voraus, dass ExifTool vorhanden ist. Ohne ExifTool wird übersprungen.
- `@Start void start(Stage stage)`: ruft erst `UiTestSupport.preparePreferences(exifTool)` auf, dann `manager = UiTestSupport.start(stage)`, und setzt `controller = manager.controller()`.
- `@Stop void stop()`: ruft `manager.stop()` auf.
- `@TempDir Path dir` liefert die Testdaten.

Hilfsmethode `loadThreeImages(FxRobot robot)`:
1. Mit `TestFiles.image(dir, ...)` drei Bilder anlegen: `a.jpg`, `b.jpg` und `c.png`.
2. In `a.jpg` per ExifTool GPS (`-GPS:GPSLatitude#=46.5`, `-GPS:GPSLatitudeRef#=N`, `-GPS:GPSLongitude#=11.5`, `-GPS:GPSLongitudeRef#=E`) und `-XMP-photoshop:City=Meran` schreiben. Dafür eine eigene `ExifTool`-Instanz mit try-with-resources und `-overwrite_original`.
3. `robot.interact(() -> controller.loadFolder(dir, false))` aufrufen.
4. Mit `UiTestSupport.waitUntilIdle(controller)` warten.

Tests:

```java
    @Test
    void startShowsAnEmptyList(FxRobot robot) {
        // stage = the Stage passed to @Start, kept in a field
        assertEquals("GeoTagger", stage.getTitle());
        assertEquals("All (0)", robot.lookup("#filter-ALL").queryAs(ToggleButton.class).getText());
        assertFalse(robot.lookup("#openFolderButton").query().isDisabled());
        assertTrue(robot.lookup("#mediaTable").queryTableView().getItems().isEmpty());
    }

    @Test
    void loadingFolderFillsListAndCounts(FxRobot robot) throws Exception {
        loadThreeImages(robot);

        final TableView<MediaFile> table = robot.lookup("#mediaTable").queryTableView();
        assertEquals(3, table.getItems().size());
        assertEquals("All (3)", robot.lookup("#filter-ALL").queryAs(ToggleButton.class).getText());
        assertEquals("Without coordinates (2)", robot.lookup("#filter-WITHOUT_COORDINATES").queryAs(ToggleButton.class).getText());
        assertEquals("Without place (2)", robot.lookup("#filter-WITHOUT_PLACE").queryAs(ToggleButton.class).getText());
        assertEquals("3 files in " + dir, robot.lookup("#statusLabel").queryAs(Label.class).getText());
        // columns aren't nodes - found via the table, the cell data is the shown text
        final TableColumn<MediaFile, ?> gpsColumn = table.getColumns().stream()
                .filter(column -> "coordinatesStatusColumn".equals(column.getId())).findFirst().orElseThrow();
        assertEquals("\u2713", gpsColumn.getCellData(fileNamed(table, "a.jpg")));
        assertEquals("\u2013", gpsColumn.getCellData(fileNamed(table, "b.jpg")));
    }

    @Test
    void filterShowsOnlyMatchingFiles(FxRobot robot) throws Exception {
        loadThreeImages(robot);

        robot.clickOn("#filter-WITHOUT_COORDINATES");
        assertEquals(2, robot.lookup("#mediaTable").queryTableView().getItems().size());

        robot.clickOn("#filter-ALL");
        assertEquals(3, robot.lookup("#mediaTable").queryTableView().getItems().size());
    }
```

Dazu kommt die private Hilfsmethode `fileNamed(TableView<MediaFile> table, String name)`. Sie liefert die Zeile, deren `path().getFileName()` gleich `name` ist.

- [ ] **Step 2: UI-Tests laufen lassen**

Während des Laufs Maus und Tastatur nicht benutzen.

Run: `.\gradlew.bat uiTest`
Expected: BUILD SUCCESSFUL, 3 Tests grün. Kurz erscheinen Fenster.

Run: `.\gradlew.bat test --tests tf.geotagger.main.GeoTaggerUiTest`
Expected: dieselben 3 Tests grün, so wie ein Start der Klasse aus NetBeans (Review Focus 4).

Run: `.\gradlew.bat test --tests tf.geotagger.testsuites.UITests`
Expected: 3 Tests grün, unter „UITests > …“.

Run: `.\gradlew.bat test`
Expected: weiterhin 102 Tests, keine Fenster.

- [ ] **Step 3: Echte Preferences prüfen** (Review Focus 1)

Run: `Get-ItemProperty 'HKCU:\Software\JavaSoft\Prefs\tf\geotagger\main'`
Expected: `exif/Tool/Path` und die Fenstermaße wie vor dem Testlauf. Unter `HKCU:\Software\JavaSoft\Prefs\tf\geotagger\test` liegen die Testwerte.

- [ ] **Step 4: Commit**

```powershell
& $git -C $app add src
& $git -C $app commit -m "UI tests: start, load folder, filters"
```

---

### Task 5: Spec und Plan 3b nachziehen

**Files:**
- Modify:
  - `docs\superpowers\specs\2026-11-03-geotagger-design.md` (Abschnitt „Tests“)
  - `docs\superpowers\plans\2026-11-04-plan-3b-bearbeiten-speichern.md`

- [ ] **Step 1: Spec**

Im Abschnitt „Tests“ wird der Punkt „UI: manueller Test des Workflows mit einer echten Sammlung“ ersetzt durch:
- **UI-Tests mit TestFX**, `@Tag("UI")`, über `gradlew uiTest` oder die Suite `UITests`. Abgedeckt sind die Abläufe ohne native Dialoge.
- **Manuell bleiben:** native Datei- und Ordnerdialoge, das Aussehen der Vorschau und später die Karte (WebView).
- **Testsammlungen:** `gradlew test` bzw. `NonUITests` ohne UI.

- [ ] **Step 2: Plan 3b um UI-Tests ergänzen**

In Plan 3b:
- **Task 5:** `DetailsPane` setzt für jedes Textfeld die ID `"details-" + field.name()`, z. B. `#details-CITY`.
- **Neuer Task 7 „UI-Tests für Bearbeiten und Speichern“** mit `GeoTaggerEditUiTest` (`@Tag("UI")`, Aufbau wie `GeoTaggerUiTest`). Er nutzt `UiTestSupport` und die Testhilfe aus dem Gerüst-Plan und enthält diese Tests:
  - `editingCityMarksTheFileAsChanged`: Datei wählen, `#details-CITY` mit „Bozen“ und Enter, dann zeigt die Statusspalte „Loc.“ `●` und `#saveButton` hat den Text „Save (1)“.
  - `invalidLatitudeIsRejected`: `91` eingeben und Enter. Das Feld trägt den Stil mit `red`, die Datei ist nicht geändert.
  - `undoAndRedo`: City setzen, `#undoButton` klicken, dann ist der alte Wert wieder da und die Datei unverändert. `#redoButton` setzt den neuen Wert erneut.
  - `multiSelectionChangesAllFiles`: per `robot.interact(() -> table.getSelectionModel().selectAll())` alles auswählen, City setzen, dann haben alle Dateien den Wert und der Button zeigt „Save (n)“.
  - `saveWritesTheFile`: City setzen, `#saveButton` klicken, `waitUntilIdle` abwarten. Danach liefert `ExifTool.readJson` für die Datei `XMP-photoshop:City = Bozen`, `*_original` existiert, und die Statusspalte zeigt wieder `✓`.
- **Manuelle Checkliste in Task 6:** Was diese Tests abdecken, wird auf „durch `GeoTaggerEditUiTest` abgedeckt“ verkürzt. Manuell bleiben Layout, Image info, „von außen geändert“ sowie die Rückfragen beim Ordnerwechsel und beim Schließen. Die Rückfrage-Dialoge sind native-nah (`Alert`), ihr Inhalt hängt an `confirmUnsavedChanges`.
- **Testzahl:** Am Ende von Plan 3b stehen dann 115 Tests ohne UI (102 + 13) und 8 UI-Tests.

- [ ] **Step 3: Commit**

```powershell
& $git -C $app add docs
& $git -C $app commit -m "Spec and plan 3b: automated UI tests"
```

---

## Abschluss

- `gradlew test`: 102 Tests, keine Fenster.
- `gradlew uiTest`: 3 UI-Tests grün.
- Die echten Preferences sind unverändert.
- Danach Plan 3b umsetzen, mit seinen UI-Tests aus Task 5 dieses Plans.

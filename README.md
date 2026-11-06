# GeoTagger
JavaFX application to geotag photos and videos

And why would anyone need a new geotagger?

My working horse for this job, GeoSetter 4.0.49, isn't maintained anymore. GeoTagger rebuilds the parts of it I still need, in Java / JavaFX:

* sync photos and videos from smartphone and DSLR with GPX tracks (incl. camera clock offset and time zone)
* set positions manually on a map
* add place (country, region, city, sublocation), altitude and time zone

All metadata is read and written with [ExifTool](https://exiftool.org) - one long running process via [ExifToolHelper](https://github.com/ThomasDaheim/ExifToolHelper).

## Status

Work in progress. Available so far:

* open a folder (optionally with subfolders), recently used folders - the list fills while the metadata is read
* file list with filters (without coordinates / place / altitude, changed) and the values as columns - coordinates like GeoSetter (`N46°40'48.05"; E11°5'28.61"`), changed values in orange, columns can be hidden
* preview with navigation and "100 %" mode - RAW files and videos show their embedded preview
* edit dialog (Ctrl+E, double click, "Edit..."): coordinates, altitude, place fields and time zone - also for several files at once, one undo step per dialog
  * coordinates as decimal degrees (`46.5`, `46,5`) or degrees with hemisphere (`48° 08′ 14″ N`, `11° 34′ 32″ O`)
* undo / redo (Ctrl+Z / Ctrl+Y), save (Ctrl+S), open folder (Ctrl+O)
* map and image info as tabs - image info shows all tags of the selected file as ExifTool reports them
* map (Leaflet via [GeoHelper](https://github.com/ThomasDaheim/GeoHelper)) with clustered markers - saved positions blue, changed ones orange, selected files red
  * set positions by dragging files from the list onto the map, by "Set position of n files here" (right click) or by dragging a selected marker
  * clicking a marker selects its file, Ctrl + drag selects all files in the rectangle (with Shift: added to the selection)
* track list: GPX files of the folder, check to load and show them on the map, "+ GPX" for files from elsewhere
  * colours from the GPX file (gpx_style or Garmin DisplayColor), each segment as its own line
  * the points of the selected tracks with time, coordinates, distance and elevation - selecting one centres the map
* GPX reading and track sync logic (the sync dialog follows)

Planned next: geo services (reverse geocoding, altitude, time zone) and the track sync dialog.

## Notes

* Note on saving: changes are collected and written only on "Save". ExifTool keeps the original as `*_original`; the saved file gets the file timestamps of the original. Files changed outside GeoTagger since loading are skipped and keep their changes.
* Note on formats: JPEG, TIFF, PNG, HEIC, RAW (CR2, CR3, NEF, ARW, DNG, ...) and videos (MOV, MP4, M4V) are written directly. For AVI and MTS an XMP sidecar `<name>.xmp` is written instead.
* Note on ExifTool: GeoTagger looks for ExifTool on the PATH at the first start; otherwise select the executable under "Preferences...".
* Note on building: GeoTagger needs [JavaHelper](https://github.com/ThomasDaheim/JavaHelper), [ExifToolHelper](https://github.com/ThomasDaheim/ExifToolHelper) and [GeoHelper](https://github.com/ThomasDaheim/GeoHelper) in the local maven repo - run "gradlew build" in those projects first. Requires JDK 25; Gradle comes via the wrapper.
* Note on running: "gradlew build" (or "gradlew installDist") creates start scripts - start `build\install\GeoTagger\bin\GeoTagger.bat`, about 3 s faster than "gradlew run". Both put JavaFX on the module path (see build.gradle).
* Note on tests:
  * "gradlew test" runs all tests without UI. Tests that need ExifTool look for it in the environment variable EXIFTOOL_PATH and then on the PATH - without it they are skipped.
  * "gradlew uiTest" runs the UI tests (TestFX). They open windows and move the mouse - keep your hands off mouse and keyboard meanwhile.
  * Suites for the IDE: tf.geotagger.testsuites.NonUITests and UITests.
  * Tests use their own preferences node, the real settings stay untouched.
  * Real camera files for the round trip test go into src/test/resources/samples - they are git-ignored.
* Note on design: specification and implementation plans are in docs/superpowers.

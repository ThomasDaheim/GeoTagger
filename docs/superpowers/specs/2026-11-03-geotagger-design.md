# GeoTagger – Design

Stand: 2026-11-03 · Status: Entwurf, abgestimmt im Brainstorming

## Ziel

GeoTagger ersetzt die Teile von GeoSetter 4.0.49, die im eigenen Foto-Workflow gebraucht werden. GeoSetter wird nicht mehr weiterentwickelt. GeoTagger ist eine eigenständige Java/JavaFX-Anwendung für Windows.

Referenz-Screenshots des Originals:

- [GeoSetter Hauptfenster](../../reference/geosetter-hauptfenster.webp)
- [GeoSetter Sync-Dialog](../../reference/geosetter-sync-dialog.png)

## Workflow, den die App abbildet

Eine Sammlung ist z. B. ein Urlaub, mit 500 bis 3.000 Dateien aus mehreren Quellen:

1. **Laden**
   - Smartphone-Fotos und -Videos haben schon Koordinaten.
   - DSLR-Fotos haben keine Koordinaten.
   - Dazu kommen GPX-Tracks.
2. **Track-Sync:** Dateien ohne Koordinaten bekommen sie über die Aufnahmezeit aus den Tracks.
3. **Karte:** Was danach noch keine Koordinaten hat, wird manuell auf der Karte gesetzt.
4. **Anreichern:** Sobald alle Dateien Koordinaten haben, werden Ortsinformationen, Höhe und Zeitzone ergänzt.
5. **Speichern:** Alle gesammelten Änderungen werden in die Dateien geschrieben.

Videos durchlaufen denselben Ablauf.

## Umfang

### In Version 1

- Ordner öffnen, optional mit Unterordnern. Die Formate sind JPEG, RAW, HEIC, TIFF, PNG und Videos (MOV, MP4).
- Dateiliste ohne Thumbnails mit den Spalten Name, Aufnahmezeit, Ort und Status (Koordinaten / Ort / Höhe / geändert).
- Große Vorschau des ausgewählten Bildes mit Blättern, Einpassen und 100 %.
- Statusfilter: Alle, Ohne Koordinaten, Ohne Ort, Ohne Höhe, Geändert.
- Track-Sync über einen Dialog, Details siehe unten.
- Leaflet-Karte:
  - Marker aller Dateien mit Koordinaten, gebündelt (Clustering).
  - Geladene Tracks werden angezeigt.
  - Koordinaten setzen per Drag & Drop aus der Liste, durch Verschieben eines Markers oder per Rechtsklick für die Auswahl.
- Ortsinformationen per Reverse-Geocoding. Die Dienste sind dieselben wie in GPXEditor (Mapbox) plus GeoNames (`api.geonames.org`, mit Benutzername).
- Höhe per openrouteservice oder lokalen SRTM-Daten, wahlweise aus dem Track.
- Zeitzone offline aus den Koordinaten.
- Reiter „Details“: editierbare Felder. Bei Mehrfachauswahl wird „(unterschiedlich)“ angezeigt, und eine Eingabe gilt für alle ausgewählten Dateien.
- Reiter „Bild-Info (ExifTool)“: alle rohen Tags der ausgewählten Datei, nur lesbar.
- Track-Liste:
  - GPX-Dateien im Ordner (und in Unterordnern) werden automatisch aufgelistet.
  - Jeder Track lässt sich einzeln laden und auf der Karte ein- und ausblenden.
  - Weitere GPX-Dateien lassen sich hinzufügen.
- Änderungen werden gesammelt und erst beim Speichern geschrieben. Undo und Redo kommen aus JavaHelper.
- Einstellungen: ExifTool-Pfad, API-Schlüssel und Benutzernamen, Dienstauswahl, zuletzt verwendete Sync-Parameter.

### Bewusst nicht in Version 1

- Thumbnail-Raster. Die Liste und die große Vorschau reichen, so wie in GeoSetter.
- Video-Vorschaubilder. Videos zeigen einen Platzhalter.
- Aufnahmerichtung bzw. Kompassdaten, Favoriten, Ortssuche, direkte Koordinateneingabe.
- Titel, Beschreibung, Stichwörter.
- Korrektur der Aufnahmezeit in der Datei (Offset schreiben).
- Offset aus einem Referenzbild ableiten.
- Mehrere unabhängige Ordner in einer Sammlung. Das Modell lässt das ohne Umbau zu, siehe [Modell](#modell).

## Metadaten

### Felder

| Feld | Quelle | Ziel-Tags |
|---|---|---|
| Breite, Länge | Track-Sync, Karte, manuell | EXIF GPS bzw. QuickTime GPS, XMP `exif:GPS*` |
| Höhe | Track, openrouteservice, SRTM, manuell | EXIF `GPSAltitude` und `GPSAltitudeRef`, XMP |
| Ländercode, Land, Region, Ort, Sublocation | Reverse-Geocoding, manuell | IPTC (`Country-PrimaryLocationCode`, `Country-PrimaryLocationName`, `Province-State`, `City`, `Sub-location`) und XMP (`photoshop:*`, `Iptc4xmpCore:*`), jeweils konsistent |
| Zeitzone der Aufnahme | Offline-Zeitzonenabfrage aus den Koordinaten, oder Kamera-Zeitzone aus dem Sync-Dialog | EXIF `OffsetTimeOriginal` (bei Videos: XMP) |

Die konkrete Tag-Zuordnung je Format liegt gekapselt im Paket `metadata` und wird über Integrationstests abgesichert.

### Schreibregeln

- Metadaten werden **direkt in die Datei** geschrieben, wenn ExifTool das Format schreiben kann. Das gilt auch für RAW, HEIC, MOV und MP4.
- Nur für Formate, die ExifTool nicht schreiben kann, entsteht eine **XMP-Sidecar-Datei** (`<name>.xmp`).
- ExifTool behält die Originaldatei als `*_original` (kein `-overwrite_original`).
- Die neue Datei behält die Datei-Zeitstempel des Originals (`-P`). Ob das unter Windows auch für das Erstellungsdatum gilt, klärt ein Integrationstest. Falls nicht, setzt die App das Erstellungsdatum nach dem Schreiben selbst zurück.
- Die Aufnahmezeit (`DateTimeOriginal` usw.) wird nie verändert.

## Architektur

### Repositories

- **GeoTagger** (dieses Repo): die Anwendung. Sie hängt von GeoHelper und JavaHelper ab, nicht von GPXEditor.
- **GeoHelper** (neues Repo, Paketpräfix `tf.geo`): wiederverwendbare Geo-Bausteine. Das Repo wird über `publishToMavenLocal` bereitgestellt, so wie JavaHelper.
- **JavaHelper** (vorhanden, `tf.JavaHelper:JavaHelper:1.17`): Undo/Redo (`tf.helper.doundo`), Preferences, Tabellen-Einstellungen, Stage- und Alert-Helfer.

GeoHelper entsteht **zusammen mit GeoTagger**. Code aus GPXEditor wird dafür kopiert und entkoppelt. GPXEditor wird erst **später** auf GeoHelper umgestellt, in einem eigenen Projekt. Bis dahin gibt es den Code bewusst zweimal.

Build: Gradle, Java 25 und JavaFX 25.0.1, so wie in GPXEditor und JavaHelper.

### GeoHelper

| Paket | Inhalt | Herkunft |
|---|---|---|
| `tf.geo.map` | Leaflet-Karte in einer WebView: Layer, Marker, Cluster, Polylines für Tracks, Callbacks von JS nach Java (Klick, Drag, Rechtsklick) | `tf.gpx.edit.leafletmap` und `resources/leaflet/` aus GPXEditor, von `TrackMap` und den GPXEditor-Preferences gelöst |
| `tf.geo.service` | Schnittstellen `ReverseGeocoder`, `ElevationProvider`, `TimeZoneLookup`. Implementierungen: Mapbox, GeoNames (neu), openrouteservice, SRTM, offline-timezone | `tf.gpx.edit.mapbox`, `tf.gpx.edit.elevation`, `TimeZoneProvider` aus GPXEditor |
| `tf.geo.exiftool` | ExifTool-Wrapper über `-stay_open True -@ -`: Befehle per stdin, Antwortende über `{ready}`. Lesen als JSON (`-json -n -G1`), Binärausgabe (`-b -PreviewImage` / `-JpgFromRaw`), Schreiben. Thread-sicher durch eine Befehlswarteschlange. | neu; ersetzt die Nutzung von im4java |

GeoHelper kennt GeoTagger nicht. Nur was die App braucht, wandert in die Library.

### GeoTagger-Pakete

| Paket | Verantwortung | Abhängigkeiten |
|---|---|---|
| `model` | `MediaFile`: Pfad, Typ, Originalwerte, ausstehende Änderungen, abgeleiteter Status. `MediaCollection`: beobachtbare Liste. `GeoTrack`: Trackpunkte mit Zeit, Koordinate und Höhe. | – |
| `metadata` | Übersetzung zwischen `MediaFile` und ExifTool-Tags je Format, Lesen in Blöcken, Speichern, Sidecar-Rückfall | `model`, `tf.geo.exiftool` |
| `sync` | Track-Sync als reine Logik: Eingabe sind Tracks, Dateien und Parameter, Ausgabe ein Ergebnis je Datei | `model` |
| `enrich` | Orts-, Höhen- und Zeitzonenabfrage für eine Dateimenge: Zusammenfassen, Drosselung, Cache, Fortschritt | `model`, `tf.geo.service` |
| `preview` | Vorschau des ausgewählten Bildes. Das vorherige und das nächste werden vorab geladen, gehalten wird nur ein kleiner Speicher-Cache. JavaFX-Formate werden direkt gelesen, RAW und HEIC über das eingebettete JPEG aus ExifTool. | `model`, `tf.geo.exiftool` |
| `ui` | Hauptfenster, Sync-Dialog, Einstellungen, Undo-Aktionen | alle obigen, `tf.geo.map`, JavaHelper |

Die Abhängigkeiten zeigen nur in eine Richtung: `ui` → `enrich` / `sync` / `metadata` / `preview` → `model`.

### Modell

- `MediaFile` hält zwei Wertsätze: `original` (zuletzt gelesen) und `pending` (geändert, noch nicht gespeichert). Angezeigt wird `pending`, wo vorhanden, sonst `original`.
- Der Status (hat Koordinaten / Ort / Höhe / ist geändert) wird aus den angezeigten Werten berechnet und speist die Filter.
- Eine Sammlung ist eine Liste von Dateien. Der Ordner bestimmt nur, wie sie befüllt wird. Mehrere Ordner pro Sammlung bräuchten später nur einen anderen Lade-Dialog und eine Ordner-Spalte.

## Hauptfenster

Das Layout ist an GeoSetter angelehnt:

- **Werkzeugleiste:** Ordnerpfad, Häkchen „Unterordner“, Track-Sync, Ort ermitteln, Höhe ermitteln, Undo, Redo und „Speichern (n)“ mit der Anzahl ungespeicherter Dateien.
- **Links oben:**
  - Statusfilter als Chips mit Anzahl.
  - Darunter die Dateiliste. Spalten: Name, Aufnahmezeit, Ort, Statussymbole.
  - Die Auswahl kann mehrere Dateien umfassen.
- **Links unten:** große Vorschau mit den Knöpfen Erstes, Zurück, Weiter, Letztes, Einpassen und 100 %.
- **Rechts oben:** die Karte.
- **Rechts Mitte:** die Reiter „Details“ und „Bild-Info (ExifTool)“.
- **Rechts unten:** Track-Liste mit Häkchen zum Laden und Einblenden sowie „+ GPX“.
- **Statusleiste:** Anzahl Dateien, ausgewählt, ungespeichert.

Aktionen der Werkzeugleiste wirken auf die Auswahl. Ist nichts ausgewählt, wirken sie auf alle gefilterten Dateien, für die die Aktion sinnvoll ist. „Ort ermitteln“ läuft zum Beispiel über alle Dateien mit Koordinaten, aber ohne Ort.

## Track-Sync-Dialog

**Eingaben**

- **Track-Quelle**, eine von:
  - alle geladenen Tracks
  - eine GPX-Datei
  - alle GPX-Dateien in einem Ordner (zuletzt gewählter Ordner wird gespeichert, z. B. ein zentrales GPX-Archiv)
- **Dateien:** „Ohne Koordinaten“ (Standard) oder „Auswahl“. Dazu das Häkchen „Vorhandene überschreiben“, standardmäßig aus.
- **Zeitzone der Kamera:** vorbelegt aus `OffsetTimeOriginal` der Datei, falls vorhanden, sonst aus der Systemzeitzone.
- **Zusätzlicher Offset:** Vorzeichen, Tage, Stunden, Minuten, Sekunden.
- **Kamera-Uhrzeit:** Diese Anzeige zeigt live die jetzige Systemzeit mit Zeitzone und Offset. Der Nutzer stellt Zeitzone und Offset so ein, dass sie mit dem Kameradisplay übereinstimmt.
- **Maximale Zeitdifferenz** zum nächsten Trackpunkt, standardmäßig 30 min.
- **Zuweisung:** zwischen den Nachbarpunkten interpolieren (Standard) oder exakt den nächsten Punkt nehmen.
- **Häkchen:**
  - „Keine Trackpunkte in der Zukunft berücksichtigen“
  - „Höhe aus Track übernehmen“ (Standard an). Es betrifft nur Dateien, die in diesem Lauf Koordinaten bekommen.
  - „Zeitzone in Datei schreiben“ (Standard aus). Es setzt die gewählte Kamera-Zeitzone als `OffsetTimeOriginal` für die zugeordneten Dateien.

**Vorschau**

- Eine Tabelle mit den Spalten Datei, Kamerazeit, korrigierte Zeit (UTC), Ergebnis-Koordinate und zeitlicher Abstand zum nächsten echten Trackpunkt.
- Die Tabelle wird bei jeder Parameteränderung neu berechnet.
- Markierungen: zugeordnet, großer Abstand (über der halben maximalen Differenz) und kein Treffer. Darunter steht eine Zusammenfassung mit den Anzahlen.

**Übernehmen**

- Die Ergebnisse werden als **ein** Undo-Schritt in die ausstehenden Änderungen übernommen.
- Dabei wird nichts in die Dateien geschrieben.

**Algorithmus**

1. Die Kamerazeit wird mit Zeitzone und Offset in UTC umgerechnet.
2. In der nach Zeit sortierten Punktliste aller Track-Quellen wird der Vorgänger und der Nachfolger gesucht.
3. Ist der kleinere Abstand größer als das Maximum, gibt es keinen Treffer.
4. Bei Interpolation wird linear zwischen Vorgänger und Nachfolger interpoliert, die Höhe ebenso. Liegt nur einer der beiden innerhalb des Maximums, wird dieser Punkt genommen.
5. Mit „keine Zukunft“ zählt nur der Vorgänger.

## Datenfluss

1. **Ordner öffnen**
   - Die App sucht Dateien mit bekannten Endungen, auf Wunsch auch in Unterordnern.
   - ExifTool liest die benötigten Tags in Blöcken von etwa 100 Dateien.
   - Die Liste füllt sich schrittweise, ein Fortschritt wird angezeigt.
   - GPX-Dateien landen in der Track-Liste.
2. **Bearbeiten:** Jede Aktion wird als Undo-Aktion ausgeführt und ändert nur `pending`.
3. **Anreichern**
   - Koordinaten werden auf ein Raster von etwa 100 m gerundet und zusammengefasst.
   - Pro Raster gibt es eine Anfrage, deren Ergebnis im Speicher zwischengespeichert wird.
   - Die Anfragen werden je Dienst gedrosselt.
   - Die Ergebnisse landen in `pending`, als ein Undo-Schritt pro Lauf.
   - Die Zeitzone wird nur ergänzt, wo noch keine vorhanden ist. Ein vorhandenes `OffsetTimeOriginal` oder eine Zeitzone aus dem Sync-Dialog hat Vorrang.
4. **Speichern**
   - Je geänderter Datei werden die Ziel-Tags für ihr Format gebildet und über ExifTool geschrieben.
   - Danach liest die App die Datei neu ein, setzt `original` neu und leert `pending`.
   - Der Undo-Verlauf wird nach erfolgreichem Speichern geleert.

## Fehlerbehandlung

| Situation | Verhalten |
|---|---|
| ExifTool nicht gefunden oder falscher Pfad | Beim Start öffnet sich der Einstellungsdialog. Ohne ExifTool ist kein Ordner ladbar. |
| ExifTool-Prozess stürzt ab oder hängt (Timeout) | Prozess neu starten, Block einmal wiederholen, danach Fehler melden |
| Schreibfehler bei einer Datei | Der Speicherlauf läuft weiter. Die Datei behält `pending` und wird markiert. Am Ende gibt es eine Zusammenfassung. |
| Datei wurde seit dem Laden von außen geändert (Änderungszeit) | Es erscheint eine Warnung, die Datei wird übersprungen und behält `pending`. |
| Dienstfehler oder Ratenlimit | Erneuter Versuch mit wachsender Wartezeit. Danach bleibt die Datei „ohne Ort“ bzw. „ohne Höhe“, und am Ende gibt es eine Zusammenfassung. |
| Ungespeicherte Änderungen beim Schließen oder Ordnerwechsel | Rückfrage: Speichern, Verwerfen oder Abbrechen |
| GPX-Datei unlesbar | Der Track wird in der Liste als fehlerhaft markiert, die übrigen Tracks bleiben nutzbar. |

## Tests

- **Unit-Tests** ohne externe Abhängigkeiten:
  - `sync`: Zeitzone, Offset, Interpolation, maximale Differenz, keine Zukunft, mehrere Tracks
  - Tag-Zuordnung je Format
  - Raster-Cache in `enrich`
  - Statusberechnung in `model`
- **Integrationstests mit echtem ExifTool**, übersprungen, wenn kein ExifTool konfiguriert ist:
  - Schreiben und wieder Lesen je Format
  - `*_original` entsteht
  - Datei-Zeitstempel bleiben erhalten (Änderungs- und Erstellungsdatum)
  - Sidecar-Rückfall
  - Aufnahmezeit bleibt unverändert
- **Musterdateien:** je eine kleine Datei der echten Kameras (DSLR-RAW, Smartphone-JPEG/HEIC, Videos). Der Nutzer stellt sie bereit, sie liegen unter `src/test/resources`.
- **Online-Dienste:** in Tests über Mocks der Schnittstellen, dazu ein manueller Smoke-Test.
- **UI:** manueller Test des Workflows mit einer echten Sammlung.

## Offene Punkte für die Planung

- Mit welchem Schritt GeoHelper beginnt. Naheliegend ist zuerst `tf.geo.exiftool`, weil Lesen und Schreiben alles andere tragen.
- Ob `-P` unter Windows auch das Erstellungsdatum erhält, klärt der erste Integrationstest.

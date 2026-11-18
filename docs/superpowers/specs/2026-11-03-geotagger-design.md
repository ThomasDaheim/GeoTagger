# GeoTagger – Design

Stand: 2026-11-03 · Status: Entwurf, abgestimmt im Brainstorming

## Ziel

GeoTagger ersetzt die Teile von GeoSetter 4.0.49, die im eigenen Foto-Workflow gebraucht werden. GeoSetter wird nicht mehr weiterentwickelt. GeoTagger ist eine eigenständige Java/JavaFX-Anwendung für Windows. Die Oberfläche ist englisch wie bei GPXEditor, die deutschen Beschriftungen in dieser Spec und in den Mockups sind nur Illustration.

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
- Dateiliste ohne Thumbnails mit den Werten als Spalten (siehe „Hauptfenster“); geänderte Werte sind markiert.
- Große Vorschau des ausgewählten Bildes mit Blättern, Einpassen und 100 %.
- Statusfilter: Alle, Ohne Koordinaten, Ohne Ort, Ohne Höhe, Geändert.
- Track-Sync über einen Dialog, Details siehe unten.
- Leaflet-Karte:
  - Marker aller Dateien mit Koordinaten, gebündelt (Clustering).
  - Geladene Tracks werden angezeigt.
  - Koordinaten setzen per Drag & Drop aus der Liste, durch Verschieben eines Markers oder per Rechtsklick für die Auswahl.
- Ortsinformationen per Reverse-Geocoding: GeoNames (`findNearbyPlaceName` über `secure.geonames.org`, mit Benutzername) oder Mapbox (Geocoding v6, mit Access-Token), wählbar in den Einstellungen. „Find place“ ist gesperrt, wenn es nichts abzufragen gibt.
- Höhe per „Find altitude“ aus lokalen SRTM-Daten und/oder openrouteservice (Plan 5b), wählbar in den Einstellungen; fehlende SRTM-Kacheln werden nach Rückfrage geladen. „Find altitude“ ist gesperrt, wenn es nichts abzufragen gibt. Die Höhe aus dem Track übernimmt der Sync-Dialog (Plan 6).
- Zeitzone: aus der Datei, dem Sync-Dialog oder dem Bearbeiten-Dialog, **nicht** aus den Koordinaten. Entscheidung vom 15.11.2026: Bei DSLR-Fotos stimmt die Zeitzone des Ortes nur, wenn die Kamerauhr auf Ortszeit stand.
- Dialog „Bearbeiten“: editierbare Felder für die Auswahl. Bei Mehrfachauswahl wird „(different)“ angezeigt, und eine Eingabe gilt für alle ausgewählten Dateien. OK ist ein Undo-Schritt.
- Reiter „Bild-Info (ExifTool)“: alle rohen Tags der ausgewählten Datei, nur lesbar.
- Track-Liste:
  - GPX-Dateien im Ordner (und in Unterordnern) werden automatisch aufgelistet.
  - Jeder Track lässt sich einzeln laden und auf der Karte ein- und ausblenden.
  - Weitere GPX-Dateien lassen sich hinzufügen. Sie bleiben in der Liste, wenn ein anderer Ordner geöffnet wird; nur die GPX-Dateien des alten Ordners werden ersetzt.
- Das Häkchen „Unterordner“ lädt den geöffneten Ordner sofort neu (nach Rückfrage bei ungespeicherten Änderungen).
- Änderungen werden gesammelt und erst beim Speichern geschrieben. Undo und Redo kommen aus JavaHelper.
- Einstellungen: ExifTool-Pfad; für „Ort ermitteln“ Dienst (GeoNames oder Mapbox), GeoNames-Benutzer, Mapbox-Token und Sprache der Ortsnamen (z. B. `de`, leer = Vorgabe des Dienstes), Umkreis für die Ortsauswahl und ein Knopf „Check“, der den gewählten Dienst mit den eingetragenen Werten abfragt; für „Höhe ermitteln“ die Quelle (SRTM, Lücken per openrouteservice – Standard / nur SRTM / nur openrouteservice), der SRTM-Ordner, das Download-Format (SRTM3 – Standard / SRTM1), die Interpolation (bilinear – Standard / nächster Gitterpunkt) und der openrouteservice-Schlüssel mit „Check“ (Höhe von München); später die zuletzt verwendeten Sync-Parameter.

### Bewusst nicht in Version 1

- Thumbnail-Raster. Die Liste und die große Vorschau reichen, so wie in GeoSetter.
- Eigene Vorschaubilder für Videos, etwa per ffmpeg. Hat ein Video ein eingebettetes Vorschaubild (z. B. Canon `ThumbnailImage`), wird dieses gezeigt, sonst ein Platzhalter.
- Aufnahmerichtung bzw. Kompassdaten. Vorhandene Werte bleiben aber erhalten, siehe [Schreibregeln](#schreibregeln) und [Mögliche nächste Schritte](#mögliche-nächste-schritte).
- Favoriten, Ortssuche, direkte Koordinateneingabe.
- Titel, Beschreibung, Stichwörter.
- Korrektur der Aufnahmezeit in der Datei (Offset schreiben).
- Offset aus einem Referenzbild ableiten.
- Mehrere unabhängige Ordner in einer Sammlung. Das Modell lässt das ohne Umbau zu, siehe [Modell](#modell).

## Metadaten

### Felder

| Feld | Quelle | Ziel-Tags |
|---|---|---|
| Breite, Länge | Track-Sync, Karte, manuell | EXIF GPS bzw. bei Videos QuickTime `Keys:GPSCoordinates` (Apple) und `UserData:GPSCoordinates` (`©xyz`, Android / Google Photos) – immer beide, sonst behielte `©xyz` die alte Position der Kamera (Entscheidung 18.11.2026); XMP `exif:GPS*` |
| Höhe | Track, openrouteservice, SRTM, manuell | EXIF `GPSAltitude` und `GPSAltitudeRef`, bei Videos zusätzlich in beiden QuickTime-`GPSCoordinates`; XMP |
| Ländercode, Land, Region, Ort, Sublocation | Reverse-Geocoding, manuell | IPTC (`Country-PrimaryLocationCode`, `Country-PrimaryLocationName`, `Province-State`, `City`, `Sub-location`) und XMP (`photoshop:*`, `Iptc4xmpCore:*`), jeweils konsistent |
| Zeitzone der Aufnahme | Kamera-Zeitzone aus dem Sync-Dialog, manuell | EXIF `OffsetTimeOriginal`. Bei Videos und Sidecar-Dateien wird sie in Version 1 nicht geschrieben, weil es dafür kein Standard-Tag gibt, das die Aufnahmezeit unberührt lässt. |

Die konkrete Tag-Zuordnung je Format liegt gekapselt im Paket `metadata` und wird über Integrationstests abgesichert.

### Schreibregeln

- Metadaten werden **direkt in die Datei** geschrieben, wenn ExifTool das Format schreiben kann. Das gilt auch für RAW, HEIC, MOV und MP4.
- Nur für Formate, die ExifTool nicht schreiben kann, entsteht eine **XMP-Sidecar-Datei**. Ihr Name ist der Dateiname ohne Endung plus `.xmp` (aus `clip.avi` wird `clip.xmp`), wie bei Lightroom.
- PNG und HEIC bekommen kein IPTC. PNG kennt IPTC nicht als Standard, dort genügt XMP.
- ExifTool behält die Originaldatei als `*_original` (kein `-overwrite_original`).
- Die neue Datei behält die Datei-Zeitstempel des Originals (`-P`). Ob das unter Windows auch für das Erstellungsdatum gilt, klärt ein Integrationstest. Falls nicht, setzt die App das Erstellungsdatum nach dem Schreiben selbst zurück.
- Die Aufnahmezeit (`DateTimeOriginal` usw.) wird nie verändert.
- Geschrieben werden nur die Tags aus der Feldtabelle. Alle anderen Tags bleiben unverändert erhalten. Das gilt ausdrücklich für die Aufnahmerichtung (`GPSImgDirection`, `GPSImgDirectionRef`, `GPSDestBearing`), die Smartphones automatisch schreiben, auch wenn Koordinaten, Ort oder Höhe derselben Datei geändert werden.

## Architektur

### Repositories

- **GeoTagger** (dieses Repo): die Anwendung. Sie hängt von ExifToolHelper, GeoHelper und JavaHelper ab, nicht von GPXEditor.
- **ExifToolHelper** (neues Repo, Paket `tf.exiftool`, `tf.ExifToolHelper:ExifToolHelper`): der ExifTool-Wrapper.
  - Er läuft als eigene Library ohne JavaFX und ohne Geo-Abhängigkeiten, damit auch PicExporter und `MakeImageJSON` in GPXEditor ihn später nutzen können.
  - Bereitgestellt wird er über `publishToMavenLocal`.
- **GeoHelper** (neues Repo, Paketpräfix `tf.geo`): wiederverwendbare Geo-Bausteine. Das Repo wird über `publishToMavenLocal` bereitgestellt, so wie JavaHelper.
- **JavaHelper** (vorhanden, `tf.JavaHelper:JavaHelper:1.17`): Undo/Redo (`tf.helper.doundo`), Preferences, Tabellen-Einstellungen, Stage- und Alert-Helfer.

GeoHelper entsteht **zusammen mit GeoTagger**. Code aus GPXEditor wird dafür kopiert und entkoppelt. GPXEditor wird erst **später** auf GeoHelper umgestellt, in einem eigenen Projekt. Bis dahin gibt es den Code bewusst zweimal.

Build: Gradle, Java 25 und JavaFX 25.0.1, so wie in GPXEditor und JavaHelper.

### GeoHelper

| Paket | Inhalt | Herkunft |
|---|---|---|
| `tf.geo.map` | Leaflet-Karte in einer WebView: Layer, Marker, Cluster, Polylines für Tracks, Callbacks von JS nach Java (Klick, Drag, Rechtsklick) | `tf.gpx.edit.leafletmap` und `resources/leaflet/` aus GPXEditor, von `TrackMap` und den GPXEditor-Preferences gelöst |
| `tf.geo.service` | Schnittstellen `ReverseGeocoder` (GeoNames neu, Mapbox Geocoding v6) und `ElevationProvider` (openrouteservice, SRTM; Plan 5b). Ein Aufruf ist eine Anfrage; Cache, Drosselung und Wiederholung macht der Aufrufer. HTTP über `HttpTransport`, damit Tests ohne Netz laufen. | `tf.gpx.edit.mapbox`, `tf.gpx.edit.elevation` aus GPXEditor |
GeoHelper kennt GeoTagger nicht. Nur was die App braucht, wandert in die Library.

### ExifToolHelper

`tf.exiftool.ExifTool` ist ein Wrapper über `-stay_open True -@ -`:
- Befehle gehen per stdin hinein, das Antwortende erkennt er am Marker `{ready}`.
- Er liest als JSON (`-json -n -G1`), gibt Binärdaten aus (`-b -PreviewImage` / `-JpgFromRaw`) und schreibt.
- Thread-sicher ist er durch eine Befehlswarteschlange.
- Er ersetzt die bisherige Nutzung von im4java.

Was GeoTagger liest und schreibt (Felder, Formatprofile, Schreibregeln), bleibt bewusst in GeoTagger. Die anderen Projekte brauchen andere Felder und haben eigene Modelle.

### GeoTagger-Pakete

| Paket | Verantwortung | Abhängigkeiten |
|---|---|---|
| `model` | `MediaFile`: Pfad, Typ, Originalwerte, ausstehende Änderungen, abgeleiteter Status. `MediaCollection`: beobachtbare Liste. | – |
| `track` | `TrackPoint` (Zeit, Koordinate, Höhe), `GpxReader` (GPX 1.0/1.1 per Jackson XML wie in den anderen Anwendungen, ohne gpx-parser), `TrackTimeline` (zeitlich sortierte Punkte aller Tracks, Nachbarsuche) | – |
| `metadata` | Übersetzung zwischen `MediaFile` und ExifTool-Tags je Format, Lesen in Blöcken, Speichern, Sidecar-Rückfall | `model`, `tf.exiftool` |
| `sync` | Track-Sync als reine Logik: Eingabe sind Tracks, Dateien und Parameter, Ausgabe ein Ergebnis je Datei | `model`, `track` |
| `enrich` | Orts-, Höhen- und Zeitzonenabfrage für eine Dateimenge: Zusammenfassen, Drosselung, Cache, Fortschritt | `model`, `tf.geo.service` |
| `preview` | Vorschau des ausgewählten Bildes. Das vorherige und das nächste werden vorab geladen, gehalten wird nur ein kleiner Speicher-Cache. JavaFX-Formate werden direkt gelesen, RAW und HEIC über das eingebettete JPEG aus ExifTool. | `model`, `tf.exiftool` |
| `ui` | Hauptfenster, Sync-Dialog, Einstellungen, Undo-Aktionen | alle obigen, `tf.geo.map`, JavaHelper |

Die Abhängigkeiten zeigen nur in eine Richtung: `ui` → `enrich` / `sync` / `metadata` / `preview` → `model`.

### Modell

- `MediaFile` hält zwei Wertsätze: `original` (zuletzt gelesen) und `pending` (geändert, noch nicht gespeichert). Angezeigt wird `pending`, wo vorhanden, sonst `original`.
- Der Status (hat Koordinaten / Ort / Höhe / ist geändert) wird aus den angezeigten Werten berechnet und speist die Filter.
- Eine Sammlung ist eine Liste von Dateien. Der Ordner bestimmt nur, wie sie befüllt wird. Mehrere Ordner pro Sammlung bräuchten später nur einen anderen Lade-Dialog und eine Ordner-Spalte.

## Hauptfenster

Das Layout ist an GeoSetter angelehnt:

- **Werkzeugleiste:** Ordnerpfad, Häkchen „Unterordner“, „Edit…“, „Find place“ (Ort ermitteln), „Find altitude“ (Höhe ermitteln), „Track sync…“, Undo, Redo und „Speichern (n)“ mit der Anzahl ungespeicherter Dateien. Eigene Icon-Leisten je Bereich kommen später.
- **Links oben:**
  - Statusfilter als Chips mit Anzahl.
  - Darunter die Dateiliste. Spalten: Name, Aufnahmezeit, Koordinaten (Grad/Min/Sek, z. B. `N46°40'48.05"; E11°5'28.61"`), Höhe, Ländercode, Land, Region, Ort, Sublocation, Zeitzone.
  - Spalten lassen sich über den Menüknopf im Tabellenkopf ausblenden. Geänderte, noch nicht gespeicherte Werte sind markiert (orange, fett).
  - Die Tabelle ist nicht editierbar. Die Auswahl kann mehrere Dateien umfassen.
- **Bearbeiten:** Dialog über Strg+E, Doppelklick oder „Edit…“ für die Auswahl. Bei Mehrfachauswahl zeigen unterschiedliche Werte „(different)“. Ungültige Eingaben sind rot und sperren OK. OK ist ein Undo-Schritt, Esc bricht ab.
- **Links unten:** große Vorschau mit den Knöpfen Erstes, Zurück, Weiter, Letztes, Einpassen und 100 %.
- **Rechts oben:** die Reiter „Karte“ und „Bild-Info (ExifTool)“.
- **Rechts unten:** GPX-Sektion. Links die Track-Liste mit Häkchen zum Laden und Einblenden sowie „+ GPX“. Rechts die Punkte der ausgewählten Tracks: Nr., Zeit, Koordinaten, Distanz, Höhe. Ein ausgewählter Punkt zentriert die Karte.
- **Statusleiste:** „n files (m with coordinates) - k selected“; die Anzahl ungespeicherter Dateien steht auf „Speichern (n)“.
- **Schrift:** 11 px.

Aktionen der Werkzeugleiste wirken auf die Auswahl. Ist nichts ausgewählt, wirken sie auf alle gefilterten Dateien, für die die Aktion sinnvoll ist. „Ort ermitteln“ läuft zum Beispiel über alle Dateien mit Koordinaten, aber ohne Ort. Mit Auswahl überschreibt die Aktion vorhandene Werte der ausgewählten Dateien (Entscheidung vom 15.11.2026). Ausnahme: „Edit…“ braucht immer eine Auswahl.

Nach dem Laden eines Ordners ist die Karte auf alle Dateien eingepasst. Die erste Datei wird für die Vorschau automatisch ausgewählt, verschiebt die Karte dabei aber nicht. Diese automatische Auswahl zählt für „Ort ermitteln“ nicht als Auswahl, erst eine eigene Auswahl des Nutzers (Entscheidung vom 15.11.2026). Während Laden, Speichern oder „Ort ermitteln“ laufen, sind auch Änderungen über die Karte gesperrt.

## Track-Sync-Dialog

**Eingaben**

- **Track-Quelle**, eine von:
  - alle geladenen Tracks
  - eine GPX-Datei
  - alle GPX-Dateien in einem Ordner **samt Unterordnern** (z. B. ein zentrales GPX-Archiv mit einem Ordner je Jahr; Entscheidung vom 18.11.2026)
  - GPX-Datei und -Ordner werden im Hintergrund gelesen, mit Fortschritt, und für die Sitzung gemerkt. Ein Index ist nicht nötig: üblich sind 1–2 Dateien bis 30 MB. Unlesbare Dateien und Dateien ohne Punkte mit Zeit stehen mit Grund unter der Zusammenfassung.
- **Dateien:** „Ohne Koordinaten“ (die gelisteten Dateien) oder „Auswahl“. Vorgewählt ist „Auswahl“, wenn der Nutzer selbst Dateien ausgewählt hat, sonst „Ohne Koordinaten“ (wie die Regel der Werkzeugleiste). Dazu das Häkchen „Vorhandene überschreiben“ für die Auswahl, standardmäßig aus.
- **Nur für die Dateien der Kamera** (Entscheidung vom 18.11.2026): Zeitzone und Offset gelten für Dateien mit lokaler Zeit ohne eigene Zone. Dateien mit eigener Zeit behalten sie: Fotos mit `OffsetTimeOriginal` und Videos mit QuickTime-UTC-Zeit, also typischerweise die des Smartphones, das auch den GPX-Track aufzeichnet. Die Vorschau zeigt bei ihnen „(own zone)“.
- **Zeitzone der Kamera:** vorbelegt mit der häufigsten Zeitzone aus den Maker Notes (`Canon:TimeZone`, Sommerzeit eingerechnet), sonst dem häufigsten `OffsetTimeOriginal` der Dateien als Hinweis, sonst der Systemzeitzone (Entscheidungen vom 18.11.2026). Die Maker Notes liest GeoTagger erst beim Öffnen des Dialogs für bis zu 20 Dateien, weil das Laden sie mit `-fast2` überspringt.
- **Zusätzlicher Offset:** Vorzeichen, Tage, Stunden, Minuten, Sekunden.
- **Kamera-Uhrzeit:** Diese Anzeige zeigt live die jetzige Systemzeit mit Zeitzone und Offset. Der Nutzer stellt Zeitzone und Offset so ein, dass sie mit dem Kameradisplay übereinstimmt.
- **Maximale Zeitdifferenz** zum nächsten Trackpunkt, standardmäßig 30 min.
- **Zuweisung:** zwischen den Nachbarpunkten interpolieren (Standard) oder exakt den nächsten Punkt nehmen.
- **Häkchen:**
  - „Keine Trackpunkte in der Zukunft berücksichtigen“
  - „Höhe aus Track übernehmen“ (Standard an). Es betrifft nur Dateien, die in diesem Lauf Koordinaten bekommen.
  - „Zeitzone in Datei schreiben“ (Standard aus). Es setzt die gewählte Kamera-Zeitzone als `OffsetTimeOriginal` für die zugeordneten Dateien der Kamera; eine eigene Zone bleibt.
- **Gemerkt** werden alle Parameter außer der Zeitzone: Track-Quelle, GPX-Datei, GPX-Ordner, Offset, maximale Differenz, Zuweisung, die Häkchen und „Vorhandene überschreiben“. Ungültige Eingaben (Zeitzone, Offset, maximale Differenz) sind rot und sperren „Apply“.

**Vorschau**

- Eine Tabelle mit den Spalten Datei, Kamerazeit, korrigierte Zeit (UTC), Ergebnis-Koordinate und zeitlicher Abstand zum nächsten echten Trackpunkt.
- Die Tabelle wird bei jeder Parameteränderung neu berechnet.
- Markierungen: zugeordnet, großer Abstand (über der halben maximalen Differenz) und kein Treffer. Darunter steht eine Zusammenfassung mit den Anzahlen.

**Übernehmen**

- Die Ergebnisse werden als **ein** Undo-Schritt in die ausstehenden Änderungen übernommen.
- Dabei wird nichts in die Dateien geschrieben.

**Algorithmus**

1. Die Kamerazeit wird mit Zeitzone und Offset in UTC umgerechnet. Hat die Datei eine eigene Zone (`OffsetTimeOriginal`) oder eine UTC-Zeit, gilt diese ohne Offset.
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
   - Koordinaten werden auf ein Raster von 0,001° (etwa 100 m) gerundet und zusammengefasst; abgefragt wird die Zellmitte.
   - Pro Rasterzelle gibt es eine Anfrage, deren Ergebnis für die Sitzung im Speicher bleibt, getrennt je Dienst und Sprache. Fehlgeschlagene Zellen werden nicht gespeichert.
   - Die Anfragen werden je Dienst gedrosselt: GeoNames 250 ms, Mapbox 100 ms Pause.
   - Vorübergehende Fehler werden nach 1, 2 und 4 s wiederholt. Ein erschöpftes Kontingent oder falsche Zugangsdaten brechen den Lauf ab; was bis dahin gefunden wurde, wird übernommen.
   - Ein gefundener Ort setzt alle fünf Ortsfelder; Teile, die der Dienst nicht liefert, werden geleert. Ausnahmen (Rückmeldung vom 16.11.2026): Der Ländername kommt aus dem Ländercode in der gewählten Sprache (z. B. „Vereinigte Staaten“), und ohne Sublocation vom Dienst bleibt die alte Sublocation, wenn der Ort gleich bleibt.
   - GeoNames: Abgefragt werden bis zu 30 Orte im Umkreis von 10 km (ohne Umkreis liefert GeoNames nur den nächsten Ort); liegt keiner im Umkreis, der nächste Ort überhaupt. Ort ist der nächste besiedelte Ort, der kein Stadtteil ist; ein Stadtteil (PPLX, z. B. Eixample in Barcelona) wird Sublocation, wenn er der nächste Ort überhaupt ist.
   - **Auswahl wie GeoSetter (Plan 5c):** Liegen mehrere Orte bzw. Stadtteile innerhalb des Umkreises aus den Einstellungen (Standard 1 km, 0 = nie fragen), fragt nach dem Lauf je Gruppe gleicher Kandidaten ein Fenster „Choose place“: Vorschau, Liste „Region; Ort; Stadtteil (Entfernung)“, editierbare Felder Region, Ort, Sublocation.
     - „Use“ übernimmt die Auswahl für diese Dateien, „Cancel“ lässt sie unverändert.
     - „Use for all“ (nur wenn weitere Gruppen folgen) beantwortet den Rest des Laufs ohne weitere Fenster. Eine andere Gruppe bekommt die Auswahl aber nur, wenn der gewählte Ort auch unter ihren Kandidaten ist, sonst ihre beste Antwort.
     - Angeboten werden nur Orte und Stadtteile, keine Firmen oder Gebäude (anders als GeoSetter). Mapbox liefert nur einen Treffer, dort wird nie gefragt.
     - Alles bleibt ein Undo-Schritt.
   - **Höhe (Plan 5b):** ohne Raster – jede Position einmal, denn die Höhe ändert sich schon auf wenigen Metern. Umfang wie bei „Find place“: die ausgewählten Dateien (überschrieben) oder alle gelisteten ohne Höhe.
     - Quelle aus den Einstellungen: zuerst die SRTM-Kacheln (`.hgt`, SRTM1 oder SRTM3) aus dem SRTM-Ordner in einem Aufruf, die Lücken per openrouteservice; oder nur eine der beiden.
     - Fehlende Kacheln: Rückfrage mit Kachelnamen und Größe nach oben („up to … MB“); bei „Ja“ lädt ein Hintergrund-Task sie mit Fortschritt. SRTM1 je Kachel von ESA (`step.esa.int`, 60° N – 56° S), SRTM3 in Blöcken von viewfinderpanoramas.org (Blocknamen wie in GPXEditor). Ein Download-Fehler stoppt die übrigen Downloads nicht und wird mit dem Ergebnis gemeldet; es geht mit den vorhandenen Kacheln weiter, bei „Nein“ ebenso. Ob eine Kachel da ist, entscheidet die Dateigröße (SRTM1 oder SRTM3), ohne die Datei zu lesen.
     - Interpolation: bilinear aus den vier Nachbarpunkten (Standard) oder nächster Gitterpunkt; Lücken in der Kachel (-32768) zählen nicht.
     - openrouteservice: höchstens 1000 Punkte je Anfrage, 1,5 s Pause zwischen den Anfragen (40 je Minute mit dem freien Schlüssel), Wiederholung nach 1, 2 und 4 s. Ein erschöpftes Kontingent, ein abgelehnter Schlüssel oder ein bleibender Fehler bricht den openrouteservice-Teil ab; die SRTM-Höhen bleiben. Ohne Schlüssel kommt die Meldung nur, wenn es Lücken gibt. Passt eine Antwort nicht zu den gefragten Punkten (Anzahl oder Position), gilt der Block als fehlgeschlagen.
     - Die Höhe wird auf 0,1 m gerundet.
   - Die Ergebnisse landen in `pending`, als ein Undo-Schritt pro Lauf. Danach zeigt die Statusleiste eine Zusammenfassung, bei Fehlern zusätzlich ein Hinweis.
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
| SRTM-Ordner fehlt bzw. openrouteservice-Schlüssel fehlt (nur openrouteservice) | „Find altitude“ meldet, was in den Einstellungen fehlt, und fragt nichts ab. |
| Download von SRTM-Kacheln schlägt fehl (z. B. Meereskachel bei ESA, HTML statt Zip, Verbindung hängt) | Die übrigen Downloads laufen weiter; ein Download ohne neue Daten für eine Minute bricht ab. Danach geht es mit den vorhandenen Kacheln und openrouteservice weiter, und die Meldung nach dem Lauf nennt je Kachel den Grund. Nicht gelieferte Kacheln werden in dieser Sitzung nicht noch einmal angeboten. Halb geladene Dateien bleiben nicht liegen, kaputte Kacheln (falsche Größe) werden ersetzt. |
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
  - nicht verwaltete Tags bleiben erhalten, geprüft an einer Smartphone-Datei mit `GPSImgDirection`, deren Koordinaten, Ort und Höhe geändert werden
- **Musterdateien:** je eine kleine Datei der echten Kameras (DSLR-RAW, Smartphone-JPEG/HEIC, Videos). Der Nutzer stellt sie bereit, sie liegen unter `src/test/resources`.
- **Online-Dienste:** in Tests über Mocks der Schnittstellen, dazu ein manueller Smoke-Test.
- **UI-Tests mit TestFX:** markiert mit `@Tag("UI")`, laufen über `gradlew uiTest` oder die Suite `UITests`. Abgedeckt sind die Abläufe ohne native Dialoge, z. B. Ordner laden, Filter, Bearbeiten, Undo/Redo und Speichern. Sie öffnen echte Fenster, Maus und Tastatur bleiben währenddessen unberührt.
- **Manuell bleiben:** native Datei- und Ordnerdialoge, Rückfragen per `Alert`, das Aussehen der Vorschau und später die Karte (WebView), dazu ein Durchgang mit einer echten Sammlung.
- **Testsammlungen:** `gradlew test` bzw. die Suite `NonUITests` laufen ohne UI. Alle Tests nutzen einen eigenen Preferences-Knoten (`tf/geotagger/test`), die echten Einstellungen bleiben unberührt.

## Mögliche nächste Schritte

Diese Punkte gehören nicht zu Version 1. Das Design soll sie aber ohne Umbau zulassen.

- **Aufnahmerichtung anzeigen:**
  - `MediaFile` bekommt ein nur lesbares Feld für `GPSImgDirection` und `GPSImgDirectionRef`.
  - Die Karte zeichnet am ausgewählten Marker eine Linie oder einen Blickkegel in diese Richtung, wie GeoSetter.
  - Geschätzter Aufwand: gering.
- **Aufnahmerichtung bearbeiten** (danach):
  - Ein Zielpunkt auf der Karte lässt sich ziehen, die App berechnet daraus den Winkel und schreibt ihn.
  - Der Bezug `T` (geografisch) bzw. `M` (magnetisch) muss dabei beachtet werden.
  - Kompassdaten aus GPX-Tracks sind nicht vorgesehen, weil kaum ein Gerät sie liefert.

## Ablösung von GeoSetter

Dieser Schritt kommt erst, wenn GeoTagger den Workflow vollständig abdeckt, also nach Plan 6, und GeoSetter nicht mehr genutzt wird. Bis dahin bleibt GeoSetter unverändert.

1. **Übrige Nutzer von GeoSetters ExifTool umstellen** auf das per winget installierte ExifTool (`C:\Users\thomas\AppData\Local\Programs\ExifTool\exiftool.exe`, 13.59 oder neuer), am besten über ExifToolHelper. Stand 2026-11-04:
   - GPXEditor `MakeImageJSON`: fester Pfad `C:\Program Files (x86)\GeoSetter beta\tools`
   - PicExporter `ConfigParameters`: derselbe Pfad als Vorgabe, wenn die Konfiguration kein `<exiftoolPath>` setzt
   - PicWorkflow: Arbeitsschritte, die im Text auf GeoSetter verweisen, auf GeoTagger umformulieren
   - die Windows-cmd-Abläufe der Bildverarbeitung: prüfen, ob sie ExifTool über den PATH oder einen GeoSetter-Pfad aufrufen
2. **Den PATH-Eintrag `C:\Users\Thomas\AppData\Roaming\GeoSetter_beta\tools` entfernen** (Benutzervariable `Path`). Danach liefert der PATH nur noch die winget-Version. NetBeans und die Terminals neu starten.
3. **Prüfen:**
   - `where exiftool` zeigt nur die winget-Version.
   - Die Tests von ExifToolHelper und GeoTagger sind ohne `EXIFTOOL_PATH` grün.
   - GPXEditor, PicExporter und PicWorkflow laufen.
4. GeoSetter deinstallieren. Dabei verschwinden auch `C:\Program Files (x86)\GeoSetter beta` und der AppData-Ordner.

## Offene Punkte für die Planung

- Geklärt: `-P` erhält unter Windows auch das Erstellungsdatum (ExifTool 12.57). `MetadataWriter` stellt es zur Sicherheit trotzdem selbst wieder her.

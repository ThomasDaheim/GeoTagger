/*
 * Copyright (c) 2014ff Thomas Feuster
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. The name of the author may not be used to endorse or promote products
 *    derived from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 * THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
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
    void elevationIsOptionalAndInvalidValuesDontBreakTheFile() throws IOException {
        final StringBuilder content = new StringBuilder("<gpx><trk><trkseg>");
        for (String ele : List.of("", "<ele/>", "<ele></ele>", "<ele> </ele>", "<ele>n/a</ele>", "<ele> 1200.5 </ele>")) {
            content.append("<trkpt lat=\"46.4\" lon=\"11.6\">").append(ele).append("<time>2026-09-02T09:08:00Z</time></trkpt>");
        }
        content.append("</trkseg></trk></gpx>");

        final List<TrackPoint> points = GpxReader.read(gpx("hoehe.gpx", content.toString()));

        assertEquals(6, points.size());
        for (int i = 0; i < 5; i++) {
            assertNull(points.get(i).elevation(), "point " + i);
        }
        assertEquals(1200.5, points.get(5).elevation());
    }

    @Test
    void readsTracksWithColors() throws IOException {
        final Path gpx = Files.writeString(dir.resolve("colors.gpx"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <gpx version="1.1" creator="test" xmlns="http://www.topografix.com/GPX/1/1"
                     xmlns:gpxx="http://www.garmin.com/xmlschemas/GpxExtensions/v3">
                  <trk><extensions><line xmlns="http://www.topografix.com/GPX/gpx_style/0/2"><color>00FF00</color></line></extensions>
                    <trkseg><trkpt lat="46.0" lon="11.0"><time>2026-11-04T10:00:00Z</time></trkpt>
                      <trkpt lat="46.1" lon="11.1"><time>2026-11-04T10:01:00Z</time></trkpt></trkseg>
                    <trkseg><trkpt lat="46.2" lon="11.2"/></trkseg>
                  </trk>
                  <trk><extensions><gpxx:TrackExtension><gpxx:DisplayColor>DarkYellow</gpxx:DisplayColor></gpxx:TrackExtension></extensions>
                    <trkseg><trkpt lat="47.0" lon="12.0"/><trkpt lat="95.0" lon="12.0"/></trkseg>
                  </trk>
                  <trk><trkseg><trkpt lat="48.0" lon="13.0"/></trkseg><trkseg/></trk>
                </gpx>
                """);
        final List<GpxLine> lines = GpxReader.readLines(gpx);
        assertEquals(4, lines.size());
        assertEquals(List.of("#00FF00", "#00FF00", "goldenrod"), lines.subList(0, 3).stream().map(GpxLine::color).toList());
        assertNull(lines.get(3).color());
        // points without time are drawn, points out of range are left out
        assertEquals(List.of(2, 1, 1, 1), lines.stream().map(line -> line.points().size()).toList());
        // the sync needs the time - only the two points with time
        assertEquals(2, GpxReader.read(gpx).size());
    }

    @Test
    void unusableColorsAreIgnored() {
        assertNull(GpxReader.cssColor("#12", null));
        assertNull(GpxReader.cssColor(null, "Purple"));
        assertNull(GpxReader.cssColor(null, "Transparent"));
        assertEquals("#ff0000", GpxReader.cssColor("#ff0000", "Blue"));
        assertEquals("darkred", GpxReader.cssColor(null, "DarkRed"));
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

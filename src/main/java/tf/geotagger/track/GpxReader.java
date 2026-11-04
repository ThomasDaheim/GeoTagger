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
            // ele is optional in GPX - read as text so that an invalid value only drops the elevation, not the file
            @JacksonXmlProperty(localName = "ele") String elevation,
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
     *
     * @param gpx GPX 1.0 or 1.1 file
     * @return all track points with time and position
     * @throws IOException if the file can't be read or isn't valid GPX
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
                        points.add(new TrackPoint(time, point.latitude(), point.longitude(), toDouble(point.elevation())));
                    }
                }
            }
        }
        return points;
    }

    /**
     * GPX files directly in dir (no sub folders), sorted by name.
     *
     * @param dir folder to search
     * @return the *.gpx files of the folder, case insensitive
     * @throws IOException if the folder can't be listed
     */
    public static List<Path> findGpxFiles(Path dir) throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(GpxReader::isGpxFile)
                    .sorted()
                    .toList();
        }
    }

    /**
     * @param file file to check
     * @return true if the file name ends with .gpx (case insensitive)
     */
    public static boolean isGpxFile(Path file) {
        return file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".gpx");
    }

    private static <T> List<T> nullToEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }

    private static Double toDouble(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Double.valueOf(text.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
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

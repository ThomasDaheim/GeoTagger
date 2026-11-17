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
package tf.geotagger.main;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.OptionalDouble;
import tf.geo.LatLon;
import tf.geo.service.ElevationProvider;
import tf.geo.service.GeoServiceException;
import tf.geo.service.JdkHttpTransport;
import tf.geo.service.OpenRouteServiceElevationProvider;
import tf.geo.service.SrtmElevationProvider;
import tf.geo.service.SrtmInterpolation;
import tf.geotagger.enrich.AltitudeEnricher;
import tf.geotagger.enrich.AltitudeSource;
import tf.geotagger.enrich.Sleeper;

/**
 * Builds the altitude sources chosen in the preferences.
 */
final class AltitudeServiceFactory {
    private static final String NO_FOLDER = "Please choose the folder of the SRTM files in the preferences";
    private static final String NO_KEY = "Please enter your openrouteservice API key in the preferences";
    // pause between two requests if openrouteservice can't be asked anyway
    private static final Duration NO_INTERVAL = Duration.ZERO;

    private AltitudeServiceFactory() {
    }

    /**
     * @return the SRTM files of the folder in the preferences, read with the interpolation in the preferences
     * @throws GeoServiceException of kind CONFIGURATION if the folder is empty or doesn't exist
     */
    static SrtmElevationProvider srtm() throws GeoServiceException {
        return srtm(GeoTaggerPreferences.SRTM_FOLDER.getAsString(), GeoTaggerPreferences.SRTM_INTERPOLATION.getAsType());
    }

    /**
     * @param folder folder of the SRTM files
     * @param interpolation how to read between the grid points
     * @return the SRTM files of the folder
     * @throws GeoServiceException of kind CONFIGURATION if the folder is empty or doesn't exist
     */
    static SrtmElevationProvider srtm(String folder, SrtmInterpolation interpolation) throws GeoServiceException {
        final String trimmed = folder == null ? "" : folder.trim();
        try {
            if (!trimmed.isEmpty() && Files.isDirectory(Path.of(trimmed))) {
                return new SrtmElevationProvider(Path.of(trimmed), interpolation);
            }
        } catch (InvalidPathException ex) {
            // as a missing folder
        }
        throw new GeoServiceException(GeoServiceException.Kind.CONFIGURATION, NO_FOLDER);
    }

    /**
     * @param apiKey openrouteservice API key, e.g. the one typed into the preferences but not saved yet
     * @return openrouteservice with this key
     * @throws GeoServiceException of kind CONFIGURATION if the key is empty
     */
    static OpenRouteServiceElevationProvider ors(String apiKey) throws GeoServiceException {
        final String key = apiKey == null ? "" : apiKey.trim();
        if (key.isEmpty()) {
            throw new GeoServiceException(GeoServiceException.Kind.CONFIGURATION, NO_KEY);
        }
        return new OpenRouteServiceElevationProvider(key, new JdkHttpTransport());
    }

    /**
     * @param sleeper waits between the requests
     * @return the enricher for the source in the preferences, with only the providers that source needs - "SRTM first"
     * without a key names the missing key only if there are gaps
     * @throws GeoServiceException of kind CONFIGURATION if the SRTM folder or (for "openrouteservice only") the key is
     * missing
     */
    static AltitudeEnricher enricher(Sleeper sleeper) throws GeoServiceException {
        final AltitudeSource source = GeoTaggerPreferences.ALTITUDE_SOURCE.getAsType();
        final SrtmElevationProvider srtm = source.usesSrtm() ? srtm() : null;
        ElevationProvider ors = null;
        Duration interval = NO_INTERVAL;
        if (source == AltitudeSource.ORS_ONLY) {
            final OpenRouteServiceElevationProvider provider = ors(GeoTaggerPreferences.ORS_API_KEY.getAsString());
            ors = provider;
            interval = provider.minRequestInterval();
        } else if (source.usesOrs()) {
            try {
                final OpenRouteServiceElevationProvider provider = ors(GeoTaggerPreferences.ORS_API_KEY.getAsString());
                ors = provider;
                interval = provider.minRequestInterval();
            } catch (GeoServiceException ex) {
                ors = new MissingKey();
            }
        }
        return new AltitudeEnricher(srtm, ors, source, sleeper, interval);
    }

    // stands in for openrouteservice without a key - tells the user only when it is needed
    private static final class MissingKey implements ElevationProvider {
        @Override
        public List<OptionalDouble> elevations(List<LatLon> positions) throws GeoServiceException {
            throw new GeoServiceException(GeoServiceException.Kind.CONFIGURATION, NO_KEY);
        }

        @Override
        public String name() {
            return "openrouteservice";
        }
    }
}

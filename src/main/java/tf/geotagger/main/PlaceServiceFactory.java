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

import tf.geo.service.GeoNamesReverseGeocoder;
import tf.geo.service.GeoServiceException;
import tf.geo.service.JdkHttpTransport;
import tf.geo.service.MapboxReverseGeocoder;
import tf.geo.service.ReverseGeocoder;
import tf.geotagger.enrich.PlaceService;

/**
 * Builds the reverse geocoder chosen in the preferences.
 */
final class PlaceServiceFactory {
    private PlaceServiceFactory() {
    }

    /**
     * @return the chosen service with its credentials and the language of the names
     * @throws GeoServiceException of kind CONFIGURATION if the user name or token of the service is missing
     */
    static ReverseGeocoder create() throws GeoServiceException {
        final String language = language();
        return switch (service()) {
            case MAPBOX -> new MapboxReverseGeocoder(credential(GeoTaggerPreferences.MAPBOX_ACCESS_TOKEN,
                    "Please enter your Mapbox access token in the preferences"), language, new JdkHttpTransport());
            case GEONAMES -> new GeoNamesReverseGeocoder(credential(GeoTaggerPreferences.GEONAMES_USERNAME,
                    "Please enter your GeoNames user name in the preferences"), language, new JdkHttpTransport());
        };
    }

    /**
     * @return key of the cache for the chosen service and language - other names, other cache
     */
    static String cacheKey() {
        return service().name() + "|" + language();
    }

    /**
     * @return the chosen service - GeoNames if the stored value is unknown
     */
    static PlaceService service() {
        try {
            return PlaceService.valueOf(GeoTaggerPreferences.PLACE_SERVICE.getAsString().trim());
        } catch (IllegalArgumentException ex) {
            return PlaceService.GEONAMES;
        }
    }

    private static String language() {
        return GeoTaggerPreferences.PLACE_LANGUAGE.getAsString().trim();
    }

    private static String credential(GeoTaggerPreferences preference, String missing) throws GeoServiceException {
        final String value = preference.getAsString().trim();
        if (value.isEmpty()) {
            throw new GeoServiceException(GeoServiceException.Kind.CONFIGURATION, missing);
        }
        return value;
    }
}

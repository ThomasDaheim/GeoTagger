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
package tf.geotagger.enrich;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import tf.geo.service.Place;
import tf.geotagger.model.Field;
import tf.geotagger.model.MediaFile;

/**
 * The field values a found place sets for a file - see spec "Datenfluss 3. Anreichern".
 */
public final class PlaceValues {
    private PlaceValues() {
    }

    /**
     * All place fields, so that no old place is mixed into the new one - with two exceptions:
     * <ul>
     * <li>the country name comes from the country code in the chosen language (GeoNames e.g. says "USA" in German,
     * Mapbox "Vereinigte Staaten") - with an empty language or an unknown code the name of the service stays;</li>
     * <li>without a sublocation from the service the file keeps its sublocation, if the city stays the same.</li>
     * </ul>
     *
     * @param place place found by the service
     * @param language language of the names, e.g. "de" - empty for the default of the service
     * @param file file to set the place for
     * @return new value per field, null deletes it - a field missing in the map stays as it is
     */
    public static Map<Field, Object> of(Place place, String language, MediaFile file) {
        return of(place, language, file, false);
    }

    /**
     * Like {@link #of(Place, String, MediaFile)} - but a place the user chose in "Choose place" sets the sublocation
     * as chosen: "Barcelona" without district deletes the old district.
     *
     * @param place place found by the service or chosen by the user
     * @param language language of the names, e.g. "de" - empty for the default of the service
     * @param file file to set the place for
     * @param chosen true if the user chose (and maybe edited) the place
     * @return new value per field, null deletes it - a field missing in the map stays as it is
     */
    public static Map<Field, Object> of(Place place, String language, MediaFile file, boolean chosen) {
        final Map<Field, Object> values = new EnumMap<>(Field.class);
        values.put(Field.COUNTRY_CODE, place.countryCode());
        values.put(Field.COUNTRY, countryName(place, language));
        values.put(Field.STATE, place.state());
        values.put(Field.CITY, place.city());
        if (chosen || place.sublocation() != null || !Objects.equals(place.city(), file.value(Field.CITY))) {
            values.put(Field.SUBLOCATION, place.sublocation());
        }
        return values;
    }

    private static String countryName(Place place, String language) {
        if (place.countryCode() == null || language == null || language.isBlank()) {
            return place.country();
        }
        final String name = Locale.of("", place.countryCode()).getDisplayCountry(Locale.forLanguageTag(language.trim()));
        // Java answers an unknown region with its code
        return name.isBlank() || name.equalsIgnoreCase(place.countryCode()) ? place.country() : name;
    }
}

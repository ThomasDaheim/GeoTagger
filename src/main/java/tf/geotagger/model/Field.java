package tf.geotagger.model;

import java.time.ZoneOffset;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * The metadata fields GeoTagger reads and writes - see spec, section "Felder".
 */
public enum Field {
    LATITUDE(Double.class),
    LONGITUDE(Double.class),
    ALTITUDE(Double.class),
    COUNTRY_CODE(String.class),
    COUNTRY(String.class),
    STATE(String.class),
    CITY(String.class),
    SUBLOCATION(String.class),
    TIME_OFFSET(ZoneOffset.class);

    public static final Set<Field> PLACE_FIELDS =
            Collections.unmodifiableSet(EnumSet.of(COUNTRY_CODE, COUNTRY, STATE, CITY, SUBLOCATION));

    private final Class<?> type;

    Field(Class<?> type) {
        this.type = type;
    }

    public Class<?> type() {
        return type;
    }
}

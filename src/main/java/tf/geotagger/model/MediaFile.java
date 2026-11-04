package tf.geotagger.model;

import java.nio.file.Path;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * One photo or video: the values last read from disk plus the changes that are not saved yet.
 * Shown values are the pending ones where present, otherwise the original ones.
 */
public final class MediaFile {
    private final Path path;
    private final MediaType mediaType;
    private Snapshot original;
    // a null value means "delete this field on save" - EnumMap allows null values
    private final EnumMap<Field, Object> pending = new EnumMap<>(Field.class);

    public MediaFile(Path path, MediaType mediaType, Snapshot original) {
        this.path = Objects.requireNonNull(path);
        this.mediaType = Objects.requireNonNull(mediaType);
        this.original = Objects.requireNonNull(original);
    }

    public Path path() {
        return path;
    }

    public MediaType mediaType() {
        return mediaType;
    }

    public Snapshot original() {
        return original;
    }

    public CaptureTime captureTime() {
        return original.captureTime();
    }

    public Object value(Field field) {
        return pending.containsKey(field) ? pending.get(field) : original.values().get(field);
    }

    public Double latitude() {
        return (Double) value(Field.LATITUDE);
    }

    public Double longitude() {
        return (Double) value(Field.LONGITUDE);
    }

    public Double altitude() {
        return (Double) value(Field.ALTITUDE);
    }

    public String text(Field field) {
        return (String) value(field);
    }

    public ZoneOffset timeOffset() {
        return (ZoneOffset) value(Field.TIME_OFFSET);
    }

    /**
     * Sets a value as pending change - null deletes the field on save.
     * Setting the original value again removes the pending change.
     */
    public void set(Field field, Object value) {
        final Object normalized = normalize(field, value);
        if (Objects.equals(normalized, original.values().get(field))) {
            pending.remove(field);
        } else {
            pending.put(field, normalized);
        }
    }

    public Map<Field, Object> pendingChanges() {
        return Collections.unmodifiableMap(new EnumMap<>(pending));
    }

    public boolean isChanged() {
        return !pending.isEmpty();
    }

    public boolean hasCoordinates() {
        return latitude() != null && longitude() != null;
    }

    public boolean hasAltitude() {
        return altitude() != null;
    }

    public boolean hasPlace() {
        return Field.PLACE_FIELDS.stream().anyMatch(field -> value(field) != null);
    }

    public void discardChanges() {
        pending.clear();
    }

    /**
     * After saving: the file was read again, the pending changes are now part of the original.
     */
    public void replaceOriginal(Snapshot fresh) {
        original = Objects.requireNonNull(fresh);
        pending.clear();
    }

    private static Object normalize(Field field, Object value) {
        if (value == null) {
            return null;
        }
        if (!field.type().isInstance(value)) {
            throw new IllegalArgumentException(field + " expects " + field.type().getSimpleName()
                    + " but got " + value.getClass().getSimpleName());
        }
        if (value instanceof String text) {
            // ExifTool gets one argument per line - no line breaks in values
            final String clean = text.replaceAll("[\\r\\n]+", " ").trim();
            return clean.isEmpty() ? null : clean;
        }
        return value;
    }

    @Override
    public String toString() {
        return path.toString();
    }
}

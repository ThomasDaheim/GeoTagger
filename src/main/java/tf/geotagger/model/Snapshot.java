package tf.geotagger.model;

import java.nio.file.attribute.FileTime;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Values of a file as last read from disk. A missing field means "not set in the file".
 *
 * @param captureTime may be null if the file has no capture time
 * @param lastModified modification time of the file when it was read - used to detect changes from outside
 */
public record Snapshot(CaptureTime captureTime, FileTime lastModified, Map<Field, Object> values) {
    public Snapshot {
        Objects.requireNonNull(lastModified);
        final EnumMap<Field, Object> copy = new EnumMap<>(Field.class);
        values.forEach((field, value) -> {
            if (value != null) {
                copy.put(field, value);
            }
        });
        values = Collections.unmodifiableMap(copy);
    }
}

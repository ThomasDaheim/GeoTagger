package tf.geotagger.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Capture time as stored in the file: camera local time (EXIF / XMP) or UTC (QuickTime CreateDate of videos).
 */
public record CaptureTime(LocalDateTime dateTime, boolean utc) {
    public CaptureTime {
        Objects.requireNonNull(dateTime);
    }
}

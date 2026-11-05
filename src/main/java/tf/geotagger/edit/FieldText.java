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
package tf.geotagger.edit;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.ZoneOffset;
import java.util.Locale;
import tf.geotagger.model.Field;

/**
 * Text of the field values in the details: formatting for display and checking / parsing the user's input.
 */
public final class FieldText {
    private FieldText() {
    }

    /**
     * @param field field of the value
     * @param value value of the field's type, may be null
     * @return the value as shown in the details - empty for null
     */
    public static String format(Field field, Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Double number) {
            return BigDecimal.valueOf(number).stripTrailingZeros().toPlainString();
        }
        if (value instanceof ZoneOffset offset) {
            // ZoneOffset.UTC.getId() is "Z"
            return ZoneOffset.UTC.equals(offset) ? "+00:00" : offset.getId();
        }
        return value.toString();
    }

    /**
     * @param field field to parse for
     * @param text text as entered - blank means "no value"
     * @return the value of the field's type, null for blank text
     * @throws IllegalArgumentException with a message for the user if the text isn't valid for the field
     */
    public static Object parse(Field field, String text) {
        final String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return switch (field) {
            case LATITUDE -> number(trimmed, "Latitude", 90);
            case LONGITUDE -> number(trimmed, "Longitude", 180);
            case ALTITUDE -> number(trimmed, "Altitude", Double.POSITIVE_INFINITY);
            case COUNTRY_CODE -> trimmed.toUpperCase(Locale.ROOT);
            case TIME_OFFSET -> offset(trimmed);
            default -> trimmed;
        };
    }

    private static Double number(String text, String name, double limit) {
        final double value;
        try {
            // comma as decimal separator is accepted as well
            value = Double.parseDouble(text.replace(',', '.'));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(name + " must be a number");
        }
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be a number");
        }
        if (Math.abs(value) > limit) {
            throw new IllegalArgumentException(name + " must be between -" + (int) limit + " and " + (int) limit);
        }
        return value;
    }

    private static ZoneOffset offset(String text) {
        try {
            return ZoneOffset.of(text);
        } catch (DateTimeException ex) {
            throw new IllegalArgumentException("Time zone must be an offset like +02:00 or -05:00");
        }
    }
}

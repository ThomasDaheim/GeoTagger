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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tf.geotagger.model.Field;

/**
 * Text of the field values in the details: formatting for display and checking / parsing the user's input.
 */
public final class FieldText {
    // the input formats follow LatLonHelper of GPXEditor, extended by decimal degrees with hemisphere ("48.1372° N")
    private static final String NUMBER = "(\\d+(?:[.,]\\d+)?)";
    private static final String DEGREE_SIGN = "\\s*[°º]";
    private static final String MINUTE_SIGN = "\\s*['′]";
    private static final String SECOND_SIGN = "\\s*(?:[\"″]|'')";
    // 48.1372 / 48.1372°
    private static final Pattern DEGREES = Pattern.compile(NUMBER + "(?:" + DEGREE_SIGN + ")?");
    // 48° 8.25'
    private static final Pattern DEGREES_MINUTES = Pattern.compile("(\\d+)" + DEGREE_SIGN + "\\s*" + NUMBER + MINUTE_SIGN);
    // 48° 8' 14.5"
    private static final Pattern DEGREES_MINUTES_SECONDS = Pattern.compile(
            "(\\d+)" + DEGREE_SIGN + "\\s*(\\d+)" + MINUTE_SIGN + "\\s*" + NUMBER + SECOND_SIGN);

    // positive / negative: letters of the hemispheres, in upper case
    private record DegreeFormat(String name, double limit, String positive, String negative, String decimalExample,
            String degreeExample) {
    }
    private static final DegreeFormat LATITUDE_FORMAT = new DegreeFormat("Latitude", 90, "N", "S", "46.5", "46° 30' 0\" N");
    // "O" is the German "Ost"
    private static final DegreeFormat LONGITUDE_FORMAT = new DegreeFormat("Longitude", 180, "EO", "W", "11.5", "11° 30' 0\" E");

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
            case LATITUDE -> degrees(trimmed, LATITUDE_FORMAT);
            case LONGITUDE -> degrees(trimmed, LONGITUDE_FORMAT);
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

    // decimal degrees with sign or hemisphere, or degrees / minutes / seconds - the hemisphere may come first or last
    private static Double degrees(String text, DegreeFormat format) {
        final IllegalArgumentException invalid = new IllegalArgumentException(format.name() + " must be decimal degrees like "
                + format.decimalExample() + " or degrees like " + format.degreeExample());
        String rest = text;
        int sign = 1;
        boolean hemisphere = false;
        final char first = Character.toUpperCase(rest.charAt(0));
        final char last = Character.toUpperCase(rest.charAt(rest.length() - 1));
        if (Character.isLetter(first)) {
            sign = hemisphereSign(first, format, invalid);
            hemisphere = true;
            rest = rest.substring(1).trim();
        } else if (Character.isLetter(last)) {
            sign = hemisphereSign(last, format, invalid);
            hemisphere = true;
            rest = rest.substring(0, rest.length() - 1).trim();
        }
        if (rest.startsWith("-") || rest.startsWith("+")) {
            if (hemisphere) {
                // "-48° N" - which one is meant?
                throw invalid;
            }
            sign = rest.startsWith("-") ? -1 : 1;
            rest = rest.substring(1).trim();
        }
        final double value = sign * unsignedDegrees(rest, invalid);
        if (Math.abs(value) > format.limit()) {
            throw new IllegalArgumentException(format.name() + " must be between -" + (int) format.limit() + " and "
                    + (int) format.limit());
        }
        return value;
    }

    private static int hemisphereSign(char letter, DegreeFormat format, IllegalArgumentException invalid) {
        if (format.positive().indexOf(letter) >= 0) {
            return 1;
        }
        if (format.negative().indexOf(letter) >= 0) {
            return -1;
        }
        throw invalid;
    }

    private static double unsignedDegrees(String text, IllegalArgumentException invalid) {
        Matcher matcher = DEGREES.matcher(text);
        if (matcher.matches()) {
            return decimal(matcher.group(1));
        }
        matcher = DEGREES_MINUTES.matcher(text);
        if (matcher.matches()) {
            return Integer.parseInt(matcher.group(1)) + belowSixty(decimal(matcher.group(2)), invalid) / 60.0;
        }
        matcher = DEGREES_MINUTES_SECONDS.matcher(text);
        if (matcher.matches()) {
            return Integer.parseInt(matcher.group(1)) + belowSixty(Integer.parseInt(matcher.group(2)), invalid) / 60.0
                    + belowSixty(decimal(matcher.group(3)), invalid) / 3600.0;
        }
        throw invalid;
    }

    private static double decimal(String text) {
        return Double.parseDouble(text.replace(',', '.'));
    }

    private static double belowSixty(double value, IllegalArgumentException invalid) {
        if (value >= 60) {
            throw invalid;
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

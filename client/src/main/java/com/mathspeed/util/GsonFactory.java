package com.mathspeed.util;

import com.google.gson.*;
import java.lang.reflect.Type;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Utility factory that registers Gson adapters for java.time types.
 */
public final class GsonFactory {

    private GsonFactory() { }

    public static Gson createGson() {
        return new GsonBuilder()
                .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
                .registerTypeAdapter(LocalDate.class, new LocalDateAdapter())
                .create();
    }

    // Adapter for LocalDateTime -> ISO_LOCAL_DATE_TIME (e.g. "2025-11-21T20:47:55")
    private static class LocalDateTimeAdapter implements JsonSerializer<LocalDateTime>, JsonDeserializer<LocalDateTime> {
        private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
        private static final DateTimeFormatter FORMATTER_NO_SECONDS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

        @Override
        public JsonElement serialize(LocalDateTime src, Type typeOfSrc, JsonSerializationContext context) {
            return src == null ? JsonNull.INSTANCE : new JsonPrimitive(src.format(FORMATTER));
        }

        @Override
        public LocalDateTime deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            if (json == null || json.isJsonNull()) {
                return null;
            }
            String s = json.getAsString();
            if (s == null || s.isEmpty()) {
                return null;
            }
            try {
                return LocalDateTime.parse(s, FORMATTER);
            } catch (DateTimeParseException ex) {
                // Try fallback without seconds (e.g. "2025-11-23T04:57")
                try {
                    return LocalDateTime.parse(s, FORMATTER_NO_SECONDS);
                } catch (DateTimeParseException ex2) {
                    throw new JsonParseException("Failed to parse LocalDateTime: " + s, ex2);
                }
            }
        }
    }

    // Adapter for LocalDate -> ISO_LOCAL_DATE (e.g. "2025-11-21")
    private static class LocalDateAdapter implements JsonSerializer<LocalDate>, JsonDeserializer<LocalDate> {
        private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

        @Override
        public JsonElement serialize(LocalDate src, Type typeOfSrc, JsonSerializationContext context) {
            return src == null ? JsonNull.INSTANCE : new JsonPrimitive(src.format(FORMATTER));
        }

        @Override
        public LocalDate deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            if (json == null || json.isJsonNull()) {
                return null;
            }
            String s = json.getAsString();
            if (s == null || s.isEmpty()) {
                return null;
            }
            return LocalDate.parse(s, FORMATTER);
        }
    }
}

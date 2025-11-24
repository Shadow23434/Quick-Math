package com.mathspeed.util;

import com.google.gson.*;
import java.time.*;
import java.time.format.DateTimeFormatter;

public final class GsonJavaTime {
    public static Gson create() {
        GsonBuilder gb = new GsonBuilder();
        DateTimeFormatter iso = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

        // LocalDateTime serializer
        JsonSerializer<LocalDateTime> ldtSerializer = (src, typeOfSrc, context) -> {
            if (src == null) return JsonNull.INSTANCE;
            return new JsonPrimitive(src.format(iso));
        };

        // LocalDateTime deserializer: accept "yyyy-MM-ddTHH:mm:ss" or epoch millis (number)
        JsonDeserializer<LocalDateTime> ldtDeserializer = (json, typeOfT, context) -> {
            if (json == null || json.isJsonNull()) return null;
            JsonPrimitive prim = json.getAsJsonPrimitive();
            try {
                if (prim.isNumber()) {
                    long epoch = prim.getAsLong();
                    return LocalDateTime.ofInstant(Instant.ofEpochMilli(epoch), ZoneId.systemDefault());
                } else if (prim.isString()) {
                    String s = prim.getAsString();
                    // try parse ISO first
                    try { return LocalDateTime.parse(s, iso); }
                    catch (Exception e) {
                        // try parsing as epoch string
                        try {
                            long epoch = Long.parseLong(s);
                            return LocalDateTime.ofInstant(Instant.ofEpochMilli(epoch), ZoneId.systemDefault());
                        } catch (Exception ex) {
                            // last resort: parse using ISO_INSTANT
                            try {
                                Instant inst = Instant.parse(s);
                                return LocalDateTime.ofInstant(inst, ZoneId.systemDefault());
                            } catch (Exception ignored) {}
                        }
                    }
                }
            } catch (Exception ignored) {}
            return null;
        };

        gb.registerTypeAdapter(LocalDateTime.class, ldtSerializer);
        gb.registerTypeAdapter(LocalDateTime.class, ldtDeserializer);

        // If you also use LocalDate / LocalTime / Instant, consider registering those too:
        gb.registerTypeAdapter(Instant.class, (JsonDeserializer<Instant>) (json, type, ctx) -> {
            if (json == null || json.isJsonNull()) return null;
            JsonPrimitive p = json.getAsJsonPrimitive();
            if (p.isNumber()) return Instant.ofEpochMilli(p.getAsLong());
            String s = p.getAsString();
            try { return Instant.parse(s); } catch (Exception e) { return null; }
        });
        gb.registerTypeAdapter(Instant.class, (JsonSerializer<Instant>) (src, type, ctx) -> src == null ? JsonNull.INSTANCE : new JsonPrimitive(src.toString()));

        return gb.create();
    }
}

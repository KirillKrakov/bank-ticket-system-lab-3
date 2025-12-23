package com.example.applicationservice.util;

import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

public class CursorUtil {

    public static class Decoded {
        public final Instant timestamp;
        public final UUID id;

        public Decoded(Instant timestamp, UUID id) {
            this.timestamp = timestamp;
            this.id = id;
        }
    }

    public static Decoded decode(String cursor) {
        if (cursor == null || cursor.trim().isEmpty()) {
            return null;
        }

        try {
            String decoded = new String(Base64.getDecoder().decode(cursor));
            String[] parts = decoded.split("\\|");
            if (parts.length != 2) {
                return null;
            }

            Instant timestamp = Instant.parse(parts[0]);
            UUID id = UUID.fromString(parts[1]);

            return new Decoded(timestamp, id);
        } catch (Exception e) {
            return null;
        }
    }

    public static String encode(Instant timestamp, UUID id) {
        String data = timestamp.toString() + "|" + id.toString();
        return Base64.getEncoder().encodeToString(data.getBytes());
    }
}
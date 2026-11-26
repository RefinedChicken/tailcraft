package io.github.refinedchicken.tailcraft;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.Set;

/**
 * One JSON line from the helper's stdout. For {@code ready} events, {@code address} is the
 * Tailcraft address in host mode and the local loopback listener in join mode.
 */
public record HelperEvent(String type, String mode, String address, String code, String message) {
    private static final Set<String> TYPES = Set.of("ready", "error", "stopped");
    private static final Set<String> FIELDS = Set.of("type", "mode", "address", "code", "message");

    public static HelperEvent parse(String line) throws IOException {
        try {
            JsonObject o = JsonParser.parseString(line).getAsJsonObject();
            for (String key : o.keySet()) {
                if (!FIELDS.contains(key)) {
                    throw new IOException("unknown helper event field: " + key);
                }
            }
            String type = required(o, "type");
            if (!TYPES.contains(type)) {
                throw new IOException("unknown helper event type: " + type);
            }
            HelperEvent e = new HelperEvent(type, optional(o, "mode"), optional(o, "address"),
                    optional(o, "code"), optional(o, "message"));
            if (type.equals("ready")) {
                if ("host".equals(e.mode)) {
                    if (!TailcraftAddress.isAddress(e.address)) throw new IOException("host ready event lacks a Tailcraft address");
                } else if ("join".equals(e.mode)) {
                    if (e.address == null || !e.address.startsWith("127.0.0.1:")) throw new IOException("join ready address is not loopback");
                } else {
                    throw new IOException("invalid ready mode");
                }
            }
            return e;
        } catch (IOException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new IOException("malformed helper event", e);
        }
    }

    private static String required(JsonObject o, String key) throws IOException {
        String value = optional(o, key);
        if (value == null) {
            throw new IOException("missing " + key);
        }
        return value;
    }

    private static String optional(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : null;
    }
}

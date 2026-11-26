package io.github.refinedchicken.tailcraft.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * {@code config/tailcraft.json}. {@code address} is informational: it is rewritten from the key
 * file on every start so admins can copy it, and is never read back as truth. The private key
 * lives in a separate file so this one is safe to paste into support chats.
 */
final class ServerConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final Path path;
    private final JsonObject json;

    private ServerConfig(Path path, JsonObject json) {
        this.path = path;
        this.json = json;
    }

    static ServerConfig load(Path path) throws IOException {
        if (!Files.exists(path)) {
            JsonObject json = new JsonObject();
            json.addProperty("enabled", true);
            ServerConfig config = new ServerConfig(path, json);
            config.save();
            return config;
        }
        try {
            JsonObject json = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
            return new ServerConfig(path, json);
        } catch (RuntimeException e) {
            throw new IOException("Could not parse " + path + ": " + e.getMessage(), e);
        }
    }

    boolean enabled() {
        return !json.has("enabled") || json.get("enabled").getAsBoolean();
    }

    synchronized void writeAddress(String address) throws IOException {
        json.addProperty("address", address);
        save();
    }

    private void save() throws IOException {
        Files.createDirectories(path.getParent());
        Path tmp = Files.createTempFile(path.getParent(), ".tailcraft-", ".json");
        try {
            Files.writeString(tmp, GSON.toJson(json) + "\n", StandardCharsets.UTF_8);
            try {
                Files.move(tmp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
}

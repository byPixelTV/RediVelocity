package de.bypixeltv.redivelocity.utils;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.velocitypowered.api.util.UuidUtils;
import de.bypixeltv.redivelocity.RediVelocity;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.UUID;

@Singleton
public class MojangUtils {

    private final RediVelocity rediVelocity;

    @Inject
    public MojangUtils(RediVelocity rediVelocity) {
        this.rediVelocity = rediVelocity;
    }

    public UUID getUUID(String username) {
        try {
            URI url = new URI("https://api.mojang.com/users/profiles/minecraft/" + username);
            BufferedReader reader = new BufferedReader(new InputStreamReader(url.toURL().openStream()));
            JsonObject jsonObject = new Gson().fromJson(reader, JsonObject.class);
            String uuid = jsonObject.get("id").getAsString();
            return UuidUtils.fromUndashed(uuid);
        } catch (IOException | URISyntaxException e) {
            rediVelocity.sendErrorLogs("Failed to get UUID for " + username + "!");
        }
        return null;
    }

    public String getName(UUID uuid) {
        try {
            URI url = new URI("https://api.mojang.com/user/profile/" + UuidUtils.toUndashed(uuid));
            BufferedReader reader = new BufferedReader(new InputStreamReader(url.toURL().openStream()));
            JsonObject jsonObject = new Gson().fromJson(reader, JsonObject.class);
            return jsonObject.get("name").getAsString();
        } catch (IOException | URISyntaxException e) {
            rediVelocity.sendErrorLogs("Failed to get Name for " + uuid + "!");
        }
        return null;
    }

    public boolean isValidUUID(String uuidString) {
        try {
            UUID.fromString(uuidString);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
/*
 * This file is part of LanternServer, licensed under the MIT License (MIT).
 *
 * Copyright (c) LanternPowered <https://github.com/LanternPowered>
 * Copyright (c) SpongePowered <https://www.spongepowered.org>
 * Copyright (c) Contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the Software), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED AS IS, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.lanternpowered.server.profile;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.lanternpowered.server.util.UUIDHelper;
import org.spongepowered.api.profile.GameProfile;
import org.spongepowered.api.profile.ProfileNotFoundException;
import org.spongepowered.api.profile.property.ProfileProperty;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class GameProfileQuery {

    private final static Gson GSON = new Gson();

    static GameProfile queryProfileByUUID(UUID uniqueId, boolean signed) throws IOException, ProfileNotFoundException {
        final URL url = new URL("https://sessionserver.mojang.com/session/minecraft/profile/"
                + UUIDHelper.toFlatString(uniqueId) + (signed ? "?unsigned=false" : ""));

        int attempts = 0;
        while (true) {
            URLConnection uc = url.openConnection();
            InputStream is = uc.getInputStream();

            // Can be empty if the unique id invalid is
            if (is.available() == 0) {
                throw new ProfileNotFoundException("Failed to find a profile with the uuid: " + uniqueId);
            }

            // If it fails too many times, just leave it
            if (++attempts > 6) {
                throw new IOException("Failed to retrieve the profile after 6 attempts: " + uniqueId);
            }

            JsonObject json = GSON.fromJson(new InputStreamReader(is), JsonObject.class);
            if (json.has("error")) {
                // Too many requests, lets wait for 10 seconds
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    throw new IOException("Something interrupted the next attempt delay.");
                }
                continue;
            }

            String name = json.get("name").getAsString();
            Multimap<String, ProfileProperty> properties = LinkedHashMultimap.create();

            if (json.has("properties")) {
                JsonArray array = json.get("properties").getAsJsonArray();
                for (JsonElement element : array) {
                    JsonObject property = (JsonObject) element;

                    String propName = property.get("name").getAsString();
                    String value = property.get("value").getAsString();
                    String signature = property.has("signature") ? property.get("signature").getAsString() : null;

                    properties.put(propName, new LanternProfileProperty(propName, value, signature));
                }
            }

            return new LanternGameProfile(uniqueId, name, properties);
        }
    }

    static Map<String, UUID> queryUUIDByName(Iterable<String> names) throws IOException {
        Map<String, UUID> results = Maps.newHashMap();
        if (!names.iterator().hasNext()) {
            return results;
        }
        List<String> namesList = Lists.newArrayList(names);
        int size = namesList.size();
        int count = 0;
        do {
            int index = count;
            count += 100;
            if (count > size) {
                count = size;
            }
            postNameToUUIDPart(results, namesList.subList(index, count));
        } while (namesList.size() - count > 0);
        return results;
    }

    private static void postNameToUUIDPart(Map<String, UUID> results, List<String> names) throws IOException {
        String body = GSON.toJson(names);
        URL url = new URL("https://api.mojang.com/profiles/minecraft");

        HttpURLConnection uc = (HttpURLConnection) url.openConnection();
        uc.setRequestMethod("POST");
        uc.setRequestProperty("Content-Type", "application/json");
        uc.setUseCaches(false);
        uc.setDoInput(true);
        uc.setDoOutput(true);

        DataOutputStream os = new DataOutputStream(uc.getOutputStream());
        os.write(body.getBytes());
        os.flush();
        os.close();

        JsonArray json = GSON.fromJson(new InputStreamReader(uc.getInputStream()), JsonArray.class);
        for (JsonElement element : json) {
            JsonObject obj = element.getAsJsonObject();
            results.put(obj.get("name").getAsString(), UUIDHelper.fromFlatString(obj.get("id").getAsString()));
        }
    }
}
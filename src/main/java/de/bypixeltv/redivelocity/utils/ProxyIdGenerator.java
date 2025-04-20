/*
 * Copyright (c) 2025.
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 *  along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package de.bypixeltv.redivelocity.utils;

import de.bypixeltv.redivelocity.jedisWrapper.RedisController;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.security.SecureRandom;
import java.util.Set;

@Singleton
public class ProxyIdGenerator {
    private static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int ID_LENGTH = 8;
    private static final String PROXIES_KEY = "rv-proxies";

    private final RedisController redisController;
    private final SecureRandom random = new SecureRandom();

    @Inject
    public ProxyIdGenerator(RedisController redisController) {
        this.redisController = redisController;
    }

    public String generate() {
        Set<String> existingProxies = redisController.getAllHashFields(PROXIES_KEY);
        String proxyKey;

        // Generiere einen eindeutigen zufälligen ID-String
        do {
            proxyKey = "Proxy-" + generateRandomString();
        } while (existingProxies.contains(proxyKey));

        return proxyKey;
    }

    private String generateRandomString() {
        StringBuilder sb = new StringBuilder(ID_LENGTH);
        for (int i = 0; i < ID_LENGTH; i++) {
            sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
        }
        return sb.toString();
    }
}
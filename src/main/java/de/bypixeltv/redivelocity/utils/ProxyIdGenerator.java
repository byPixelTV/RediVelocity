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

import java.util.List;

@Singleton
public class ProxyIdGenerator {
    private final RedisController redisController;

    @Inject
    public ProxyIdGenerator(RedisController redisController) {
        this.redisController = redisController;
    }

    public String generate() {
        List<String> proxies = redisController.getAllHashValues("rv-proxies");

        if (proxies.isEmpty()) {
            redisController.deleteString("rv-proxies-counter");
        }

        long newId = redisController.increment("rv-proxies-counter");

        return "Proxy-" + newId;
    }
}
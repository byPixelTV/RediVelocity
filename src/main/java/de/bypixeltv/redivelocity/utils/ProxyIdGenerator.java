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
import java.util.Set;

@Singleton
public class ProxyIdGenerator {
    private final RedisController redisController;

    @Inject
    public ProxyIdGenerator(RedisController redisController) {
        this.redisController = redisController;
    }

    public String generate() {
        Set<String> proxiesList = redisController.getAllHashFields("rv-proxies");
        List<Integer> ids = proxiesList.stream()
                .map(proxy -> proxy.replace("Proxy-", ""))
                .map(Integer::parseInt)
                .sorted()
                .toList();

        int newId = 1;
        for (int id : ids) {
            if (id == newId) {
                newId++;
            } else {
                break;
            }
        }
        return "Proxy-" + newId; // Return the counter as the new ID after the loop
    }
}
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

package de.bypixeltv.redivelocity.listeners;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyPingEvent;
import de.bypixeltv.redivelocity.jedisWrapper.RedisController;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class ProxyPingListener {

    private final RedisController redisController;

    @Inject
    public ProxyPingListener(RedisController redisController) {
        this.redisController = redisController;
    }

    @Subscribe()
    @SuppressWarnings("unused")
    public void onProxyPing(ProxyPingEvent event) {
        String players = redisController.getString("rv-global-playercount");
        var ping = event.getPing().asBuilder();
        ping.onlinePlayers(players != null ? Integer.parseInt(players) : 0);
        event.setPing(ping.build());
    }
}
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

package dev.bypixel.redivelocity.config;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class Config {
    private int configVersion = 11;
    private RedisConfig redis = new RedisConfig();
    private CloudSupportConfig cloud = new CloudSupportConfig();
    private VersionControlConfig versionControl = new VersionControlConfig();
    private MessagesConfig messages = new MessagesConfig();
    private ResourcePackConfig resourcepack = new ResourcePackConfig();
    private JoingateConfig joingate = new JoingateConfig();
    private boolean jsonFormat;
    private boolean playerCountSync;
    private boolean debugMode;

    @Getter
    @Setter
    public static class RedisConfig {
        private String host = "127.0.0.1";
        private int port = 6379;
        private String username = "default";
        private String password = "password";
        private boolean useSsl = false;
        private String channel = "redivelocity-players";
    }

    @Getter
    @Setter
    public static class CloudSupportConfig {
        private boolean enabled = false;
        private String cloudSystem = "simplecloud"; // Options: simplecloud, vulpescloud
    }

    @Getter
    @Setter
    public static class VersionControlConfig {
        private boolean enabled = false;
        private List<Integer> allowedVersions = List.of(769, 768, 767, 770);
        private String kickMessage = "<dark_grey>- <dark_red>Version</dark_red> -</dark_grey><br><br><grey>You have to use the <aqua>Version</aqua> <blue>1.21+</blue> to play on <aqua>Example.net</aqua>.<br>Please update your <aqua>Version</aqua> to join the <aqua>Server</aqua>!</grey>";
    }

    @Getter
    @Setter
    public static class JoingateConfig {
        private Boolean allowJavaClients = true;
        private Boolean floodgateHook = true;
        private Boolean allowBedrockClients = true;
    }

    @Getter
    @Setter
    public static class MessagesConfig {
        private String prefix = "<grey>[<aqua>RediVelocity</aqua>]</grey>";
    }

    @Getter
    @Setter
    public static class ResourcePackConfig {
        private boolean enabled = false;
        private boolean forceResourcepack = false;
        private String resourcepackUrl = "https://example.net/resourcepack.zip";
        private String resourcepackMessage = "<dark_grey>- <dark_red>Resourcepack</dark_red> -</dark_grey><br><br><grey>You have to download the <aqua>Resourcepack</aqua> to play on <aqua>Example.net</aqua>.<br>Please click on accept to download the <aqua>Resourcepack</aqua> and join the <aqua>Server</aqua>!</grey>";
        private String resourcepackKickMessage = "<dark_grey>- <dark_red>Resourcepack</dark_red> -</dark_grey><br><br><grey>You have to download the <aqua>Resourcepack</aqua> to play on <aqua>Example.net</aqua>.<brPlease click on accept to download the <aqua>Resourcepack</aqua> and join the <aqua>Server</aqua>!</grey>";
    }
}
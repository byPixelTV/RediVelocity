package de.bypixeltv.redivelocity.config;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Config {
    private int configVersion = 4;
    private RedisConfig redis = new RedisConfig();
    private CloudNetConfig cloudnet = new CloudNetConfig();
    private VersionControlConfig versionControl = new VersionControlConfig();
    private MessagesConfig messages = new MessagesConfig();
    private ResourcePackConfig resourcepack = new ResourcePackConfig();
    private boolean jsonFormat;
    private boolean playerCountSync;

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
    public static class CloudNetConfig {
        private boolean enabled = false;
        private boolean cloudnetUseServiceId = true;
    }

    @Getter
    @Setter
    public static class VersionControlConfig {
        private boolean enabled = false;
        private int protocolVersion = 754;
        private String kickMessage = "<dark_grey>- <dark_red>Version</dark_red> -</dark_grey><br><br><grey>You have to use the <aqua>Version</aqua> <blue>1.16.5</blue> to play on <aqua>Example.net</aqua>.<br>Please update your <aqua>Version</aqua> to join the <aqua>Server</aqua>!</grey>";
    }

    @Getter
    @Setter
    public static class MessagesConfig {
        private String prefix = "<dark_gray>[<aqua>ℹ</aqua>]</dark_gray> <color:#0079FF>⌞RediVelocity⌝</color> <dark_gray>◘</dark_gray>";
        private String kickMessage = "<dark_grey>- <dark_red>Sorry, but you are blocked</dark_red> -</dark_grey><br><br><grey>You got blocked from <aqua>Example.net</aqua>! You are not allowed to join this <aqua>Network</aqua>.<br>If you think this is a mistake, you can join our <blue>Discord Server</blue> to get help!</grey><br><br><blue><b><u>dc.example.net</u></b></blue>";
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
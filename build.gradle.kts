plugins {
    id("com.gradleup.shadow") version "8.3.7"
    id("java")
}

group = "dev.bypixel"
version = "1.2.0-SNAPSHOT"

repositories {
    // Maven central snapshots
    maven {
        url = uri("https://s01.oss.sonatype.org/content/repositories/snapshots")
    }

    // maven central releases
    mavenCentral()

    // papermc
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }

    // geysermc & floodgate
    maven("https://repo.opencollab.dev/main/")

    // vulpescloud
    maven("https://repo.vulpescloud.de/snapshots")

    // simplecloud
    maven("https://repo.simplecloud.app/snapshots")
    maven("https://buf.build/gen/maven")
}

dependencies {
    compileOnly("com.velocitypowered:velocity-api:3.4.0-SNAPSHOT")
    annotationProcessor("com.velocitypowered:velocity-api:3.4.0-SNAPSHOT")

    // Jedis and SnakeYAML
    implementation("redis.clients:jedis:6.0.0")
    implementation("org.yaml:snakeyaml:2.4")

    // CommandAPI
    implementation("dev.jorel:commandapi-velocity-shade:10.1.0")

    implementation("org.json:json:20250517")

    // Lombok dependencies
    annotationProcessor("org.projectlombok:lombok:1.18.38")
    compileOnly("org.projectlombok:lombok:1.18.38")

    compileOnly("app.simplecloud.api.platform:velocity:0.0.5-dev.1745077021664-28517d8")

    compileOnly("org.geysermc.geyser:api:2.4.2-SNAPSHOT")
    compileOnly("org.geysermc.floodgate:api:2.2.3-SNAPSHOT")

    val vulpesCloudVersion = "2.0.0-ALPHA"
    compileOnly("de.vulpescloud", "VulpesCloud-bridge", vulpesCloudVersion)
    compileOnly("de.vulpescloud", "VulpesCloud-api", vulpesCloudVersion)
}

sourceSets {
    getByName("main") {
        java {
            srcDir("src/main/java")
        }
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
        options.release.set(21)
    }

    shadowJar {
        archiveBaseName.set("RediVelocity")
        archiveVersion.set(version.toString())
        archiveClassifier.set("")

        relocate("redis.clients", "dev.bypixel.shaded.redis.clients")
        relocate("org.yaml.snakeyaml", "dev.bypixel.shaded.org.yaml.snakeyaml")
        relocate("dev.jorel.commandapi", "dev.bypixel.shaded.dev.jorel.commandapi")
        relocate("org.json", "dev.bypixel.shaded.org.json")
    }

    build {
        dependsOn(shadowJar)
    }
}

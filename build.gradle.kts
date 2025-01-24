plugins {
    id("com.gradleup.shadow") version "8.3.5"
    id("java")
}

group = "de.bypixeltv"
version = "1.1.1-Beta"

repositories {
    maven {
        url = uri("https://s01.oss.sonatype.org/content/repositories/snapshots")
    }

    mavenCentral()

    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }

    maven {
        name = "simplecloud"
        url = uri("https://repo.thesimplecloud.eu/artifactory/list/gradle-release-local")
    }

    maven {
        url = uri("https://repo.opencollab.dev/main/")
    }

    maven {
        url = uri("https://repo.vulpescloud.de/snapshots")
    }

}

dependencies {
    compileOnly("com.velocitypowered:velocity-api:3.4.0-SNAPSHOT")

    // Jedis and SnakeYAML
    implementation("redis.clients:jedis:5.2.0")
    implementation("org.yaml:snakeyaml:2.3")

    // CommandAPI
    implementation("dev.jorel:commandapi-velocity-shade:9.6.2-SNAPSHOT")

    implementation("org.json:json:20250107")

    // Lombok dependencies
    annotationProcessor("org.projectlombok:lombok:1.18.36")
    compileOnly("org.projectlombok:lombok:1.18.36")

    // CloudNet
    val cloudNetVersion = "4.0.0-RC11.1"
    compileOnly("eu.cloudnetservice.cloudnet:driver:$cloudNetVersion")
    compileOnly("eu.cloudnetservice.cloudnet:bridge:$cloudNetVersion")
    compileOnly("eu.cloudnetservice.cloudnet:wrapper-jvm:$cloudNetVersion")

    val simpleCloudVersion = "2.8.1"
    compileOnly("eu.thesimplecloud.simplecloud", "simplecloud-api", simpleCloudVersion)
    compileOnly("eu.thesimplecloud.simplecloud", "simplecloud-plugin", simpleCloudVersion)

    compileOnly("org.geysermc.geyser:api:2.4.2-SNAPSHOT")
    compileOnly("org.geysermc.floodgate:api:2.2.3-SNAPSHOT")

    val vulpesCloudVersion = "1.0.0-alpha2"
    compileOnly("de.vulpescloud", "VulpesCloud-wrapper", vulpesCloudVersion)
}

sourceSets {
    getByName("main") {
        java {
            srcDir("src/main/java")
        }
    }
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
        options.release.set(23)
    }

    shadowJar {
        archiveBaseName.set("RediVelocity")
        archiveVersion.set(version.toString())
        archiveClassifier.set("")

        relocate("redis.clients", "de.bypixeltv.shaded.redis.clients")
        relocate("org.yaml.snakeyaml", "de.bypixeltv.shaded.org.yaml.snakeyaml")
        relocate("dev.jorel.commandapi", "de.bypixeltv.shaded.dev.jorel.commandapi")
        relocate("org.json", "de.bypixeltv.shaded.org.json")
    }

    build {
        dependsOn(shadowJar)
    }
}

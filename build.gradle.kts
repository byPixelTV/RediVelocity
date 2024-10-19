plugins {
    id("io.github.goooler.shadow") version "8.1.8"
    id("java")
}

group = "de.bypixeltv"
version = "1.0.3"

repositories {
    maven {
        url = uri("https://s01.oss.sonatype.org/content/repositories/snapshots")
    }

    mavenCentral()
    maven {
        name = "jitpack"
        url = uri("https://jitpack.io")
    }
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
    maven {
        name = "simplecloud"
        url = uri("https://repo.thesimplecloud.eu/artifactory/list/gradle-release-local")
    }
}

dependencies {
    compileOnly("com.velocitypowered:velocity-api:3.3.0-SNAPSHOT")

    // Jedis and SnakeYAML
    implementation("redis.clients:jedis:5.2.0")
    implementation("org.yaml:snakeyaml:2.3")

    // CommandAPI and Jakarta Inject
    implementation("dev.jorel:commandapi-velocity-shade:9.6.0-SNAPSHOT")

    implementation("org.json:json:20240303")

    // Lombok dependencies
    annotationProcessor("org.projectlombok:lombok:1.18.34")
    compileOnly("org.projectlombok:lombok:1.18.34")

    // CloudNet
    val cloudNetVersion = "4.0.0-SNAPSHOT"
    compileOnly("eu.cloudnetservice.cloudnet:driver:$cloudNetVersion")
    compileOnly("eu.cloudnetservice.cloudnet:bridge:$cloudNetVersion")
    compileOnly("eu.cloudnetservice.cloudnet:wrapper-jvm:$cloudNetVersion")

    val simpleCloudVersion = "2.8.1"
    compileOnly("eu.thesimplecloud.simplecloud", "simplecloud-api", simpleCloudVersion)
    compileOnly("eu.thesimplecloud.simplecloud", "simplecloud-plugin", simpleCloudVersion)
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

    build {
        dependsOn(shadowJar)
    }
}
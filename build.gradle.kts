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
}

dependencies {
    compileOnly("com.velocitypowered:velocity-api:3.3.0-SNAPSHOT")

    // Jedis and SnakeYAML
    implementation("redis.clients:jedis:5.2.0")
    implementation("org.yaml:snakeyaml:2.3")

    // CommandAPI and Jakarta Inject
    implementation("dev.jorel:commandapi-velocity-shade:9.6.0-SNAPSHOT")

    // Lombok dependencies
    annotationProcessor("org.projectlombok:lombok:1.18.34")
    compileOnly("org.projectlombok:lombok:1.18.34")

    // CloudNet
    val cloudNetVersion = "4.0.0-SNAPSHOT"
    compileOnly(platform("eu.cloudnetservice.cloudnet:bom:$cloudNetVersion"))
    compileOnly("eu.cloudnetservice.cloudnet", "bridge")
    compileOnly("eu.cloudnetservice.cloudnet", "wrapper-jvm")
    compileOnly("eu.cloudnetservice.cloudnet", "platform-inject-api")
    compileOnly("eu.cloudnetservice.cloudnet", "driver")
    compileOnly("eu.cloudnetservice.cloudnet", "syncproxy")
    compileOnly("dev.derklaro.aerogel", "aerogel-auto", "2.1.0")

    annotationProcessor("dev.derklaro.aerogel", "aerogel-auto", "2.1.0")
    annotationProcessor("eu.cloudnetservice.cloudnet", "platform-inject-processor", cloudNetVersion)

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
        options.release.set(21)
        options.compilerArgs.add("-AaerogelAutoFileName=autoconfigure/bindings.aero")
    }

    build {
        dependsOn(shadowJar)
    }
}
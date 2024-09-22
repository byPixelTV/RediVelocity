plugins {
    kotlin("jvm") version "2.0.20"
    id("io.github.goooler.shadow") version "8.1.8"
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
    implementation(kotlin("stdlib"))

    // Kotlinx Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

    implementation("redis.clients:jedis:5.2.0-SNAPSHOT")
    implementation("org.yaml:snakeyaml:2.3")
    implementation("org.bstats:bstats-velocity:3.1.0")
    annotationProcessor("com.velocitypowered:velocity-api:3.3.0-SNAPSHOT")

    implementation("dev.jorel:commandapi-velocity-shade:9.6.0-SNAPSHOT")
    implementation("jakarta.inject:jakarta.inject-api:2.0.1")

    compileOnly("com.velocitypowered:velocity-api:3.3.0-SNAPSHOT")

    compileOnly("eu.cloudnetservice.cloudnet:syncproxy:4.0.0-RC10")
    compileOnly("eu.cloudnetservice.cloudnet:bridge:4.0.0-RC10")
    compileOnly("eu.cloudnetservice.cloudnet:driver:4.0.0-RC10")
    compileOnly("eu.cloudnetservice.cloudnet:wrapper-jvm:4.0.0-RC10")
}

sourceSets {
    getByName("main") {
        java {
            srcDir("src/main/java")
        }
        kotlin {
            srcDir("src/main/kotlin")
        }
    }
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
        options.release.set(21)
    }

    named("compileKotlin", org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask::class.java)

    build {
        dependsOn(shadowJar)
    }

    shadowJar {
        relocate("org.bstats", "de.bypixeltv.redivelocity.lib.bstats")
        dependencies {
            exclude(dependency("eu.cloudnetservice.cloudnet:.*"))
        }
    }
}

kotlin {
    jvmToolchain(21)
}
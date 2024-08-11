plugins {
    kotlin("jvm") version "2.0.0"
    id("io.github.goooler.shadow") version "8.1.7"
}

group = "de.bypixeltv"
version = "1.0.2"

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
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0-RC")

    implementation("redis.clients:jedis:5.2.0-beta4")
    implementation("org.yaml:snakeyaml:2.2")
    implementation("org.bstats:bstats-velocity:3.0.2")
    annotationProcessor("com.velocitypowered:velocity-api:3.3.0-SNAPSHOT")

    implementation("dev.jorel:commandapi-velocity-shade:9.6.0-SNAPSHOT")
    implementation("jakarta.inject:jakarta.inject-api:2.0.1.MR")

    compileOnly("com.velocitypowered:velocity-api:3.3.0-SNAPSHOT")

    compileOnly("eu.cloudnetservice.cloudnet:syncproxy:4.0.0-RC10")
    compileOnly("eu.cloudnetservice.cloudnet:bridge:4.0.0-RC10")
    compileOnly("eu.cloudnetservice.cloudnet:driver:4.0.0-RC10")
    compileOnly("eu.cloudnetservice.cloudnet:platform-inject-runtime:4.0.0-RC10")
    compileOnly("eu.cloudnetservice.cloudnet:platform-inject-processor:4.0.0-RC10")
    compileOnly("eu.cloudnetservice.cloudnet:platform-inject-loader:4.0.0-RC10")
    compileOnly("eu.cloudnetservice.cloudnet:platform-inject-api:4.0.0-RC10")
    compileOnly("eu.cloudnetservice.cloudnet:platform-inject-support:4.0.0-RC10")
    compileOnly("eu.cloudnetservice.cloudnet:wrapper-jvm:4.0.0-RC10")
    compileOnly("eu.cloudnetservice.cloudnet:common:4.0.0-RC10")
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
        relocate("org.bstats", "de.bypixeltv.redivelocity.metrics")
        dependencies {
            exclude(dependency("eu.cloudnetservice.cloudnet:.*"))
        }
    }
}

kotlin {
    jvmToolchain(21)
}
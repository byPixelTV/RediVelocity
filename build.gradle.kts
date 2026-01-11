/*
 * Copyright (c) 2024-present byPixelTV & contributors.
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

import org.apache.tools.ant.filters.ReplaceTokens
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.3.0"
    id("com.gradleup.shadow") version "9.3.1"
    id("org.bxteam.quark") version "1.3.0"
    kotlin("plugin.serialization") version "2.3.0"
}

fun getLatestTag(): String {
    try {
        // Fetch all tags
        ProcessBuilder("git", "fetch", "--tags")
            .redirectErrorStream(true)
            .start()
            .apply {
                inputStream.bufferedReader().use { it.readText() }
                waitFor()
            }

        val branch = ProcessBuilder("git", "rev-parse", "--abbrev-ref", "HEAD")
            .redirectErrorStream(true)
            .start()
            .inputStream
            .bufferedReader()
            .use { it.readText().trim() }

        // Try to get latest tag
        val tagProcess = ProcessBuilder("git", "describe", "--tags", "--abbrev=0")
            .redirectErrorStream(true)
            .start()
        val rawTag = tagProcess.inputStream.bufferedReader().use { it.readText().trim() }
        tagProcess.waitFor()

        val hasTag = rawTag.isNotEmpty() && !rawTag.startsWith("fatal:")

        // Always get commit hash (works even if no tag)
        val commitProcess = ProcessBuilder("git", "rev-parse", "--short", "HEAD")
            .redirectErrorStream(true)
            .start()
        val commit = commitProcess.inputStream.bufferedReader().use { it.readText().trim() }
        commitProcess.waitFor()

        // If no commit found (super rare, empty repo)
        if (commit.isEmpty()) return "unknown"

        return if (hasTag) {
            val tag = rawTag.removePrefix("v")
            if (branch == "release") tag else "$tag+$commit"
        } else {
            // no tag → default to 0.0.1 + commit
            "0.0.1+$commit"
        }
    } catch (_: Exception) {
        return "unknown"
    }
}

val versionString = getLatestTag()

group = "dev.bypixel"
version = versionString

repositories {
    maven {
        name = "bypixelRepoReleases"
        url = uri("https://repo.bypixel.dev/releases")
    }
    maven {
        name = "bypixelRepoSnapshots"
        url = uri("https://repo.bypixel.dev/snapshots")
    }

    // maven central releases
    mavenCentral()

    // papermc
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }

    // vulpescloud
    maven("https://repo.vulpescloud.de/snapshots")

    // simplecloud
    maven("https://repo.simplecloud.app/snapshots")
    maven("https://buf.build/gen/maven")
}

dependencies {
    compileOnly("com.velocitypowered:velocity-api:3.4.0-SNAPSHOT")

    implementation("dev.jorel:commandapi-velocity-shade:11.1.0")

    implementation("dev.dejvokep:boosted-yaml:1.3.7")

    quark("org.json:json:20250517")

    quark("com.squareup.okhttp3:okhttp:5.3.2")

    implementation("dev.bypixel:LettuceWrapper:0.4.0")
    quark("io.lettuce:lettuce-core:7.2.1.RELEASE") {
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core")
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-reactive")
    }

    quark("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    quark("org.jetbrains.kotlinx:kotlinx-coroutines-reactive:1.10.2")
    quark("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation(kotlin("stdlib"))

    compileOnly("app.simplecloud.api.platform:velocity:0.0.5-dev.1745077021664-28517d8")

    val vulpesCloudVersion = "3.0.0"
    compileOnly("de.vulpescloud", "bridge", vulpesCloudVersion)
    compileOnly("de.vulpescloud", "api", vulpesCloudVersion)

    val cloudnetVersion = "4.0.0-RC17-SNAPSHOT"
    compileOnly("eu.cloudnetservice.cloudnet", "bridge-api", cloudnetVersion)
    compileOnly("eu.cloudnetservice.cloudnet", "driver-api", cloudnetVersion)
    compileOnly("eu.cloudnetservice.cloudnet", "wrapper-jvm-api", cloudnetVersion)
}

quark {
    platform = "velocity"

    repositories {
        includeProjectRepositories()
    }

    // DO NOT relocate kotlinx or kotlin stdlib, it will throw errors when loading the plugin
    relocate("org.json", "dev.bypixel.redivelocity.libs.json")
    relocate("dev.jorel.commandapi", "dev.bypixel.redivelocity.libs.commandapi")
    relocate("dev.dejvokep.boostedyaml", "dev.bypixel.redivelocity.libs.boostedyaml")
    relocate("com.squareup.okhttp3", "dev.bypixel.redivelocity.libs.okhttp3")
    relocate("io.lettuce", "dev.bypixel.redivelocity.libs.lettuce")
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

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
        options.release.set(21)
    }
    compileKotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    processResources {
        filteringCharset = "UTF-8"

        filesMatching("velocity-plugin.json") {
            filter<ReplaceTokens>("tokens" to mapOf("version" to versionString))
        }
    }

    jar {
        enabled = false
    }

    shadowJar {
        archiveBaseName.set("RediVelocity")
        archiveVersion.set(version.toString())
        archiveClassifier.set("")

        relocate("dev.bypixel.lettucewrapper", "dev.bypixel.redivelocity.libs.lettucewrapper")

        manifest {
            attributes(
                "Implementation-Version" to rootProject.version.toString(),
            )
        }
    }

    build {
        dependsOn(shadowJar)
    }
}
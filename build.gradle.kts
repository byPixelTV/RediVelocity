plugins {
    kotlin("jvm") version "2.0.0"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "de.bypixeltv"
version = "1.0.2"

repositories {
    mavenCentral()
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }

    maven {
        url = uri("https://s01.oss.sonatype.org/content/repositories/snapshots")
    }
}

dependencies {
    implementation(kotlin("stdlib"))

    // Kotlinx Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0-RC")

    compileOnly("com.velocitypowered:velocity-api:3.3.0-SNAPSHOT")
    implementation("redis.clients:jedis:5.2.0-beta4")
    implementation("dev.jorel:commandapi-velocity-shade:9.5.0-SNAPSHOT")
    implementation("org.yaml:snakeyaml:2.2")
    implementation("org.bstats:bstats-velocity:3.0.2")
    annotationProcessor("com.velocitypowered:velocity-api:3.3.0-SNAPSHOT")
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
        options.release.set(17)
    }

    named("compileKotlin", org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask::class.java)

    build {
        dependsOn(shadowJar)
    }

    shadowJar {
        relocate("org.bstats", "de.bypixeltv.redivelocity.metrics")
    }
}

kotlin {
    jvmToolchain(17)
}
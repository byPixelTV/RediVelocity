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

    compileOnly("com.velocitypowered:velocity-api:3.3.0-SNAPSHOT")
    annotationProcessor("com.velocitypowered:velocity-api:3.3.0-SNAPSHOT")

    // Jedis and SnakeYAML
    implementation("redis.clients:jedis:5.2.0")
    implementation("org.yaml:snakeyaml:2.3")

    // CommandAPI and Jakarta Inject
    implementation("dev.jorel:commandapi-velocity-shade:9.6.0-SNAPSHOT")
    implementation("jakarta.inject:jakarta.inject-api:2.0.1")

    // Lombok dependencies
    annotationProcessor("org.projectlombok:lombok:1.18.34")
    compileOnly("org.projectlombok:lombok:1.18.34")

    // CloudNet dependencies
    compileOnly("eu.cloudnetservice.cloudnet:syncproxy:4.0.0-RC10")
    compileOnly("eu.cloudnetservice.cloudnet:bridge:4.0.0-RC10")
    compileOnly("eu.cloudnetservice.cloudnet:driver:4.0.0-RC10")
    compileOnly("eu.cloudnetservice.cloudnet:wrapper-jvm:4.0.0-RC10")
    compileOnly("eu.cloudnetservice.cloudnet:platform-inject-api:4.0.0-RC10")

    // CloudNet and Aerogel annotationProcessors
    annotationProcessor("eu.cloudnetservice.cloudnet:platform-inject-processor:4.0.0-RC10")
    annotationProcessor("dev.derklaro.aerogel", "aerogel-auto", "2.1.0")
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

    compileKotlin {
        compilerOptions {
        }
    }
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-AaerogelAutoFileName=autoconfigure/bindings.aero")
}

kotlin {
    jvmToolchain(21)
}
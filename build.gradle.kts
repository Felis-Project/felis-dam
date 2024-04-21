plugins {
    `java-gradle-plugin`
    kotlin("jvm") version "1.9.23"
    kotlin("plugin.serialization") version "1.9.23"
    `maven-publish`
}

group = "io.github.joemama"
version = "1.6-ALPHA"

repositories {
    maven {
        name = "Felis Repo"
        url = uri("https://repsy.io/mvn/0xjoemama/public/")
    }
    mavenCentral()
    gradlePluginPortal()
}

val asmVersion = "9.6"

dependencies {
    // For remapping
    implementation("io.github.joemama:actually-tiny-remapper:1.0.0-alpha")

    // For transformations
    implementation("org.ow2.asm:asm-commons:$asmVersion")
    implementation("org.ow2.asm:asm-util:$asmVersion")
    implementation("org.ow2.asm:asm-tree:$asmVersion")

    // For manifest parsing
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    implementation("org.vineflower:vineflower:1.10.0")
    implementation("gradle.plugin.org.jetbrains.gradle.plugin.idea-ext:gradle-idea-ext:1.1.8")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.23")
}

gradlePlugin {
    plugins.create("felis-dam") {
        id = "felis-dam"
        implementationClass = "io.github.joemama.loader.make.FelisDamPlugin"
    }
}

publishing {
    repositories {
        maven {
            name = "Repsy"
            url = uri("https://repo.repsy.io/mvn/0xjoemama/public")
            credentials {
                username = System.getenv("REPSY_USERNAME")
                password = System.getenv("REPSY_PASSWORD")
            }
        }
        mavenLocal()
    }
}

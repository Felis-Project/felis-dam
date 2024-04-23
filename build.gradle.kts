plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    `java-gradle-plugin`
    `maven-publish`
}

group = "felis"
version = "1.7.2-alpha"

repositories {
    maven {
        name = "Felis Repo"
        url = uri("https://repsy.io/mvn/0xjoemama/public/")
    }
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    // For remapping
    implementation(libs.atr)
    // For transformations
    implementation(libs.bundles.asm)
    // decompiler
    implementation(libs.vineflower)
    // For manifest parsing
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // plugins to apply
    implementation("gradle.plugin.org.jetbrains.gradle.plugin.idea-ext:gradle-idea-ext:1.1.8")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.versions.kotlin.get()}")
}

gradlePlugin {
    plugins.create("felis-dam") {
        id = "felis-dam"
        implementationClass = "felis.dam.FelisDamPlugin"
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
    }
}

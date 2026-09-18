pluginManagement {
    val detektVersion = providers.gradleProperty("detekt.version").get()
    val kotlinVersion = providers.gradleProperty("kotlin.version").get()
    val loomVersion = providers.gradleProperty("loom.version").get()
    val shadowVersion = providers.gradleProperty("shadow.version").get()

    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net")
    }
    plugins {
        id("dev.detekt") version detektVersion
        id("com.gradleup.shadow") version shadowVersion
        id("net.fabricmc.fabric-loom") version loomVersion
        kotlin("jvm") version kotlinVersion
    }
}

rootProject.name = "Skysoft"

include("detekt-rules")

val supportedMinecraftVersions = providers.gradleProperty("skysoft.supportedMinecraftVersions")
    .get()
    .split(",")
    .map(String::trim)
    .filter(String::isNotEmpty)

supportedMinecraftVersions.forEach { minecraftVersion ->
    val projectName = "mc${minecraftVersion.replace(".", "_")}"
    include(projectName)
    project(":$projectName").projectDir = file("versions/$projectName")
}

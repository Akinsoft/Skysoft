import dev.detekt.gradle.Detekt
import dev.detekt.gradle.extensions.DetektExtension
import net.fabricmc.loom.api.LoomGradleExtensionAPI
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.plugins.quality.CheckstyleExtension
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.language.jvm.tasks.ProcessResources
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    base
    kotlin("jvm") apply false
    id("dev.detekt") apply false
    id("net.fabricmc.fabric-loom") apply false
}

val supportedMinecraftVersions = providers.gradleProperty("skysoft.supportedMinecraftVersions")
    .map { it.split(",").map(String::trim).filter(String::isNotEmpty) }
    .get()
val defaultMinecraftVersion = providers.gradleProperty("skysoft.minecraft").get()
val skysoftVersion = providers.gradleProperty("skysoft.version")
require(defaultMinecraftVersion in supportedMinecraftVersions) {
    "Default Minecraft target $defaultMinecraftVersion is not in supported targets: ${supportedMinecraftVersions.joinToString()}"
}

fun projectNameFor(minecraftVersion: String): String = "mc${minecraftVersion.replace(".", "_")}"

fun targetFor(projectName: String): String =
    supportedMinecraftVersions.single { projectNameFor(it) == projectName }

fun ProviderFactory.targetProperty(name: String, minecraftVersion: String): String =
    gradleProperty("$name.$minecraftVersion")
        .orNull
        ?: error("Missing $name.$minecraftVersion for Minecraft $minecraftVersion")

group = "com.skysoft"
version = skysoftVersion.get()

val javaVersion = 25
val rootLibsDirectory = layout.buildDirectory.dir("libs")
val releaseAssetsDirectory = layout.buildDirectory.dir("release-assets")

fun targetProjectFor(minecraftVersion: String) = project(":${projectNameFor(minecraftVersion)}")

fun skysoftJarName(minecraftVersion: String): String = "Skysoft-$version-mc$minecraftVersion.jar"

val targetProjects = supportedMinecraftVersions.map(::targetProjectFor)

configure(targetProjects) {
    val minecraftVersion = targetFor(name)
    val minecraftDependencyVersion = providers.targetProperty("skysoft.minecraftDependency", minecraftVersion)
    val modrinthProject = providers.gradleProperty("skysoft.modrinthProject").get()
    val detektVersion = providers.gradleProperty("detekt.version").get()
    val checkstyleVersion = providers.gradleProperty("checkstyle.version").get()
    val fabricModJsonMinecraftVersion = "~$minecraftVersion"
    val fabricLoaderVersion = providers.gradleProperty("fabric.loader.version").get()
    val fabricApiVersion = providers.targetProperty("fabric.api.version", minecraftVersion)
    val fabricLanguageKotlinVersion = providers.gradleProperty("fabric.language.kotlin.version").get()
    val modMenuVersion = providers.targetProperty("modmenu.version", minecraftVersion)
    val skyblockerVersion = providers.targetProperty("skyblocker.version", minecraftVersion)
    val moulconfigGroup = providers.gradleProperty("moulconfig.group").get()
    val moulconfigVersion = providers.targetProperty("moulconfig.version", minecraftVersion)
    val hypixelModApiVersion = providers.gradleProperty("hypixel.modapi.version").get()
    val hypixelModApiFabricVersion = providers.targetProperty("hypixel.modapi.fabric.version", minecraftVersion)
    val hypixelModApi = "net.hypixel:mod-api:$hypixelModApiVersion"
    val hypixelModApiFabric = "maven.modrinth:hypixel-mod-api:$hypixelModApiFabricVersion"
    val classTweakerResource = rootProject.layout.projectDirectory.file("src/main/resources/skysoft.official.classtweaker")
    val targetSourceSet = "target${minecraftVersion.replace(".", "_")}"
    val javaSourceDirectories = listOf(
        rootProject.file("src/main/java"),
        rootProject.file("src/$targetSourceSet/java"),
    )
    val kotlinSourceDirectories = listOf(
        rootProject.file("src/main/kotlin"),
        rootProject.file("src/$targetSourceSet/kotlin"),
    )
    val detektSourceDirectories = if (minecraftVersion == defaultMinecraftVersion) {
        kotlinSourceDirectories
    } else {
        listOf(rootProject.file("src/$targetSourceSet/kotlin"))
    }
    group = rootProject.group
    version = rootProject.version
    layout.buildDirectory.set(rootProject.layout.buildDirectory.dir("targets/$name"))

    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "dev.detekt")
    apply(plugin = "net.fabricmc.fabric-loom")
    apply(plugin = "checkstyle")

    repositories {
        mavenCentral()
        maven("https://maven.fabricmc.net")
        maven("https://api.modrinth.com/maven")
        maven("https://repo.hypixel.net/repository/Hypixel/")
    }

    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(javaVersion))
    }

    extensions.configure<SourceSetContainer> {
        named("main") {
            java.setSrcDirs(javaSourceDirectories)
            resources.setSrcDirs(listOf(rootProject.file("src/main/resources")))
        }
    }

    extensions.configure<KotlinJvmProjectExtension> {
        sourceSets.named("main") {
            kotlin.setSrcDirs(kotlinSourceDirectories + javaSourceDirectories)
        }
    }

    extensions.configure<LoomGradleExtensionAPI>("loom") {
        accessWidenerPath.set(classTweakerResource)
        fabricModJsonPath.set(rootProject.layout.projectDirectory.file("src/main/resources/fabric.mod.json"))
    }

    dependencies {
        add("minecraft", "com.mojang:minecraft:$minecraftDependencyVersion")
        add("implementation", "net.fabricmc:fabric-loader:$fabricLoaderVersion")
        add("implementation", "net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")
        add("implementation", "net.fabricmc:fabric-language-kotlin:$fabricLanguageKotlinVersion")
        add("compileOnly", "maven.modrinth:modmenu:$modMenuVersion")
        add("compileOnly", "maven.modrinth:skyblocker-liap:$skyblockerVersion")
        add("implementation", hypixelModApi)
        add("runtimeOnly", hypixelModApiFabric)
        add("detektPlugins", "dev.detekt:detekt-rules-ktlint-wrapper:$detektVersion")
        add("detektPlugins", project(":detekt-rules"))

        val moulconfig = "$moulconfigGroup:modern-$minecraftVersion:$moulconfigVersion"
        add("implementation", moulconfig)
        add("include", moulconfig)
    }

    val resourceProperties = mapOf(
        "version" to project.version,
        "minecraft" to fabricModJsonMinecraftVersion,
        "minecraftRaw" to minecraftVersion,
        "modrinthProject" to modrinthProject,
        "java" to javaVersion,
        "fabricApi" to fabricApiVersion,
        "fabricLanguageKotlin" to fabricLanguageKotlinVersion,
        "fabricLoader" to fabricLoaderVersion,
        "hypixelModApi" to hypixelModApiFabricVersion,
        "moulconfigVersion" to moulconfigVersion,
    )

    tasks.named<ProcessResources>("processResources") {
        inputs.properties(resourceProperties)
        inputs.file(classTweakerResource)
        filesMatching("skysoft.official.classtweaker") {
            path = "skysoft.classtweaker"
        }
        from(rootProject.file("THIRD_PARTY_NOTICES.md")) {
            into("META-INF")
        }
        from(rootProject.file("LICENSE")) {
            into("META-INF")
            rename { "LICENSE-skysoft" }
        }
        from(rootProject.file("LICENSE-GPL-3.0")) {
            into("META-INF")
        }
        from(rootProject.file("LICENSE-LGPL-2.1")) {
            into("META-INF")
        }
        from(rootProject.file("credits.md")) {
            into("META-INF")
            rename { "CREDITS.md" }
        }
        filesMatching("fabric.mod.json") {
            expand(resourceProperties)
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(javaVersion)
    }

    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions.jvmTarget.set(JvmTarget.fromTarget(javaVersion.toString()))
    }

    extensions.configure<CheckstyleExtension>("checkstyle") {
        toolVersion = checkstyleVersion
        configDirectory.set(rootProject.layout.projectDirectory.dir("config/checkstyle"))
    }

    tasks.withType<Detekt>().configureEach {
        jvmTarget = javaVersion.toString()

        reports {
            html.required.set(true)
            sarif.required.set(true)
        }
    }

    extensions.configure<DetektExtension> {
        parallel = true
        buildUponDefaultConfig = true
        config.setFrom(rootProject.layout.projectDirectory.file("detekt/detekt.yml"))
        source.setFrom(files(detektSourceDirectories))
    }

    tasks.withType<AbstractArchiveTask>().configureEach {
        archiveBaseName.set("Skysoft")
        archiveVersion.set("${project.version}-mc$minecraftVersion")
    }

}

val collectVersionJars = tasks.register<Sync>("collectVersionJars") {
    group = "build"
    description = "Collects all supported Minecraft release jars into build/libs."
    into(rootLibsDirectory)
    supportedMinecraftVersions.forEach { minecraftVersion ->
        val targetProject = targetProjectFor(minecraftVersion)
        dependsOn(targetProject.tasks.named("jar"))
        from(targetProject.layout.buildDirectory.dir("libs")) {
            include(skysoftJarName(minecraftVersion))
        }
    }
}

val collectReleaseJars = tasks.register<Sync>("collectReleaseJars") {
    group = "distribution"
    description = "Collects publishable jars for release."
    into(releaseAssetsDirectory)
    supportedMinecraftVersions.forEach { minecraftVersion ->
        val targetProject = targetProjectFor(minecraftVersion)
        dependsOn(targetProject.tasks.named("jar"))
        from(targetProject.layout.buildDirectory.dir("libs")) {
            include(skysoftJarName(minecraftVersion))
        }
    }
}

tasks.named("assemble") {
    dependsOn(collectVersionJars)
}

tasks.named("check") {
    dependsOn(targetProjects.map { it.tasks.named("check") })
    dependsOn(":detekt-rules:check")
}

tasks.named("build") {
    dependsOn(collectVersionJars)
}

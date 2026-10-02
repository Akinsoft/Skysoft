import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import dev.detekt.gradle.Detekt
import dev.detekt.gradle.extensions.DetektExtension
import net.fabricmc.loom.api.LoomGradleExtensionAPI
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.plugins.quality.CheckstyleExtension
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.tasks.Jar
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
    id("com.gradleup.shadow") apply false
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
    val skyblockerVersion = if (minecraftVersion == "26.3") null
        else providers.targetProperty("skyblocker.version", minecraftVersion)
    val moulconfigGroup = providers.gradleProperty("moulconfig.group").get()
    val moulconfigVersion = providers.targetProperty("moulconfig.version", minecraftVersion)
    val hypixelModApiVersion = providers.gradleProperty("hypixel.modapi.version").get()
    val hypixelModApiFabricVersion = providers.targetProperty("hypixel.modapi.fabric.version", minecraftVersion)
    val hypixelModApi = "net.hypixel:mod-api:$hypixelModApiVersion"
    val hypixelModApiFabric = "maven.modrinth:hypixel-mod-api:$hypixelModApiFabricVersion"
    val classTweakerResource = rootProject.layout.projectDirectory.file(
        if (minecraftVersion == "26.3") "src/target26_3/resources/skysoft.official.classtweaker"
        else "src/main/resources/skysoft.official.classtweaker",
    )
    val targetSourceSet = "target${minecraftVersion.replace(".", "_")}"
    val javaSourceDirectories = listOf(
        rootProject.file("src/main/java"),
        rootProject.file("src/$targetSourceSet/java"),
    )
    val kotlinSourceDirectories = listOf(
        rootProject.file("src/main/kotlin"),
        rootProject.file("src/$targetSourceSet/kotlin"),
    )
    val commonJavaRoot = rootProject.file("src/main/java")
    val commonKotlinRoot = rootProject.file("src/main/kotlin")
    val targetJavaRoot = rootProject.file("src/$targetSourceSet/java")
    val targetKotlinRoot = rootProject.file("src/$targetSourceSet/kotlin")
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
        providers.gradleProperty("moulconfig.repository").orNull?.let { maven(rootProject.uri(it)) }
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
            java.exclude { element ->
                element.file.toPath().startsWith(commonJavaRoot.toPath()) &&
                    targetJavaRoot.resolve(element.path).isFile
            }
            resources.setSrcDirs(listOf(rootProject.file("src/main/resources")))
            resources.exclude("skysoft.official.classtweaker")
            if (minecraftVersion == "26.3") resources.exclude("skysoft.mixins.json")
        }
    }

    extensions.configure<KotlinJvmProjectExtension> {
        sourceSets.named("main") {
            kotlin.setSrcDirs(kotlinSourceDirectories + javaSourceDirectories)
            kotlin.exclude { element ->
                (element.file.toPath().startsWith(commonKotlinRoot.toPath()) &&
                    targetKotlinRoot.resolve(element.path).isFile) ||
                    (element.file.toPath().startsWith(commonJavaRoot.toPath()) &&
                    targetJavaRoot.resolve(element.path).isFile)
            }
        }
    }

    extensions.configure<SourceSetContainer> {
        named("test") { resources.setSrcDirs(listOf(rootProject.file("src/$targetSourceSet/test/resources"))) }
    }
    extensions.configure<KotlinJvmProjectExtension> {
        sourceSets.named("test") {
            kotlin.setSrcDirs(listOf(rootProject.file("src/$targetSourceSet/test/kotlin")))
        }
    }
    tasks.withType<Test>().configureEach { useJUnitPlatform() }
    if (minecraftVersion == "26.3") {
        val sourceSets = extensions.getByType<SourceSetContainer>()
        val mixinTestRuntime = configurations.create("mixinTestRuntime") {
            isCanBeConsumed = false
            extendsFrom(configurations.getByName("testRuntimeClasspath"))
        }
        dependencies.add(mixinTestRuntime.name, "net.fabricmc:fabric-loader-junit:$fabricLoaderVersion")
        val mixinTest = tasks.register<Test>("mixinTest") {
            description = "Audits actual native 26.3 mixin application without starting Minecraft."
            group = "verification"
            testClassesDirs = sourceSets.getByName("test").output.classesDirs
            classpath = sourceSets.getByName("test").output + sourceSets.getByName("main").output + mixinTestRuntime
            filter { includeTestsMatching("com.skysoft.test.NativeMixinAuditTest") }
        }
        tasks.named<Test>("test") {
            dependsOn(mixinTest)
            exclude("com/skysoft/test/NativeMixinAuditTest.class")
        }
    }

    extensions.configure<LoomGradleExtensionAPI>("loom") {
        accessWidenerPath.set(classTweakerResource)
        fabricModJsonPath.set(rootProject.layout.projectDirectory.file("src/main/resources/fabric.mod.json"))
    }

    val tinyFileDialogs = configurations.create("tinyFileDialogs") { isTransitive = false }
    val bundledSoftConfig = configurations.create("bundledSoftConfig") {
        isTransitive = false
    }
    val isolatedSoftConfigResources = tasks.register<Sync>("isolateSoftConfigResources") {
        from({ zipTree(bundledSoftConfig.singleFile) }) {
            include("fabric.mod.json")
            filter { line -> line.replace("moulconfig", "skysoft_softconfig") }
        }
        into(layout.buildDirectory.dir("isolated-softconfig/resources"))
    }
    val isolatedSoftConfig = tasks.register<ShadowJar>("isolateSoftConfig") {
        archiveFileName.set("skysoft-softconfig-$moulconfigVersion-mc$minecraftVersion.jar")
        destinationDirectory.set(layout.buildDirectory.dir("isolated-softconfig"))
        from({ zipTree(bundledSoftConfig.singleFile) }) {
            exclude("fabric.mod.json")
        }
        from(isolatedSoftConfigResources)
        relocate("assets.moulconfig", "assets.skysoft_softconfig")
        relocate("moulconfig", "skysoft_softconfig")
    }

    dependencies {
        add("minecraft", "com.mojang:minecraft:$minecraftDependencyVersion")
        add("implementation", "net.fabricmc:fabric-loader:$fabricLoaderVersion")
        add("implementation", "net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")
        add("implementation", "net.fabricmc:fabric-language-kotlin:$fabricLanguageKotlinVersion")
        add("compileOnly", "maven.modrinth:modmenu:$modMenuVersion")
        // 26.3 uses the real optional Skyblocker API through one cached reflective lookup.
        skyblockerVersion?.let { add("compileOnly", "maven.modrinth:skyblocker-liap:$it") }
        add("testImplementation", platform("org.junit:junit-bom:5.10.0"))
        add("testImplementation", "org.junit.jupiter:junit-jupiter")
        add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher")
        if (minecraftVersion == "26.3") {
            add("implementation", "org.lwjgl:lwjgl-tinyfd:3.4.3")
            add(tinyFileDialogs.name, "org.lwjgl:lwjgl-tinyfd:3.4.3")
            listOf("linux", "linux-arm32", "linux-arm64", "linux-ppc64le", "linux-riscv64",
                "macos", "macos-arm64", "windows", "windows-x86", "windows-arm64", "freebsd").forEach {
                add(tinyFileDialogs.name, "org.lwjgl:lwjgl-tinyfd:3.4.3:natives-$it")
            }
        }
        add("implementation", hypixelModApi)
        add("implementation", "org.brotli:dec:0.1.2")
        add("include", "org.brotli:dec:0.1.2")
        add("runtimeOnly", hypixelModApiFabric)
        add("detektPlugins", "dev.detekt:detekt-rules-ktlint-wrapper:$detektVersion")
        add("detektPlugins", project(":detekt-rules"))

        val moulconfig = "$moulconfigGroup:modern-$minecraftVersion:$moulconfigVersion"
        add("implementation", moulconfig)
        add(bundledSoftConfig.name, moulconfig)
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
        if (minecraftVersion == "26.3") {
            from(rootProject.file("src/target26_3/resources/skysoft.mixins.json"))
        }
        from(classTweakerResource) {
            rename { "skysoft.classtweaker" }
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

    tasks.withType<ShadowJar>().configureEach {
        configurations = emptyList()
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
        failOnDuplicateEntries = true
        relocate("io.github.notenoughupdates.moulconfig", "com.skysoft.deps.softconfig")
        mergeServiceFiles()
    }

    tasks.named<Jar>("jar") {
        archiveClassifier.set("unshaded")
        from(isolatedSoftConfig) {
            into("META-INF/jars")
        }
    }

    val releaseJar = tasks.register<ShadowJar>("releaseJar") {
        group = "build"
        description = "Assembles the release jar with isolated SoftConfig references."
        val mainJar = tasks.named<Jar>("jar")
        from(mainJar.map { zipTree(it.archiveFile.get().asFile) })
        if (minecraftVersion == "26.3") {
            from({ tinyFileDialogs.filter { it.name == "lwjgl-tinyfd-3.4.3.jar" }.map { zipTree(it) } }) {
                exclude("META-INF/MANIFEST.MF", "META-INF/INDEX.LIST", "META-INF/*.SF", "META-INF/*.RSA",
                    "META-INF/versions/**/module-info.class")
            }
            from({ tinyFileDialogs.filter { it.name != "lwjgl-tinyfd-3.4.3.jar" }.map { zipTree(it) } }) {
                // The API jar already carries the identical hashes for every supported native.
                exclude("META-INF/MANIFEST.MF", "META-INF/INDEX.LIST", "META-INF/*.SF", "META-INF/*.RSA",
                    "META-INF/versions/**/module-info.class", "META-INF/**/*.sha1")
            }
        }
        archiveClassifier.set("")
    }

    tasks.named("assemble") {
        dependsOn(releaseJar)
    }
}

val collectVersionJars = tasks.register<Sync>("collectVersionJars") {
    group = "build"
    description = "Collects all supported Minecraft release jars into build/libs."
    into(rootLibsDirectory)
    supportedMinecraftVersions.forEach { minecraftVersion ->
        val targetProject = targetProjectFor(minecraftVersion)
        dependsOn(targetProject.tasks.named("releaseJar"))
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
        dependsOn(targetProject.tasks.named("releaseJar"))
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

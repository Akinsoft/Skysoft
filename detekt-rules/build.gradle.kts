import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
}

repositories {
    mavenCentral()
}

val detektVersion = providers.gradleProperty("detekt.version").get()
val javaVersion = 25

dependencies {
    compileOnly("dev.detekt:detekt-api:$detektVersion")
    if (file("src/test").isDirectory) {
        testImplementation(kotlin("test-junit5"))
        testImplementation("dev.detekt:detekt-test:$detektVersion")
    }
}

kotlin {
    jvmToolchain(javaVersion)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(JvmTarget.fromTarget(javaVersion.toString()))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

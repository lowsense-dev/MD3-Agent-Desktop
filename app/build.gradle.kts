import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import javax.xml.parsers.DocumentBuilderFactory

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.ksp)
}

val appVersion = "1.0.0"
version = appVersion

kotlin {
    jvmToolchain(21)
    compilerOptions {
        optIn.addAll(
            "androidx.compose.material3.ExperimentalMaterial3Api",
            "androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
        )
    }
}

// Android-style string resources: the XML stays as on the phone, and R.string.* ids are generated from it.
val generateR by tasks.registering {
    val strings = file("src/main/resources/strings/values/strings.xml")
    val out = layout.buildDirectory.dir("generated/r")
    val version = appVersion
    val drawableDir = file("src/main/resources/drawable")
    inputs.dir(drawableDir)
    inputs.file(strings)
    inputs.property("version", version)
    outputs.dir(out)
    doLast {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(strings)
        fun names(tag: String): List<String> {
            val nodes = doc.getElementsByTagName(tag)
            return (0 until nodes.length).map { nodes.item(it).attributes.getNamedItem("name").nodeValue }
        }
        val drawables = drawableDir.listFiles { f -> f.extension == "xml" }.orEmpty().map { it.nameWithoutExtension }.sorted()
        val text = buildString {
            appendLine("package dev.huecoder.md3agent")
            appendLine()
            appendLine("// Generated from strings.xml by the generateR task. Do not edit.")
            appendLine("object R {")
            appendLine("    object string {")
            names("string").forEachIndexed { i, n -> appendLine("        const val $n: Int = $i") }
            appendLine("    }")
            appendLine("    object plurals {")
            names("plurals").forEachIndexed { i, n -> appendLine("        const val $n: Int = ${100_000 + i}") }
            appendLine("    }")
            appendLine("    object drawable {")
            drawables.forEachIndexed { i, n -> appendLine("        const val $n: Int = ${200_000 + i}") }
            appendLine("    }")
            appendLine("    val drawableNames: Map<Int, String> = mapOf(${drawables.mapIndexed { i, n -> "${200_000 + i} to \"$n\"" }.joinToString()})")
            appendLine("    val stringNames: List<String> = listOf(${names("string").joinToString { "\"$it\"" }})")
            appendLine("    val pluralNames: List<String> = listOf(${names("plurals").joinToString { "\"$it\"" }})")
            appendLine("}")
        }
        val dir = out.get().asFile.resolve("dev/huecoder/md3agent").apply { mkdirs() }
        dir.resolve("R.kt").writeText(text)
        dir.resolve("BuildConfig.kt").writeText(
            "package dev.huecoder.md3agent\n\nobject BuildConfig {\n    const val VERSION_NAME: String = \"$version\"\n}\n",
        )
    }
}

sourceSets.main { kotlin.srcDir(generateR) }

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(libs.compose.material3)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.navigation.compose)
    implementation(libs.material.symbols.rounded)
    implementation(libs.material.symbols.rounded.filled)
    implementation(libs.graphics.shapes)
    implementation(libs.room.runtime)
    implementation(libs.sqlite.bundled)
    ksp(libs.room.compiler)
    implementation(libs.datastore.preferences.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.okhttp)
    implementation(libs.okhttp.dnsoverhttps)
    implementation(libs.markdown.m3)
    implementation(libs.markdown.code)
    implementation(libs.markdown.coil3)
    implementation(libs.jlatexmath)
    implementation(libs.jlatexmath.cyrillic)
    implementation(libs.material.kolor)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.dyn4j)
    implementation(libs.dbus.java.core)
    implementation(libs.dbus.java.unixsocket)

    testImplementation(libs.junit)
    testImplementation(kotlin("test-junit"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}

tasks.test {
    systemProperty("java.awt.headless", "true")
    systemProperty("screens.dir", layout.buildDirectory.dir("screens").get().asFile.path)
}

compose.desktop {
    application {
        mainClass = "dev.huecoder.md3agent.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Deb, TargetFormat.Rpm)
            packageName = "md3-agent"
            packageVersion = appVersion
        }
    }
}

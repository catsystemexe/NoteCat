import java.net.URI
import java.security.MessageDigest
import java.util.Properties
import java.util.zip.ZipInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Výchozí hodnoty pro vývoj lze přepsat v local.properties (notecat.backendUrl, notecat.backendToken).
// Skutečná konfigurace se za běhu ukládá v šifrovaných preferencích – tohle je jen dev default.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "cz.notecat.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "cz.notecat.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField(
            "String",
            "DEFAULT_BACKEND_URL",
            "\"${localProps.getProperty("notecat.backendUrl", "")}\""
        )
        buildConfigField(
            "String",
            "DEFAULT_BACKEND_TOKEN",
            "\"${localProps.getProperty("notecat.backendToken", "")}\""
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.security.crypto)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.vosk.android)
    implementation(variantOf(libs.jna) { artifactType("aar") })

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

// ---------------------------------------------------------------------------
// Stažení českého Vosk modelu do assets (necommituje se do gitu, ~46 MB).
// Model slouží výhradně k offline rozpoznání ukončovacího povelu „konec".
// ---------------------------------------------------------------------------
val voskModelName = "vosk-model-small-cs-0.4-rhasspy"
val voskModelUrl = "https://alphacephei.com/vosk/models/$voskModelName.zip"
val voskAssetsDir = layout.projectDirectory.dir("src/main/assets/model-cs")

val downloadVoskModel by tasks.registering {
    outputs.dir(voskAssetsDir)
    onlyIf { !voskAssetsDir.asFile.resolve("README").exists() && !voskAssetsDir.asFile.resolve("am").exists() }
    doLast {
        val target = voskAssetsDir.asFile
        target.mkdirs()
        val zipFile = layout.buildDirectory.file("tmp/$voskModelName.zip").get().asFile
        zipFile.parentFile.mkdirs()
        if (!zipFile.exists()) {
            logger.lifecycle("Stahuji Vosk model ($voskModelUrl)…")
            URI(voskModelUrl).toURL().openStream().use { input ->
                zipFile.outputStream().use { input.copyTo(it) }
            }
        }
        logger.lifecycle("Rozbaluji Vosk model do assets…")
        ZipInputStream(zipFile.inputStream().buffered()).use { zin ->
            var entry = zin.nextEntry
            while (entry != null) {
                // Odstranit kořenový adresář "vosk-model-small-cs-0.4-rhasspy/"
                val rel = entry.name.substringAfter('/')
                if (rel.isNotEmpty()) {
                    val out = target.resolve(rel)
                    if (entry.isDirectory) out.mkdirs() else {
                        out.parentFile.mkdirs()
                        out.outputStream().use { zin.copyTo(it) }
                    }
                }
                zin.closeEntry()
                entry = zin.nextEntry
            }
        }
        // Marker pro Vosk StorageService (očekává uuid soubor pro synchronizaci verze modelu)
        val uuid = MessageDigest.getInstance("MD5").digest(voskModelName.toByteArray())
            .joinToString("") { "%02x".format(it) }
        target.resolve("uuid").writeText(uuid)
    }
}

tasks.named("preBuild") {
    dependsOn(downloadVoskModel)
}

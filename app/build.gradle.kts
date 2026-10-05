import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

android {
    namespace = "com.ricordella.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.remindella.app"
        minSdk = 28
        targetSdk = 37
        versionCode = 8
        versionName = "0.5.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Stessa chiave di debug su tutti i computer (è nel progetto): installando da Android Studio su un
    // altro PC l'app si aggiorna invece di chiedere di disinstallarla. Non usarla per la versione sullo Store.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    // Firma di release: legge keystore.properties nella radice del progetto (fuori da git).
    // Senza quel file la build di release resta non firmata e il resto del progetto compila lo stesso.
    val keystoreProps = rootProject.file("keystore.properties")
    if (keystoreProps.exists()) {
        val props = Properties().apply { keystoreProps.inputStream().use(::load) }
        signingConfigs {
            create("release") {
                storeFile = rootProject.file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        // La build di debug si installa accanto a quella dello Store (altro pacchetto, altri dati).
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            signingConfigs.findByName("release")?.let { signingConfig = it }
            isMinifyEnabled = true
            isShrinkResources = true
            // Simboli delle librerie native dentro l'AAB: Play Console li usa per leggere i crash.
            ndk.debugSymbolLevel = "FULL"
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Solo le lingue dell'app: le traduzioni delle librerie nelle altre lingue sarebbero peso inutile.
    androidResources {
        localeFilters += listOf("it", "en", "de", "fr", "es")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets {
        // Gli schemi esportati da Room servono ai test di migrazione.
        getByName("androidTest").assets.directories.add("$projectDir/schemas")
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.navigation.suite)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    // Riconoscimento del testo degli scontrini e lettura dei codici dalle foto. Versioni "Play Services":
    // il modello lo fornisce il sistema (scaricato all'installazione, vedi il manifest), non pesa sull'app.
    implementation(libs.mlkit.text.recognition)

    // Carte d'imbarco: scansione con la fotocamera (senza permesso) e lettura da foto/PDF.
    implementation(libs.play.services.code.scanner)
    implementation(libs.mlkit.barcode.scanning)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}

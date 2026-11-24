import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    // Neutral placeholder namespace: no personal data, no domain claimed.
    // Replace it with your own reverse-domain id if you publish to a store.
    namespace = "org.example.chesshost"
    compileSdk = 37

    defaultConfig {
        applicationId = "org.example.chesshost"
        minSdk = 26
        targetSdk = 37
        versionCode = 2
        versionName = "1.0.0-beta2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Nur die ABIs, für die es native Engines gibt. 32-Bit-Geräte hätten
        // ohnehin keine Engine zur Auswahl, würden aber ONNX Runtime (~25-40 MB je ABI)
        // mitinstallieren.
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    // Signing: uses keystore.properties in the project root when present (never
    // commit it), otherwise falls back to the debug key so a fresh clone can still
    // build. Distributable builds need their own keystore.
    val keystorePropsFile = rootProject.file("keystore.properties")
    signingConfigs {
        if (keystorePropsFile.exists()) {
            create("release") {
                val props = Properties().apply {
                    keystorePropsFile.inputStream().use { load(it) }
                }
                storeFile = rootProject.file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = if (keystorePropsFile.exists()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    // Stockfish wird als natives UCI-Executable in jniLibs/<abi>/libstockfish.so
    // ausgeliefert und beim Start aus nativeLibraryDir als Prozess gestartet.
    packaging {
        jniLibs {
            useLegacyPackaging = true
            keepDebugSymbols += "**/libstockfish.so"
        }
    }

    testOptions {
        unitTests.all { test ->
            // Pfad zu einem Maia-Netz für den echten Modelltest:
            // ./gradlew testDebugUnitTest -PmaiaModel=/pfad/maia3-5m.fp16.onnx
            val model = (project.findProperty("maiaModel") as String?).orEmpty()
            test.systemProperty("maia.model", model)
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.kotlinx.coroutines.android)

    // Neuronale Engines: Das Netz kommt als Download, die Laufzeit steckt hier.
    implementation(libs.onnxruntime.android)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    // org.json ist auf der JVM nicht vorhanden; der Katalog-Parser braucht es im Test.
    testImplementation(libs.json)
    // Damit der Maia-Test das echte Netz auch ohne Gerät auswerten kann.
    testImplementation(libs.onnxruntime.jvm)
}

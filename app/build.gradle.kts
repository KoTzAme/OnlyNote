plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.onlynote.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.onlynote.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 49
        versionName = "1.2"
    }

    signingConfigs {
        create("local") {
            // Ключ в репозиторий не кладём: если его нет, релиз соберётся
            // неподписанным, а debug подпишется отладочным ключом системы.
            val store = rootProject.file("keystore/debug.keystore")
            if (store.exists()) {
                storeFile = store
                storePassword = System.getenv("ONLYNOTE_STORE_PASSWORD") ?: "android"
                keyAlias = System.getenv("ONLYNOTE_KEY_ALIAS") ?: "onlynote"
                keyPassword = System.getenv("ONLYNOTE_KEY_PASSWORD") ?: "android"
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("local")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("local")
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

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    defaultConfig {
        // Приложение только на русском: переводы AndroidX на девяносто языков
        // лежат в resources.arsc и там же остаются, если их не отсечь.
        resourceConfigurations += listOf("ru")
        // Только 64 бита: 32-битные устройства больше не поддерживаем — это
        // отдельная копия libandroidx.graphics.path.so в APK.
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "DebugProbesKt.bin",
            "kotlin-tooling-metadata.json",
            "/META-INF/*.version",
            "/META-INF/*.kotlin_module",
            // Метаданные Kotlin нужны только kotlin-reflect, которого в APK нет.
            "kotlin/**",
            // Профиль депт-оптимизации нужен системе, а не приложению: с ним
            // запуск чуть быстрее, но лежит он в assets и весит 6 КБ.
            "assets/dexopt/baseline.prof",
            "assets/dexopt/baseline.profm",
        )
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    // ui-tooling и ui-tooling-preview не нужны: @Preview в проекте нет,
    // а debug-сборка с ними весит на восемь мегабайт больше.

    testImplementation("junit:junit:4.13.2")
    // Настоящий org.json для JVM-тестов: в android.jar методы-заглушки.
    testImplementation("org.json:json:20240303")
}

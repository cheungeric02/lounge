import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// APP_VERSION in PatchNotes.kt is the single version source.
val appVersion: Int = run {
    val src = file("src/main/java/ca/cheungeric/lounge/ui/patchnotes/PatchNotes.kt").readText()
    Regex("""const\s+val\s+APP_VERSION\s*=\s*(\d+)""").find(src)?.groupValues?.get(1)?.toInt()
        ?: error("APP_VERSION not found in PatchNotes.kt")
}

// Release signing: ~/.lounge-keys/keystore.properties (local) or LOUNGE_* env vars (CI); else unsigned.
data class ReleaseKey(val storeFile: String, val storePassword: String, val keyAlias: String, val keyPassword: String)

val releaseKey: ReleaseKey? = run {
    val propsFile = File(System.getProperty("user.home"), ".lounge-keys/keystore.properties")
    if (propsFile.exists()) {
        val p = Properties().apply { propsFile.inputStream().use { load(it) } }
        val store = p.getProperty("storeFile")
        val storePath = if (store != null && !File(store).isAbsolute) File(propsFile.parentFile, store).path else store
        if (storePath != null) ReleaseKey(
            storePath,
            p.getProperty("storePassword"),
            p.getProperty("keyAlias"),
            p.getProperty("keyPassword"),
        ) else null
    } else {
        val path = System.getenv("LOUNGE_KEYSTORE_PATH")
        val storePw = System.getenv("LOUNGE_KEYSTORE_PASSWORD")
        val alias = System.getenv("LOUNGE_KEY_ALIAS")
        val keyPw = System.getenv("LOUNGE_KEY_PASSWORD")
        if (!path.isNullOrBlank() && !storePw.isNullOrBlank() && !alias.isNullOrBlank() && !keyPw.isNullOrBlank()) {
            ReleaseKey(path, storePw, alias, keyPw)
        } else null
    }
}

android {
    namespace = "ca.cheungeric.lounge"
    compileSdk = 35

    defaultConfig {
        applicationId = "ca.cheungeric.lounge"
        minSdk = 28
        targetSdk = 34
        versionCode = appVersion
        versionName = "v$appVersion"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseKey != null) {
            create("release") {
                storeFile = file(releaseKey.storeFile)
                storePassword = releaseKey.storePassword
                keyAlias = releaseKey.keyAlias
                keyPassword = releaseKey.keyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (releaseKey != null) {
                signingConfig = signingConfigs.getByName("release")
            }
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
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.tv.material)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
}

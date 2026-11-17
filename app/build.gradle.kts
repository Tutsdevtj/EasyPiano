import java.util.Properties
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}
val signing = Properties().apply {
    val props = rootProject.file("keystore.properties")
    if (props.exists()) props.inputStream().use { load(it) }
}
fun secret(env: String, prop: String) = System.getenv(env)?.takeIf { it.isNotBlank() } ?: signing.getProperty(prop)
val store = secret("PIANO_KEYSTORE_PATH", "storeFile")
val storePass = secret("PIANO_KEYSTORE_PASSWORD", "storePassword")
val alias = secret("PIANO_KEY_ALIAS", "keyAlias")
val keyPass = secret("PIANO_KEY_PASSWORD", "keyPassword")
val provided = listOf(store, storePass, alias, keyPass).count { !it.isNullOrBlank() }
check(provided == 0 || provided == 4) { "Assinatura incompleta: configure as quatro propriedades." }
val releaseTag = providers.environmentVariable("VERSION_TAG").orElse(providers.environmentVariable("GITHUB_REF_NAME")).orElse("v0.1.0").get()
val versionParts = Regex("^v?(\\d+)\\.(\\d+)\\.(\\d+)$").matchEntire(releaseTag)?.groupValues?.drop(1)?.map { it.toInt() } ?: listOf(0, 1, 0)
check(versionParts[0] <= 2000 && versionParts[1] < 1000 && versionParts[2] < 1000)
android {
    namespace = "com.arthur.easypiano"
    compileSdk = 37
    compileSdkMinor = 2
    buildToolsVersion = "37.0.0"
    ndkVersion = "27.2.12479018"
    defaultConfig {
        applicationId = "com.arthur.easypiano"
        minSdk = 26
        targetSdk = 37
        versionCode = versionParts[0] * 1000000 + versionParts[1] * 1000 + versionParts[2]
        versionName = versionParts.joinToString(".")
        ndk { abiFilters += if (providers.gradleProperty("emulator").isPresent) listOf("x86_64") else listOf("arm64-v8a", "armeabi-v7a") }
        externalNativeBuild { cmake { arguments += "-DANDROID_STL=c++_shared"; cppFlags += listOf("-std=c++17", "-O3", "-Wall", "-Wextra") } }
    }
    signingConfigs {
        if (provided == 4) create("production") {
            storeFile = rootProject.file(store!!)
            storePassword = storePass
            keyAlias = alias
            keyPassword = keyPass
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = if (provided == 4) signingConfigs.getByName("production") else signingConfigs.getByName("debug")
            if (provided == 0) logger.warn("AVISO: release assinado com chave DEBUG; configure uma chave permanente antes de distribuir atualizações.")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    buildFeatures { compose = true; prefab = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" } }
    androidResources { noCompress += "ogg" }
    packaging { jniLibs { useLegacyPackaging = false } }
}
dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime)
    implementation(libs.datastore)
    implementation(libs.oboe)
    testImplementation(libs.junit)
}

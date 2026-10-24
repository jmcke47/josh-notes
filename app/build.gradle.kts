import java.util.Properties

plugins {
    id("com.android.application")
}

fun loadProps(name: String): Properties = Properties().apply {
    val f = rootProject.file(name)
    if (f.exists()) f.inputStream().use { load(it) }
}

val versionProps = loadProps("version.properties")
val updateProps = loadProps("update.properties")
val keystoreProps = loadProps("keystore/keystore.properties")

android {
    namespace = "com.josh.notes"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.josh.notes"
        minSdk = 26
        targetSdk = 37
        versionCode = versionProps.getProperty("VERSION_CODE", "1").toInt()
        versionName = versionProps.getProperty("VERSION_NAME", "1.0.0")
        // Vosk native libs: real phones only (keeps APK small).
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
        buildConfigField(
            "String", "UPDATE_MANIFEST_URL",
            "\"" + (project.findProperty("updateManifestUrl") as String?
                ?: updateProps.getProperty("UPDATE_MANIFEST_URL", "")) + "\""
        )
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file("keystore/" + keystoreProps.getProperty("storeFile", "release.jks"))
            storePassword = keystoreProps.getProperty("storePassword")
            keyAlias = keystoreProps.getProperty("keyAlias")
            keyPassword = keystoreProps.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            // Same key for debug too, so any build can update any other.
            signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures { buildConfig = true }
    // Compress native libs (Vosk) inside the APK: much smaller download.
    packaging { jniLibs { useLegacyPackaging = true } }
    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            // ./gradlew testDebugUnitTest -PscreensDir=/path renders Robolectric screenshots there.
            it.systemProperty("screens.dir", (project.findProperty("screensDir") as String?) ?: "")
            it.maxHeapSize = "3g"
            it.jvmArgs("--add-opens=java.base/java.io=ALL-UNNAMED", "--add-opens=java.base/java.lang=ALL-UNNAMED", "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED", "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("androidx.activity:activity-ktx:1.13.0")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("com.google.android.material:material:1.14.0")
    implementation("com.alphacephei:vosk-android:0.3.75")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.work:work-runtime-ktx:2.12.0")
    implementation("androidx.documentfile:documentfile:1.1.0")
    implementation("androidx.exifinterface:exifinterface:1.4.2")
    implementation("com.google.android.gms:play-services-auth:22.0.0")
    implementation("com.squareup.okhttp3:okhttp:5.5.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.17")
    testImplementation("androidx.test:core:1.7.0")
    testImplementation("org.json:json:20250517")
    implementation("net.java.dev.jna:jna:5.18.1@aar")
}

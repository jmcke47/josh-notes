// Old School theme kit — Android library module. Vendored into each app by tools/sync-into.sh (see README).
plugins {
    id("com.android.library")
}

val kitVersion = file("VERSION").readText().trim()

android {
    namespace = "com.josh.oldschool"
    compileSdk = 37
    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
        buildConfigField("String", "KIT_VERSION", "\"$kitVersion\"")
    }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("com.google.android.material:material:1.14.0")
}

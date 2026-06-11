plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "org.astermail.android.api"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        buildConfigField("String", "API_BASE_URL", "\"https://app.astermail.org\"")
        buildConfigField("String", "VERSION_NAME", "\"0.1.0\"")
    }

    buildTypes {
        getByName("debug") {
            val localApi = providers.gradleProperty("astermail.localApi").orNull == "true"
            val debugUrl = if (localApi) "http://10.0.2.2:3000" else "https://app.astermail.org"
            buildConfigField("String", "API_BASE_URL", "\"$debugUrl\"")
        }
        getByName("release") {
            buildConfigField("String", "API_BASE_URL", "\"https://app.astermail.org\"")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    api(libs.ktor.client.auth)
    api(libs.ktor.client.core)
    implementation(libs.ktor.client.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
}

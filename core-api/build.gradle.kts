plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

android {
    namespace = "org.astermail.android.api"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        buildConfigField("String", "API_BASE_URL", "\"https://app.astermail.org\"")
        buildConfigField("String", "VERSION_NAME", "\"0.1.0\"")
        buildConfigField("String", "WEBAUTHN_ORIGIN", "\"https://app.astermail.org\"")
    }

    buildTypes {
        getByName("debug") {
            val localApi = providers.gradleProperty("astermail.localApi").orNull == "true"
            val localPort = providers.gradleProperty("astermail.localApiPort").orNull ?: "3000"
            val debugUrl = if (localApi) "http://10.0.2.2:$localPort" else "https://app.astermail.org"
            val webauthnOrigin = providers.gradleProperty("astermail.webauthnOrigin").orNull
                ?: "https://app.astermail.org"
            buildConfigField("String", "API_BASE_URL", "\"$debugUrl\"")
            buildConfigField("String", "WEBAUTHN_ORIGIN", "\"$webauthnOrigin\"")
        }
        getByName("release") {
            buildConfigField("String", "API_BASE_URL", "\"https://app.astermail.org\"")
            buildConfigField("String", "WEBAUTHN_ORIGIN", "\"https://app.astermail.org\"")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
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
    testImplementation(libs.junit)
}

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

val app_version_name: String = Regex("""versionName\s*=\s*"([^"]+)"""")
    .find(providers.fileContents(rootProject.layout.projectDirectory.file("app/build.gradle.kts")).asText.get())
    ?.groupValues
    ?.get(1)
    ?: error("versionName not found in app/build.gradle.kts")

android {
    namespace = "org.astermail.android.api"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        buildConfigField("String", "API_BASE_URL", "\"https://app.astermail.org\"")
        buildConfigField("String", "VERSION_NAME", "\"$app_version_name\"")
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

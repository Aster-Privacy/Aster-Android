plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.androidx.baselineprofile)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

android {
    namespace = "org.astermail.android.baselineprofile"
    compileSdk = 36

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        minSdk = 28
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        missingDimensionStrategy("distribution", "full")
    }

    targetProjectPath = ":app"

    testOptions {
        managedDevices {
            allDevices {
                create<com.android.build.api.dsl.ManagedVirtualDevice>("aster_gen_api34") {
                    device = "Pixel 6"
                    apiLevel = 34
                    systemImageSource = "google"
                }
            }
        }
    }
}

baselineProfile {
    managedDevices += "aster_gen_api34"
    useConnectedDevices = false
}

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.androidx.espresso.core)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}

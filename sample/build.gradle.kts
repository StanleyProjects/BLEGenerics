import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import sp.kx.gradlex.camelCase

repositories {
    google()
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    maven("https://central.sonatype.com/repository/maven-snapshots/")
}

plugins {
    id("com.android.application")
    id("kotlin-android")
    id("org.jetbrains.compose") version "1.7.3"
}

android {
    namespace = "sp.sample.blegenerics"
    compileSdk = Version.Android.compileSdk

    defaultConfig {
        applicationId = namespace
        minSdk = Version.Android.minSdk
        targetSdk = Version.Android.targetSdk
        versionCode = 1
        versionName = "0.0.$versionCode"
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".$name"
            versionNameSuffix = "-$name"
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    composeOptions.kotlinCompilerExtensionVersion = "1.5.15"
}

androidComponents.onVariants { variant ->
    val output = variant.outputs.single()
    check(output is com.android.build.api.variant.impl.VariantOutputImpl)
    output.outputFileName = listOf(
        rootProject.name,
        android.defaultConfig.versionName!!,
        variant.name,
        android.defaultConfig.versionCode!!.toString(),
    ).joinToString(separator = "-", postfix = ".apk")
    afterEvaluate {
        tasks.getByName<JavaCompile>(camelCase("compile", variant.name, "JavaWithJavac")) {
            targetCompatibility = Version.jvmTarget
        }
        tasks.getByName<KotlinCompile>(camelCase("compile", variant.name, "Kotlin")) {
            kotlinOptions.jvmTarget = Version.jvmTarget
        }
    }
}

dependencies {
    implementation(project(":lib"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation(compose.foundation)
    implementation("com.github.kepocnhh:BLEScanner:0.3.1u-SNAPSHOT")
    implementation("com.github.kepocnhh:Clicks:0.1.0u-SNAPSHOT")
}

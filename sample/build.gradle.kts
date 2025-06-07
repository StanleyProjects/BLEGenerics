import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

repositories {
    google()
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    maven("https://s01.oss.sonatype.org/content/repositories/snapshots")
}

plugins {
    id("com.android.application")
    id("kotlin-android")
    id("org.jetbrains.compose") version Version.compose
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
        compose = true
        buildConfig = true
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
        tasks.getByName<JavaCompile>("compile${variant.name.replaceFirstChar(Char::titlecase)}JavaWithJavac") {
            targetCompatibility = Version.jvmTarget
        }
        tasks.getByName<KotlinCompile>("compile${variant.name.replaceFirstChar(Char::titlecase)}Kotlin") {
            kotlinOptions.jvmTarget = Version.jvmTarget
        }
    }
}

dependencies {
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation(compose.foundation)
    implementation("com.github.kepocnhh:BLEScanner:0.1.3u-SNAPSHOT")
}

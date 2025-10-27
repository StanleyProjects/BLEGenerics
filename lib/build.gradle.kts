import com.android.build.gradle.api.BaseVariant
import com.android.build.gradle.api.LibraryVariant
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import sp.kx.gradlex.GitHub
import sp.kx.gradlex.Markdown
import sp.kx.gradlex.Maven
import sp.kx.gradlex.add
import sp.kx.gradlex.asFile
import sp.kx.gradlex.assemble
import sp.kx.gradlex.buildDir
import sp.kx.gradlex.camelCase
import sp.kx.gradlex.check
import sp.kx.gradlex.create
import sp.kx.gradlex.eff

version = "0.3.6"

val maven = Maven.Artifact(
    group = "com.github.kepocnhh",
    id = rootProject.name,
)

val gh = GitHub.Repository(
    owner = "StanleyProjects",
    name = rootProject.name,
)

repositories {
    google()
    mavenCentral()
}

plugins {
    id("com.android.library")
    id("kotlin-android")
}

fun BaseVariant.getVersion(): String {
    return when (flavorName) {
        "unstable" -> {
            when (buildType.name) {
                "debug" -> "${version}u-SNAPSHOT"
                else -> error("Build type \"${buildType.name}\" is not supported for flavor \"$flavorName\"!")
            }
        }
        else -> error("Flavor name \"$flavorName\" is not supported!")
    }
}

fun checkReadme(variant: BaseVariant) {
    tasks.create("check", variant.name, "Readme") {
        doLast {
            when (variant.name) {
                "unstableDebug" -> {
                    val version = variant.getVersion()
                    val expected = setOf(
                        "GitHub ${Markdown.link(text = version, uri = gh.release(version = version))}",
                        "Maven ${Markdown.link("metadata", Maven.Snapshot.metadata(artifact = maven))}",
                        "maven(\"${Maven.Snapshot.Host}\")",
                        "implementation(\"${maven.moduleName(version = version)}\")",
                        "gradle lib:assemble${variant.name.replaceFirstChar(Char::titlecase)}",
                    )
                    rootDir.resolve("README.md").check(
                        expected = expected,
                        report = buildDir()
                            .dir("reports/analysis/readme")
                            .dir(variant.name)
                            .asFile("index.html"),
                    )
                }
                else -> error("Variant \"${variant.name}\" is not supported!")
            }
        }
    }
}

fun assemblePom(variant: BaseVariant) {
    tasks.create("assemble", variant.name, "Pom") {
        doLast {
            val version = variant.getVersion()
            val target = buildDir()
                .dir("xml")
                .dir(variant.name)
                .file("${maven.name(version = version)}.pom")
            val text = maven.pom(version = version, packaging = "aar")
            val file = target.assemble(text = text)
            println("POM: ${file.absolutePath}")
        }
    }
}

fun assembleSource(variant: BaseVariant) {
    tasks.add<Jar>("assemble", variant.name, "Source") {
        val sourceSets = variant.sourceSets.flatMap { it.kotlinDirectories }.distinctBy { it.absolutePath }
        from(sourceSets)
        val file = buildDir()
            .dir("sources")
            .dir(variant.name)
            .asFile("${maven.name(variant.getVersion())}-sources.jar")
        outputs.upToDateWhen {
            file.exists()
        }
        doLast {
            file.parentFile!!.mkdirs()
            val renamed = archiveFile.get().asFile.eff().renameTo(file)
            check(renamed)
            println("Archive: ${file.absolutePath}")
        }
    }
}

fun assembleMetadata(variant: BaseVariant) {
    tasks.create("assemble", variant.name, "Metadata") {
        doLast {
            val target = buildDir().dir("yml").file("metadata.yml")
            val file = gh.assemble(version = variant.getVersion(), target = target)
            println("Metadata: ${file.absolutePath}")
        }
    }
}

fun assembleMavenMetadata(variant: BaseVariant) {
    tasks.create("assemble", variant.name, "MavenMetadata") {
        doLast {
            val target = buildDir().dir("yml").file("maven-metadata.yml")
            val file = maven.assemble(version = variant.getVersion(), target = target)
            println("Maven metadata: ${file.absolutePath}")
        }
    }
}

android {
    namespace = "sp.ax.blegenerics"
    compileSdk = Version.Android.compileSdk

    defaultConfig {
        minSdk = Version.Android.minSdk
    }

    productFlavors {
        mapOf("stability" to setOf("unstable")).forEach { (dimension, flavors) ->
            flavorDimensions += dimension
            flavors.forEach { flavor ->
                create(flavor) {
                    this.dimension = dimension
                }
            }
        }
    }

    fun onVariant(variant: LibraryVariant) {
        val supported = setOf("unstableDebug")
        if (!supported.contains(variant.name)) {
            tasks.getByName(camelCase("pre", variant.name, "Build")) {
                doFirst {
                    error("Variant \"${variant.name}\" is not supported!")
                }
            }
            return
        }
        val output = variant.outputs.single()
        check(output is com.android.build.gradle.internal.api.LibraryVariantOutputImpl)
        output.outputFileName = "${rootProject.name}-${variant.getVersion()}.aar"
        checkReadme(variant = variant)
        assemblePom(variant = variant)
        assembleSource(variant = variant)
        assembleMetadata(variant = variant)
        assembleMavenMetadata(variant = variant)
        afterEvaluate {
            tasks.getByName<JavaCompile>(camelCase("compile", variant.name, "JavaWithJavac")) {
                targetCompatibility = Version.jvmTarget
            }
            tasks.getByName<KotlinCompile>(camelCase("compile", variant.name, "Kotlin")) {
                kotlinOptions {
                    jvmTarget = Version.jvmTarget
                    freeCompilerArgs = freeCompilerArgs + setOf("-module-name", maven.moduleName(separator = '-'))
                }
            }
        }
    }

    libraryVariants.all {
        onVariant(variant = this)
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
    testImplementation("org.robolectric:robolectric:4.13")
}

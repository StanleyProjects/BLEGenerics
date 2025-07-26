repositories {
    mavenCentral()
    maven("https://central.sonatype.com/repository/maven-snapshots/")
}

plugins {
    `kotlin-dsl`
}

dependencies {
    implementation("com.github.kepocnhh:GradleExtension.Core:0.6.1-SNAPSHOT")
}

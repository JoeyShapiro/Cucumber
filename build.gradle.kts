plugins {
    kotlin("multiplatform") version "1.8.0"
}

group = "me.oniichan"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

kotlin {
    val hostOs = System.getProperty("os.name")
    val isMingwX64 = hostOs.startsWith("Windows")
    val nativeTarget = when {
        hostOs == "Mac OS X" -> macosX64("native")
        hostOs == "Linux" -> linuxX64("native")
        isMingwX64 -> mingwX64("native")
        else -> throw GradleException("Host OS is not supported in Kotlin/Native.")
    }

    linuxX64("linux") { // Define your target instead.
        binaries {
            executable("cucumber") {
                // Binary configuration.
                entryPoint = "main"
            }
        }
    }
    macosArm64("mac") {
        binaries {
            executable("cucumber") {
                entryPoint = "main"
            }
        }
    }

    sourceSets {
        val nativeMain by getting
        val nativeTest by getting
        val macMain by getting {
            dependsOn(nativeMain)
        }
        val linuxMain by getting {
            dependsOn(nativeMain)
        }
    }
}

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

System.setProperty("android.aapt2FromMavenOverride", "/tmp/aapt2-universal-wrapper.sh")

android {
    namespace = "com.badini.translate.webapp"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.badini.translate.webapp"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        create("release") {
            storeFile = file("release.keystore")
            storePassword = "badini123"
            keyAlias = "badini"
            keyPassword = "badini123"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.webkit:webkit:1.8.0")
}

// Post-processing to ensure APK is properly signed with v2/v3 schemes
tasks.whenTaskAdded {
    if (name == "packageRelease") {
        finalizedBy("signReleaseWithApksigner")
    }
}

tasks.register("signReleaseWithApksigner") {
    doLast {
        val apkFile = file("build/outputs/apk/release/app-release.apk")
        if (apkFile.exists()) {
            val keystore = file("release.keystore")
            val javaHome = System.getProperty("java.home")
            val apksignerJar = android.sdkDirectory.resolve("build-tools/${android.buildToolsVersion}/lib/apksigner.jar")
            
            exec {
                commandLine(
                    "${javaHome}/bin/java",
                    "-jar", apksignerJar.absolutePath,
                    "sign",
                    "--ks", keystore.absolutePath,
                    "--ks-pass", "pass:badini123",
                    "--ks-key-alias", "badini",
                    "--key-pass", "pass:badini123",
                    apkFile.absolutePath
                )
            }
            println("APK signed with v2/v3 schemes: ${apkFile.absolutePath}")
        }
    }
}

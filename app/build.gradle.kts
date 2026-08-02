import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Lokale Signatur-Konfiguration aus keystore.properties (nicht im Repo)
val keystoreProps = Properties().apply {
    val f = file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

System.setProperty("android.aapt2FromMavenOverride", "/opt/android-sdk/build-tools/34.0.0/aapt2")

android {
    namespace = "com.badini.translate.webapp"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.badini.translate.webapp"
        minSdk = 21
        targetSdk = 34
        versionCode = 2
        versionName = "1.1.0"
    }

    signingConfigs {
        create("release") {
            if (keystoreProps.getProperty("RELEASE_STORE_FILE") != null) {
                storeFile = file(keystoreProps.getProperty("RELEASE_STORE_FILE"))
                storePassword = keystoreProps.getProperty("RELEASE_STORE_PASSWORD")
                keyAlias = keystoreProps.getProperty("RELEASE_KEY_ALIAS")
                keyPassword = keystoreProps.getProperty("RELEASE_KEY_PASSWORD")
            }
        }
        create("upload") {
            if (keystoreProps.getProperty("UPLOAD_STORE_FILE") != null) {
                storeFile = file(keystoreProps.getProperty("UPLOAD_STORE_FILE"))
                storePassword = keystoreProps.getProperty("UPLOAD_STORE_PASSWORD")
                keyAlias = keystoreProps.getProperty("UPLOAD_KEY_ALIAS")
                keyPassword = keystoreProps.getProperty("UPLOAD_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("upload")
        }
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
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

// Wrap aapt2 for ARM64 compatibility
tasks.register("wrapAapt2") {
    doLast {
        val cacheDirs = listOf(
            file("/root/.gradle/caches/transforms-3"),
            file("/root/.gradle/caches/8.4/transforms")
        )
        
        for (cacheDir in cacheDirs) {
            if (cacheDir.exists()) {
                cacheDir.walkTopDown().forEach { file ->
                    if (file.name == "aapt2" && file.isFile) {
                        val firstBytes = try { file.readBytes().take(4).toByteArray() } catch(e: Exception) { byteArrayOf() }
                        if (firstBytes.size == 4 && firstBytes[0] == 0x7f.toByte()) {
                            val origFile = file.resolveSibling("aapt2.orig")
                            if (!origFile.exists()) {
                                file.copyTo(origFile)
                                println("Backed up: ${file.path}")
                                
                                val wrapper = """#!/usr/bin/env python3
import sys, os, struct, subprocess
def read_n(n):
    b = b''
    while len(b) < n:
        c = os.read(sys.stdin.buffer.fileno(), n - len(b))
        if not c: raise EOFError()
        b += c
    return b
SDK_AAPT2 = "/opt/android-sdk/build-tools/34.0.0/aapt2"
sys.stdout.buffer.write(b'Ready\n')
sys.stdout.buffer.flush()
while True:
    try:
        h = read_n(4)
        l = struct.unpack('<I', h)[0]
        if l == 0 or l > 1048576: break
        cmd = read_n(l).decode('utf-8', errors='replace').strip()
        if not cmd or cmd == 'quit': break
        parts = cmd.split()
        r = subprocess.run([SDK_AAPT2] + parts, capture_output=True, timeout=120)
        resp = struct.pack('<III', len(r.stdout), len(r.stderr), r.returncode)
        sys.stdout.buffer.write(resp)
        if r.stdout: sys.stdout.buffer.write(r.stdout)
        if r.stderr: sys.stdout.buffer.write(r.stderr)
        sys.stdout.buffer.flush()
    except: break
sys.stdout.buffer.write(b'Exiting daemon\n')
sys.stdout.buffer.flush()
"""
                                file.writeText(wrapper)
                                file.setExecutable(true)
                                println("Wrapped: ${file.path}")
                            }
                        }
                    }
                }
            }
        }
    }
}

tasks.matching { it.name.contains("mergeReleaseResources") }.configureEach {
    dependsOn("wrapAapt2")
}

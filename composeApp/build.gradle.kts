plugins {
    kotlin("multiplatform")
    id("com.android.application")
    id("org.jetbrains.compose")
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "17"
            }
        }
    }
    
    wasmJs {
        moduleName = "composeApp"
        browser {
            commonWebpackConfig {
                outputFileName = "composeApp.js"
            }
        }
        binaries.executable()
    }
    
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation(compose.components.resources)
                implementation(compose.materialIconsExtended)
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
            }
        }
        
        val androidMain by getting {
            dependencies {
                implementation("androidx.activity:activity-compose:1.8.2")
                implementation("androidx.appcompat:appcompat:1.6.1")
                implementation("androidx.core:core-ktx:1.12.0")
            }
        }
        
        val wasmJsMain by getting {
            dependencies {
                // Compose Web Wasm standard support
            }
        }
    }
}

android {
    namespace = "com.closetos.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.closetos.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }
    
    sourceSets {
        named("main") {
            manifest.srcFile("src/androidMain/AndroidManifest.xml")
            res.srcDirs("src/androidMain/res")
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

val wasmJsCopySkiko = tasks.register("wasmJsCopySkiko") {
    doLast {
        val expandedDir = rootProject.file(".gradle/expanded")
        val targetDir = rootProject.layout.buildDirectory.dir("js/packages/composeApp/kotlin").get().asFile
        targetDir.mkdirs()
        expandedDir.listFiles()?.forEach { dir ->
            if (dir.isDirectory) {
                val mjsFile = dir.resolve("skiko.mjs")
                val wasmFile = dir.resolve("skiko.wasm")
                if (mjsFile.exists()) {
                    mjsFile.copyTo(targetDir.resolve("skiko.mjs"), overwrite = true)
                }
                if (wasmFile.exists()) {
                    wasmFile.copyTo(targetDir.resolve("skiko.wasm"), overwrite = true)
                }
            }
        }
        if (!targetDir.resolve("skiko.mjs").exists()) {
            error("skiko.mjs not found after copy — run a full Gradle sync/build first")
        }
    }
}

tasks.named("compileKotlinWasmJs").configure {
    finalizedBy(wasmJsCopySkiko)
}

tasks.configureEach {
    if (name == "wasmJsBrowserDevelopmentWebpack" || 
        name == "wasmJsBrowserProductionWebpack" || 
        name == "wasmJsBrowserTest") {
        dependsOn(wasmJsCopySkiko)
    }
    if (name == "wasmJsDevelopmentExecutableCompileSync" || 
        name == "wasmJsProductionExecutableCompileSync") {
        finalizedBy(wasmJsCopySkiko)
    }
}


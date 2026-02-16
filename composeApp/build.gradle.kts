import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.atomicfu)
}

kotlin {
    android {
        namespace = "com.connor.kwitter.composeapp"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    compilerOptions {
        //jvmTarget.set(JvmTarget.JVM_21)
        freeCompilerArgs.addAll(
            "-Xjvm-default=all",
            "-Xcontext-parameters",
            "-Xwhen-guards"
        )
    }
    
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }
    
    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.activity.compose)

            // Coroutines Android
            implementation(libs.kotlinx.coroutines.android)

            // Video playback
            implementation(libs.androidx.media3.exoplayer)
            implementation(libs.androidx.media3.ui)

            // Coil video thumbnail
            implementation(libs.coil.video)

            // Koin Android
            implementation(libs.koin.android)
            implementation(libs.koin.androidx.compose)
        }

        iosMain.dependencies {
            // iOS specific dependencies
        }

        commonMain.dependencies {
            // Compose
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)

            // Kotlinx Serialization
            implementation(libs.kotlinx.serialization.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.serialization.protobuf)

            // Coroutines
            implementation(libs.kotlinx.coroutines.core)

            // Kotlinx Extensions
            implementation(libs.kotlinx.datetime)
            // atomicfu is applied as a plugin, no need for direct dependency
            implementation(libs.kotlinx.io.core)

            // DataStore
            implementation(libs.androidx.datastore.preferences)
            implementation(libs.androidx.datastore.core)

            // Room
            implementation(libs.androidx.room.runtime)
            implementation(libs.androidx.room.paging)
            implementation(libs.androidx.sqlite.bundled)

            // Paging
            implementation(libs.androidx.paging.common)
            implementation(libs.androidx.paging.compose)

            // Arrow
            implementation(libs.arrow.core)
            implementation(libs.arrow.fx.coroutines)
            implementation(libs.arrow.optics)
            implementation(libs.arrow.resilience)

            // Molecule
            implementation(libs.molecule.runtime)

            // Koin
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)

            // Crash reporting
            implementation(libs.crashkios.crashlytics)

            // Navigation
            implementation(libs.androidx.navigation3.ui)
            // Navigation Android
            implementation(libs.androidx.navigation3.runtime.android)

            // Lifecycle ViewModel with Navigation3
            implementation(libs.androidx.lifecycle.viewmodel.navigation3)

            // Ktor
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.cio)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.client.auth)
            implementation(libs.ktor.client.encoding)
            implementation(libs.ktor.client.websockets)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktor.serialization.kotlinx.protobuf)

            // Coil
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor3)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.koin.test)
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)

    // Room KSP
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspIosArm64", libs.androidx.room.compiler)
    add("kspIosSimulatorArm64", libs.androidx.room.compiler)

    // Arrow Optics KSP
    add("kspCommonMainMetadata", libs.arrow.optics.ksp.plugin)
    add("kspAndroid", libs.arrow.optics.ksp.plugin)
    add("kspIosArm64", libs.arrow.optics.ksp.plugin)
    add("kspIosSimulatorArm64", libs.arrow.optics.ksp.plugin)
}

// Room configuration
room {
    schemaDirectory("$projectDir/schemas")
}

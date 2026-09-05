import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.jetbrains.kotlin.serialization)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
}

/**
 * Which commit a build came from, so a page that has been deployed can say
 * which one it is.
 *
 * Read here rather than in a task so the value is a configuration input and
 * the generated file is rewritten when it changes. Deliberately not
 * `git status`: that answers differently after every edit, and asking it here
 * would throw away the configuration cache on every build.
 */
private val gitCommit = providers.exec {
    commandLine("git", "rev-parse", "--short=8", "HEAD")
    isIgnoreExitValue = true
}.standardOutput.asText.map { it.trim().ifBlank { "unknown" } }

private val gitCommittedAt = providers.exec {
    commandLine("git", "log", "-1", "--format=%cd", "--date=format:%Y-%m-%d %H:%M")
    isIgnoreExitValue = true
}.standardOutput.asText.map { it.trim().ifBlank { "unknown" } }

val generateBuildInfo by tasks.registering {
    val commit = gitCommit
    val committedAt = gitCommittedAt
    val into = layout.buildDirectory.dir("generated/buildInfo")

    inputs.property("commit", commit)
    inputs.property("committedAt", committedAt)
    outputs.dir(into)

    doLast {
        val file = into.get().asFile
            .resolve("pl/pelotasplus/eyeofbeholder/BuildInfo.kt")

        file.parentFile.mkdirs()
        file.writeText(
            """
            package pl.pelotasplus.eyeofbeholder

            /** Written by Gradle at build time. See generateBuildInfo. */
            object BuildInfo {
                const val COMMIT = "${commit.get()}"
                const val COMMITTED_AT = "${committedAt.get()}"
            }

            """.trimIndent(),
        )
    }
}

kotlin {
    sourceSets.all {
        languageSettings.optIn("kotlin.ExperimentalUnsignedTypes")
    }

    // Android and the desktop both have java.io, and both keep saves as files;
    // without this they are unrelated source sets and would need two copies.
    //
    // Everything that is not Android draws through Skia and can be handed a
    // buffer of pixels the same way, which is one source set rather than four.
    applyDefaultHierarchyTemplate {
        common {
            group("jvmShared") {
                withJvm()
                withAndroidTarget()
            }
            group("skia") {
                withJvm()
                withIos()
                withJs()
                withWasmJs()
            }
        }
    }

    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
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
    
    jvm()
    
    // The dev server leaves the browser alone: the page reloads itself on
    // every rebuild, so a tab is opened once and kept, and one more of them
    // each time the server restarts is not help.
    js {
        browser {
            commonWebpackConfig {
                devServer = (devServer ?: KotlinWebpackConfig.DevServer()).copy(open = false)
            }
        }
        binaries.executable()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            commonWebpackConfig {
                devServer = (devServer ?: KotlinWebpackConfig.DevServer()).copy(open = false)
            }
        }
        binaries.executable()
    }
    
    sourceSets {
        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.koin.android)
        }
        commonMain {
            kotlin.srcDir(generateBuildInfo)
        }
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(compose.preview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.androidx.navigation.compose)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.kermit)
            implementation(libs.kotlinx.collections.immutable)
            implementation(libs.kotlinx.coroutinesCore)
            implementation(libs.kotlinx.serializationJson)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
        }
    }
}

android {
    namespace = "pl.pelotasplus.eyeofbeholder"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "pl.pelotasplus.eyeofbeholder"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    debugImplementation(compose.uiTooling)
}

// The game data is not in the repository, so a checkout without it can only
// run what does not read it. See NeedsGameData.
tasks.named<Test>("jvmTest") {
    if (providers.gradleProperty("withoutGameData").isPresent) {
        useJUnit { excludeCategories("pl.pelotasplus.eyeofbeholder.NeedsGameData") }
    }
}

compose.desktop {
    application {
        mainClass = "pl.pelotasplus.eyeofbeholder.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "pl.pelotasplus.eyeofbeholder"
            packageVersion = "1.0.0"
        }
    }
}

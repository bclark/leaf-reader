import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import javax.inject.Inject

plugins {
    id("com.android.test")
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "net.digitalbc.leafreader.r8smoke"
    compileSdk = 36
    targetProjectPath = ":app"
    defaultConfig {
        minSdk = 26
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    flavorDimensions += "distribution"
    productFlavors {
        create("full") { dimension = "distribution" }
        create("fdroid") { dimension = "distribution" }
    }
    buildTypes {
        create("performance") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    // Keep runner dependencies independent of libraries removed from the optimized target.
    experimentalProperties["android.experimental.self-instrumenting"] = true
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

abstract class GenerateR8Probes @Inject constructor(private val exec: ExecOperations) : DefaultTask() {
    @get:InputFile @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val mapping: RegularFileProperty
    @get:InputFile @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val script: RegularFileProperty
    @get:OutputDirectory abstract val outputDirectory: DirectoryProperty

    @TaskAction fun generate() {
        exec.exec {
            commandLine("python3", script.get().asFile, mapping.get().asFile,
                "--output", outputDirectory.get().file("r8-probes.json").asFile)
        }
    }
}

androidComponents {
    beforeVariants { it.enable = it.buildType == "performance" }
    onVariants(selector().withBuildType("performance")) { variant ->
        val flavor = variant.productFlavors.single().second
        val capitalized = flavor.replaceFirstChar { it.uppercaseChar() }
        val generate = tasks.register<GenerateR8Probes>("generate${capitalized}R8Probes") {
            dependsOn(":app:minify${capitalized}PerformanceWithR8")
            mapping.set(project(":app").layout.buildDirectory.file("outputs/mapping/${flavor}Performance/mapping.txt"))
            script.set(rootProject.layout.projectDirectory.file("scripts/r8-smoke-probes.py"))
            outputDirectory.set(layout.buildDirectory.dir("generated/r8-probes/$flavor"))
        }
        variant.sources.assets!!.addGeneratedSourceDirectory(generate, GenerateR8Probes::outputDirectory)
    }
}

dependencies {
    implementation("androidx.test:runner:1.7.0")
    implementation("androidx.test.ext:junit:1.3.0")
    implementation("androidx.test.uiautomator:uiautomator:2.4.1")
}

import java.nio.file.Files

plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "io.benwiegand.projection.geargrinder.privd"
    compileSdk = 36
    defaultConfig {
        minSdk = 26
        targetSdk = 36
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

androidComponents {
    onVariants { variant ->
        val buildType = variant.name;
        val buildTypeUpper = variant.name.replaceFirstChar { it.uppercase() }

        tasks.register<Exec>("create${buildTypeUpper}PrivdJar") {
            group = "build"
            description = "privileged daemon jar asset"
            dependsOn(tasks.build)

            val outputJar = rootProject.projectDir.resolve("app/src/main/assets/privd.jar")
            val androidJar = "${android.sdkDirectory.path}/platforms/android-${android.defaultConfig.targetSdk}/android.jar"
            val classesDir = layout.buildDirectory.dir("intermediates/javac/${buildType}/compile${buildTypeUpper}JavaWithJavac/classes")
            val classFiles = Files.walk(file(classesDir).toPath())
                .filter { it.toFile().isFile() }
                .toArray()

            commandLine(
                "${android.sdkDirectory}/build-tools/${android.buildToolsVersion}/d8",
                "--release",
                "--output", outputJar,
                "--classpath", androidJar,
                *classFiles
            )
        }
    }
}

import org.gradle.process.ExecOperations
import javax.inject.Inject

plugins {
    alias(libs.plugins.android.library)
}

// The Audio Lab Stage room (3D placement with measured ear responses) runs as a native library, built here the
// same way as opusDecoder: a cacheable task that drives the NDK's CMake per ARM ABI.
val stageNdkVersion = libs.versions.androidNdk.get()
val stageCmakeVersion = "4.1.2"

// Only ARM ABIs are shipped, as in opusDecoder; the room is off on x86 devices.
val stageAbis = listOf("arm64-v8a", "armeabi-v7a")

android {
    namespace = "com.metrolist.stage"
    compileSdk {
        version = release(37) { minorApiLevel = 1 }
    }

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    lint {
        ignoreTestSources = true
    }
}

// AGP's externalNativeBuild never reaches the Gradle build cache, so CI would rebuild the library on every run.
// This task is cacheable: unless the C++ sources or the toolchain pins change, the stripped libraries come
// from the build cache and no NDK is needed at all.
@CacheableTask
abstract class BuildStageJni
    @Inject
    constructor(
        private val exec: ExecOperations,
        private val fs: FileSystemOperations,
    ) : DefaultTask() {
        @get:InputFiles
        @get:PathSensitive(PathSensitivity.RELATIVE)
        @get:IgnoreEmptyDirectories
        abstract val jniSources: ConfigurableFileCollection

        @get:Input
        abstract val ndkVersion: Property<String>

        @get:Input
        abstract val cmakeVersion: Property<String>

        @get:Input
        abstract val abis: ListProperty<String>

        @get:Input
        abstract val minSdk: Property<Int>

        @get:Internal
        abstract val jniDirectory: DirectoryProperty

        @get:Internal
        abstract val sdkDirectory: DirectoryProperty

        @get:Internal
        abstract val workDirectory: DirectoryProperty

        @get:OutputDirectory
        abstract val outputDirectory: DirectoryProperty

        @TaskAction
        fun build() {
            val sdk = sdkDirectory.get().asFile
            val ndk = sdk.resolve("ndk/${ndkVersion.get()}")
            val cmakeBin = sdk.resolve("cmake/${cmakeVersion.get()}/bin")
            val executableSuffix = if (System.getProperty("os.name").startsWith("Windows")) ".exe" else ""
            if (!ndk.isDirectory || !cmakeBin.isDirectory) installToolchain(sdk)

            fs.delete { delete(outputDirectory) }
            for (abi in abis.get()) {
                val buildDir = workDirectory.get().asFile.resolve(abi)
                // Reconfiguring a stale tree for a new ANDROID_PLATFORM produced a larger library than a clean build.
                fs.delete { delete(buildDir) }
                exec.exec {
                    commandLine(
                        cmakeBin.resolve("cmake$executableSuffix"),
                        "-S",
                        jniDirectory.get().asFile,
                        "-B",
                        buildDir,
                        "-G",
                        "Ninja",
                        "-DCMAKE_MAKE_PROGRAM=${cmakeBin.resolve("ninja$executableSuffix")}",
                        "-DCMAKE_TOOLCHAIN_FILE=${ndk.resolve("build/cmake/android.toolchain.cmake")}",
                        "-DANDROID_ABI=$abi",
                        "-DANDROID_PLATFORM=android-${minSdk.get()}",
                        "-DCMAKE_BUILD_TYPE=Release",
                    )
                }
                exec.exec { commandLine(cmakeBin.resolve("cmake$executableSuffix"), "--build", buildDir, "--target", "mochystage") }
                fs.copy {
                    from(buildDir.resolve("libmochystage.so"))
                    into(outputDirectory.get().asFile.resolve(abi))
                }
            }
        }

        private fun installToolchain(sdk: File) {
            val isWindows = System.getProperty("os.name").startsWith("Windows")
            val sdkManagerName = if (isWindows) "sdkmanager.bat" else "sdkmanager"
            val sdkManager =
                sdk
                    .resolve("cmdline-tools")
                    .listFiles()
                    .orEmpty()
                    .map { it.resolve("bin/$sdkManagerName") }
                    .firstOrNull { it.isFile && (isWindows || it.canExecute()) }
                    ?: error("Install NDK ${ndkVersion.get()} and CMake ${cmakeVersion.get()}, or the SDK command-line tools")
            val command = listOf(sdkManager.absolutePath, "--install", "ndk;${ndkVersion.get()}", "cmake;${cmakeVersion.get()}")
            exec.exec {
                commandLine(if (isWindows) listOf("cmd.exe", "/d", "/c", "call") + command else command)
            }
        }
    }

val buildStageJni =
    tasks.register<BuildStageJni>("buildStageJni") {
        jniDirectory.set(layout.projectDirectory.dir("src/main/cpp"))
        jniSources.from(
            fileTree("src/main/cpp") {
                exclude("**/.git", "**/.git/**")
            },
        )
        ndkVersion.set(stageNdkVersion)
        cmakeVersion.set(stageCmakeVersion)
        abis.set(stageAbis)
        minSdk.set(24)
        sdkDirectory.set(androidComponents.sdkComponents.sdkDirectory)
        workDirectory.set(layout.buildDirectory.dir("stage-cmake"))
        outputDirectory.set(layout.buildDirectory.dir("stage-jniLibs"))
    }

androidComponents {
    onVariants { variant ->
        variant.sources.jniLibs?.addGeneratedSourceDirectory(buildStageJni, BuildStageJni::outputDirectory)
    }
}

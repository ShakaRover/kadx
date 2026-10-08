import com.diffplug.gradle.spotless.FormatExtension
import com.diffplug.gradle.spotless.SpotlessExtension
import com.diffplug.spotless.LineEnding
import nl.littlerobots.vcu.plugin.resolver.VersionSelectors
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform

plugins {
	id("nl.littlerobots.version-catalog-update") version "1.1.1"
	id("com.diffplug.spotless") version "8.9.0"
}

val kadxEnv = loadEnv(file("$rootDir/.env"))

val kadxVersion = kadxEnv["KADX_VERSION"] ?: "dev"
extra.set("kadxVersion", kadxVersion)
println("kadx version: $kadxVersion")
version = kadxVersion

val kadxBuildJavaVersion = getBuildJavaVersion()
extra.set("kadxBuildJavaVersion", kadxBuildJavaVersion)

fun getBuildJavaVersion(): Int? {
	val envVarName = "KADX_BUILD_JAVA_VERSION"
	val buildJavaVer = kadxEnv[envVarName]?.toInt() ?: return null
	if (buildJavaVer < 11) {
		throw GradleException("'$envVarName' can't be set to lower than 11")
	}
	println("Set Java toolchain for kadx build to version '$buildJavaVer'")
	return buildJavaVer
}

allprojects {
	apply(plugin = "java")
	apply(plugin = "checkstyle")
	apply(plugin = "com.diffplug.spotless")

	configure<SpotlessExtension> {
		java {
			importOrderFile("$rootDir/config/code-formatter/eclipse.importorder")
			eclipse().configFile("$rootDir/config/code-formatter/eclipse.xml")
			removeUnusedImports()
			commonFormatOptions()
		}
		kotlin {
			ktlint().editorConfigOverride(mapOf("indent_style" to "tab"))
			commonFormatOptions()
		}
		kotlinGradle {
			ktlint()
			commonFormatOptions()
		}
		format("misc") {
			target("**/*.gradle", "**/*.xml", "**/.gitignore", "**/.properties")
			targetExclude(
				".gradle/**",
				".idea/**",
				"*/build/**",
				// tests/ 下是 .gitignore 掉的 OSS 语料（tests/oss、tests/work 等）与临时产物，
				// 属于第三方代码，不能被 spotless 改写
				"tests/**",
			)
			commonFormatOptions()
		}
	}
}

fun FormatExtension.commonFormatOptions() {
	lineEndings = LineEnding.UNIX
	encoding = Charsets.UTF_8
	trimTrailingWhitespace()
	endWithNewline()
}

versionCatalogUpdate {
	sortByKey = true
	versionSelector(VersionSelectors.STABLE)
	keep {
		keepUnusedVersions = true
	}
}

fun loadEnv(file: File): Map<String, String> {
	val envMap = HashMap<String, String>()
	System
		.getenv()
		.filter { it.key.startsWith("KADX_") }
		.forEach { envMap[it.key] = it.value }
	if (file.exists()) {
		file
			.readLines()
			.map { it.trim() }
			.filter { it.isNotEmpty() && !it.startsWith("#") }
			.forEach {
				val (k, v) = it.split("=", limit = 2)
				envMap[k.trim()] = v.trim()
			}
	}
	println(
		"Loaded env vars (${envMap.size}):\n${
			envMap.toList().sortedBy { it.first }.joinToString(separator = "\n") { "${it.first}=${it.second}" }
		}\n",
	)
	return envMap
}

val distWinConfiguration =
	configurations.create("distWinConfiguration") {
		isCanBeConsumed = false
	}
val distWinWithJreConfiguration =
	configurations.create("distWinWithJreConfiguration") {
		isCanBeConsumed = false
	}
val distMacConfiguration =
	configurations.create("distMacConfiguration") {
		isCanBeConsumed = false
	}
dependencies {
	distWinConfiguration(project(":kadx-gui", "distWinConfiguration"))
	distWinWithJreConfiguration(project(":kadx-gui", "distWinWithJreConfiguration"))
	distMacConfiguration(project(":kadx-gui", "distMacConfiguration"))
}

val copyArtifacts =
	tasks.register<Copy>("copyArtifacts") {
		val jarCliPattern = "kadx-cli-(.*)-all.jar".toPattern()
		from(tasks.getByPath(":kadx-cli:installShadowDist")) {
			exclude("**/*.jar")
			filter { line ->
				jarCliPattern
					.matcher(line)
					.replaceAll("kadx-$1-all.jar")
					.replace("-jar \"\\\"\$CLASSPATH\\\"\"", "-cp \"\\\"\$CLASSPATH\\\"\" kadx.cli.KadxCLI")
					.replace("-jar \"%CLASSPATH%\"", "-cp \"%CLASSPATH%\" kadx.cli.KadxCLI")
			}
		}
		val jarGuiPattern = "kadx-gui-(.*)-all.jar".toPattern()
		from(tasks.getByPath(":kadx-gui:installShadowDist")) {
			exclude("**/*.jar")
			filter { line -> jarGuiPattern.matcher(line).replaceAll("kadx-$1-all.jar") }
		}
		from(tasks.getByPath(":kadx-gui:installShadowDist")) {
			include("**/*.jar")
			rename("kadx-gui-(.*)-all.jar", "kadx-$1-all.jar")
		}
		from(layout.projectDirectory) {
			include("README.md")
			include("LICENSE")
		}
		into(layout.buildDirectory.dir("kadx"))
		duplicatesStrategy = DuplicatesStrategy.EXCLUDE
	}

val pack =
	tasks.register<Zip>("pack") {
		from(copyArtifacts)
		archiveFileName.set("kadx-$kadxVersion.zip")
		destinationDirectory.set(layout.buildDirectory)
		eachFile {
			if (path == "bin/kadx" || path == "bin/kadx-gui") {
				permissions {
					unix("rwxr-xr-x")
				}
			}
		}
	}

val distWin =
	tasks.register<Zip>("distWin") {
		group = "kadx"
		description = "Build Windows bundle"

		from(distWinConfiguration)

		destinationDirectory.set(layout.buildDirectory.dir("distWin"))
		archiveFileName.set("kadx-gui-$kadxVersion-win.zip")
		duplicatesStrategy = DuplicatesStrategy.EXCLUDE
	}

val distWinWithJre =
	tasks.register<Zip>("distWinWithJre") {
		description = "Build Windows with JRE bundle"

		from(distWinWithJreConfiguration)

		destinationDirectory.set(layout.buildDirectory.dir("distWinWithJre"))
		archiveFileName.set("kadx-gui-$kadxVersion-with-jre-win.zip")
		duplicatesStrategy = DuplicatesStrategy.EXCLUDE
	}

val distMac =
	tasks.register<Copy>("distMac") {
		group = "kadx"
		description = "Build macOS DMG bundle (with bundled JRE)"

		from(distMacConfiguration)

		into(layout.buildDirectory.dir("distMac"))
	}

val dist =
	tasks.register("dist") {
		group = "kadx"
		description = "Build kadx distribution zip bundles"

		dependsOn(pack)

		val os = DefaultNativePlatform.getCurrentOperatingSystem()
		if (os.isWindows) {
			if (project.hasProperty("bundleJRE")) {
				println("Build win bundle with JRE")
				dependsOn(distWinWithJre)
			} else {
				dependsOn(distWin)
			}
		} else if (os.isMacOsX) {
			dependsOn(distMac)
		}
	}

val cleanBuildDir =
	tasks.register<Delete>("cleanBuildDir") {
		delete(layout.buildDirectory)
	}
tasks.getByName("clean").dependsOn(cleanBuildDir)

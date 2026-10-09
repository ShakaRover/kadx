pluginManagement {
	repositories {
		gradlePluginPortal()
		mavenCentral()
	}
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
	repositories {
		mavenCentral()
		// required for: aapt-proto, R8
		google()
		// smali/baksmali 来自 ShakaRover/ksmali（google/smali 的 fork，4.x 起改成 ANTLR4 + Kotlin），
		// 它发布在 Maven Central 上，无需任何凭据。
	}
	repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
}

plugins {
	id("org.gradle.toolchains.foojay-resolver-convention") version ("1.0.0")
}

if (!JavaVersion.current().isJava11Compatible) {
	throw GradleException("Kadx requires at least Java 11 for build (current version is '${JavaVersion.current()}')")
}

rootProject.name = "kadx"

include("kadx-core")
include("kadx-cli")
include("kadx-gui-api")
include("kadx-gui")

include("kadx-commons:kadx-app-commons")
include("kadx-commons:kadx-zip")
include("kadx-commons:kadx-analysis")

include("kadx-plugins:kadx-input-api")
include("kadx-plugins:kadx-dex-input")
include("kadx-plugins:kadx-java-input")
include("kadx-plugins:kadx-raung-input")
include("kadx-plugins:kadx-smali-input")
include("kadx-plugins:kadx-java-convert")
include("kadx-plugins:kadx-rename-mappings")
include("kadx-plugins:kadx-kotlin-metadata")
include("kadx-plugins:kadx-kotlin-source-debug-extension")
include("kadx-plugins:kadx-xapk-input")
include("kadx-plugins:kadx-aab-input")
include("kadx-plugins:kadx-apkm-input")
include("kadx-plugins:kadx-apks-input")

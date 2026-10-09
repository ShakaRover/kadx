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
		// smali/baksmali 用的是 ShakaRover/ksmali（google/smali 的 fork，4.x 起改成
		// ANTLR4 + Kotlin），它只发布到 GitHub Packages 的 io.github.shakarover.ksmali 组。
		// GitHub Packages 即使对公开制品也要求认证：需要 read:packages 的 PAT，
		// 通过 -Pgpr.user/-Pgpr.key（或环境变量 GPR_USER/GPR_KEY）提供。
		maven {
			name = "ksmali"
			url = uri("https://maven.pkg.github.com/ShakaRover/ksmali")
			content {
				includeGroup("io.github.shakarover.ksmali")
			}
			credentials {
				username = providers.gradleProperty("gpr.user").orNull
					?: System.getenv("GPR_USER")
					?: System.getenv("GITHUB_ACTOR")
					?: ""
				password = providers.gradleProperty("gpr.key").orNull
					?: System.getenv("GPR_KEY")
					?: System.getenv("GITHUB_TOKEN")
					?: ""
			}
		}
	}
	repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
}

// 缺凭据时依赖会以 401 失败，先给一句能看懂的提示，省得去猜 "Could not resolve ... smali:5.0.0"
val hasKsmaliCredentials =
	providers.gradleProperty("gpr.key").orNull != null ||
		System.getenv("GPR_KEY") != null ||
		System.getenv("GITHUB_TOKEN") != null
if (!hasKsmaliCredentials) {
	gradle.rootProject {
		logger.lifecycle(
			"WARN: 未提供 GitHub Packages 凭据，io.github.shakarover.ksmali:* 将无法解析。" +
				"请用 -Pgpr.user=<user> -Pgpr.key=<read:packages token>（或环境变量 GPR_USER/GPR_KEY）重试。",
		)
	}
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

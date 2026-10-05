import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
	java
	checkstyle
}

val kadxVersion = rootProject.extra["kadxVersion"] as String
val kadxBuildJavaVersion = rootProject.extra["kadxBuildJavaVersion"] as Int?

group = "io.github.skylot"
version = kadxVersion

dependencies {
	implementation(libs.slf4j.api)
	compileOnly(libs.jetbrains.annotations)

	testImplementation(libs.logback.classic)
	testImplementation(libs.assertj)

	testImplementation(libs.junit)
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")

	testCompileOnly(libs.jetbrains.annotations)
}

java {
	kadxBuildJavaVersion?.let { buildJavaVer ->
		toolchain {
			languageVersion = JavaLanguageVersion.of(buildJavaVer)
		}
	}
	sourceCompatibility = JavaVersion.VERSION_11
	targetCompatibility = JavaVersion.VERSION_11
}

checkstyle {
	toolVersion = libs.versions.checkstyle.get()
}

tasks {
	compileJava {
		options.encoding = "UTF-8"
		// options.compilerArgs = listOf("-Xlint:deprecation")
	}
	test {
		useJUnitPlatform()
		maxParallelForks = Runtime.getRuntime().availableProcessors()
		testLogging {
			showExceptions = true
			exceptionFormat = TestExceptionFormat.FULL
			showCauses = true
		}
	}
}

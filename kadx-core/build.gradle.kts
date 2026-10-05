plugins {
	id("kadx-kotlin")
	id("kadx-library")
}

dependencies {
	api(project(":kadx-plugins:kadx-input-api"))
	api(project(":kadx-commons:kadx-zip"))

	implementation(libs.gson)
	implementation(libs.kotlinx.coroutines.core)

	testImplementation(project(":kadx-plugins:kadx-dex-input"))
	// 'ClassNotFound' error is raised if set as 'testRuntime'
	// for the plugins below when running the tests from vscode.
	testImplementation(project(":kadx-plugins:kadx-smali-input"))
	testImplementation(project(":kadx-plugins:kadx-java-convert"))
	testImplementation(project(":kadx-plugins:kadx-java-input"))
	testImplementation(project(":kadx-plugins:kadx-raung-input"))

	testImplementation(libs.commons.lang3)
	testImplementation(libs.eclipse.jdt.ecj)
	testImplementation(libs.async.profiler)
}

val kadxTestJavaVersion = getTestJavaVersion()

fun getTestJavaVersion(): Int? {
	val envVarName = "KADX_TEST_JAVA_VERSION"
	val testJavaVer = System.getenv(envVarName)?.toInt() ?: return null
	val currentJavaVer =
		java.toolchain.languageVersion
			.get()
			.asInt()
	if (testJavaVer < currentJavaVer) {
		throw GradleException("'$envVarName' can't be set to lower version than $currentJavaVer")
	}
	println("Set Java toolchain for core tests to version '$testJavaVer'")
	return testJavaVer
}

tasks.named<Test>("test") {
	kadxTestJavaVersion?.let { testJavaVer ->
		javaLauncher =
			javaToolchains.launcherFor {
				languageVersion = JavaLanguageVersion.of(testJavaVer)
			}
	}

	// disable cache to allow test's rerun,
	// because most tests are integration and depends on plugins and environment
	outputs.cacheIf { false }

	// exclude temp tests
	exclude("**/tmp/*")

	// maxHeapSize = "4g"
}

tasks.processResources {
	val kadxVersion = rootProject.extra["kadxVersion"] as String
	val kadxBundleType = project.findProperty("kadxBundleType") as String? ?: ""

	inputs.property("kadxVersion", kadxVersion)
	inputs.property("kadxBundleType", kadxBundleType)

	filesMatching("kadx-build-info.properties") {
		expand(
			mapOf(
				"kadxVersion" to kadxVersion,
				"kadxBundleType" to kadxBundleType,
			),
		)
	}
}

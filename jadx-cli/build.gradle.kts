plugins {
	id("jadx-kotlin")
	id("jadx-library")
	id("application")

	// use shadow only for application scripts, jar will be copied from jadx-gui
	alias(libs.plugins.shadow)
}

dependencies {
	implementation(project(":jadx-core"))
	implementation(project(":jadx-plugins-tools"))
	implementation(project(":jadx-commons:jadx-app-commons"))
	implementation(project(":jadx-commons:jadx-analysis"))

	runtimeOnly(project(":jadx-plugins:jadx-dex-input"))
	runtimeOnly(project(":jadx-plugins:jadx-java-input"))
	runtimeOnly(project(":jadx-plugins:jadx-java-convert"))
	runtimeOnly(project(":jadx-plugins:jadx-smali-input"))
	runtimeOnly(project(":jadx-plugins:jadx-rename-mappings"))
	runtimeOnly(project(":jadx-plugins:jadx-kotlin-metadata"))
	runtimeOnly(project(":jadx-plugins:jadx-kotlin-source-debug-extension"))
	runtimeOnly(project(":jadx-plugins:jadx-xapk-input"))
	runtimeOnly(project(":jadx-plugins:jadx-aab-input"))
	runtimeOnly(project(":jadx-plugins:jadx-apkm-input"))
	runtimeOnly(project(":jadx-plugins:jadx-apks-input"))

	implementation(libs.jcommander)
	implementation(libs.logback.classic)
	implementation(libs.gson)
}

application {
	applicationName = "jadx"
	mainClass.set("jadx.cli.JadxCLI")
	applicationDefaultJvmArgs =
		listOf(
			"-XX:+IgnoreUnrecognizedVMOptions",
			"-Xms256M",
			"-XX:MaxRAMPercentage=70.0",
			"-XX:ParallelGCThreads=3",
			// disable zip checks (#1962)
			"-Djdk.util.zip.disableZip64ExtraFieldValidation=true",
			// Foreign API access for 'directories' library (Windows only)
			"--enable-native-access=ALL-UNNAMED",
		)
	applicationDistribution.from("$rootDir") {
		include("README.md")
		include("NOTICE")
		include("LICENSE")
	}
}

tasks.shadowJar {
	// shadow jar not needed
	configurations = listOf()
}

// 对**真实开源项目 APK** 的反编译回归测试（“错误数不得上升”看门狗）。
// 默认不参与 check/build；需显式指定含 *.apk 的目录：
//   JADX_REAL_APKS=$PWD/tests/apks ./gradlew :jadx-cli:realApkTest
//   或 ./gradlew :jadx-cli:realApkTest -PjadxRealApks=$PWD/tests/apks
// 真实 APK 较大，需要更大的堆（默认 test 堆会 OOM）。
tasks.named<Test>("test") {
	// 正常测试不跑真实 APK（体积大、需外部目录）
	filter { excludeTestsMatching("*RealApkDecompileTest") }
}

tasks.register<Test>("realApkTest") {
	description = "Decompile real OSS APKs and assert error baselines (opt-in)"
	group = "verification"
	testClassesDirs =
		sourceSets.test
			.get()
			.output.classesDirs
	classpath = sourceSets.test.get().runtimeClasspath
	maxHeapSize = "4g"
	useJUnitPlatform()
	filter { includeTestsMatching("*RealApkDecompileTest") }
	outputs.upToDateWhen { false }
	val apkDir =
		providers
			.gradleProperty("jadxRealApks")
			.orElse(providers.environmentVariable("JADX_REAL_APKS"))
	apkDir.orNull?.let { systemProperty("jadx.real.apks", it) }
}

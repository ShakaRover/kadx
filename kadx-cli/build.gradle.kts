plugins {
	id("kadx-kotlin")
	id("kadx-library")
	id("application")

	// use shadow only for application scripts, jar will be copied from kadx-gui
	alias(libs.plugins.shadow)
}

dependencies {
	implementation(project(":kadx-core"))
	implementation(project(":kadx-commons:kadx-app-commons"))
	implementation(project(":kadx-commons:kadx-analysis"))

	runtimeOnly(project(":kadx-plugins:kadx-dex-input"))
	runtimeOnly(project(":kadx-plugins:kadx-java-input"))
	runtimeOnly(project(":kadx-plugins:kadx-java-convert"))
	runtimeOnly(project(":kadx-plugins:kadx-smali-input"))
	runtimeOnly(project(":kadx-plugins:kadx-rename-mappings"))
	runtimeOnly(project(":kadx-plugins:kadx-kotlin-metadata"))
	runtimeOnly(project(":kadx-plugins:kadx-kotlin-source-debug-extension"))
	runtimeOnly(project(":kadx-plugins:kadx-xapk-input"))
	runtimeOnly(project(":kadx-plugins:kadx-aab-input"))
	runtimeOnly(project(":kadx-plugins:kadx-apkm-input"))
	runtimeOnly(project(":kadx-plugins:kadx-apks-input"))

	implementation(libs.jcommander)
	implementation(libs.logback.classic)
	implementation(libs.gson)
}

application {
	applicationName = "kadx"
	mainClass.set("kadx.cli.KadxCLI")
	applicationDefaultJvmArgs =
		listOf(
			"-XX:+IgnoreUnrecognizedVMOptions",
			"-Xms256M",
			"-XX:MaxRAMPercentage=70.0",
			// 不要固定 -XX:ParallelGCThreads。上游 jadx 设的 3 在 10 核机器上只用了 30% 的 GC 并行度：
			// GC 日志实测 `Using 3 workers of 3 for full compaction`，young GC 平均停顿 121ms，
			// 单次 G1 Full GC 长达 147s；交给 JVM 按核数自动决定（本机 10 核实测为 9，
			// 而非 8：HotSpot 的 ergonomic 公式是 8 + (n-8)*5/8）。
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
//   KADX_REAL_APKS=$PWD/tests/apks ./gradlew :kadx-cli:realApkTest
//   或 ./gradlew :kadx-cli:realApkTest -PkadxRealApks=$PWD/tests/apks
// 真实 APK 较大，需要更大的堆（默认 test 堆会 OOM）。
tasks.named<Test>("test") {
	// 正常测试不跑真实 APK（体积大、需外部目录）
	filter { excludeTestsMatching("*RealApkDecompileTest") }
	filter { excludeTestsMatching("*RealApkSingleClassTest") }
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
	filter { includeTestsMatching("*RealApkSingleClassTest") }
	outputs.upToDateWhen { false }
	val apkDir =
		providers
			.gradleProperty("kadxRealApks")
			.orElse(providers.environmentVariable("KADX_REAL_APKS"))
	apkDir.orNull?.let { systemProperty("kadx.real.apks", it) }
}

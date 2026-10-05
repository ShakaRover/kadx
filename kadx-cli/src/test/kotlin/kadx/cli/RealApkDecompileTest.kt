package kadx.cli

import kadx.api.KadxArgs
import kadx.api.KadxDecompiler
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.util.Properties

/**
 * 对**真实开源项目 APK** 的反编译回归测试（“错误数不得上升”看门狗）。
 *
 * 与仓库里那些几 KB 的假样本 APK 不同，这里用的是真实 App 的 debug APK
 * （含 Kotlin/Compose/coroutines 等大量真实字节码）。
 *
 * **默认跳过**（真实 APK 体积大、不入库）：指向包含 `*.apk` 的目录即可运行：
 * ```
 * KADX_REAL_APKS=$PWD/tests/apks ./gradlew :kadx-cli:test --tests "*RealApkDecompileTest"
 * ```
 * 也支持 `-Dkadx.real.apks=<dir>`。
 *
 * 只检查在 `real-apk-baseline.properties` 中有条目的 APK（键=文件名，值=允许的最大错误数）。
 * 修复问题后应下调基线值。
 */
class RealApkDecompileTest {

	@Test
	fun `real apks decompile within error baseline`() {
		val dir = System.getProperty("kadx.real.apks") ?: System.getenv("KADX_REAL_APKS")
		assumeTrue(dir != null && File(dir).isDirectory) {
			"set KADX_REAL_APKS (or -Dkadx.real.apks) to a directory containing real *.apk"
		}
		val apkDir = File(dir!!)
		val baseline = loadBaseline()
		val apks = apkDir.listFiles { f -> f.isFile && f.extension == "apk" }
			.orEmpty()
			.filter { baseline.containsKey(it.name) }
			.sortedBy { it.name }
		assumeTrue(apks.isNotEmpty()) {
			"no *.apk with a baseline entry found in $apkDir (known: ${baseline.keys})"
		}

		val failures = ArrayList<String>()
		for (apk in apks) {
			val maxErrors = checkNotNull(baseline.getProperty(apk.name)) { "no baseline for ${apk.name}" }.toInt()
			val errors = decompileAndCountErrors(apk, apkDir)
			println("RealApk ${apk.name}: errors=$errors baseline=$maxErrors")
			if (errors > maxErrors) {
				failures += "${apk.name}: errors=$errors > baseline=$maxErrors"
			}
		}
		assertThat(failures).`as`("real APK decompile error regressions").isEmpty()
	}

	private fun decompileAndCountErrors(apk: File, apkDir: File): Int {
		val outDir = File(apkDir.parentFile, "out/${apk.nameWithoutExtension}")
		outDir.mkdirs()
		val args = KadxArgs().apply {
			inputFiles.add(apk)
			this.outDir = outDir
		}
		KadxDecompiler(args).use { kadx ->
			kadx.load()
			kadx.save()
			return kadx.getErrorsCount()
		}
	}

	private fun loadBaseline(): Properties {
		val props = Properties()
		val res = RealApkDecompileTest::class.java.classLoader.getResourceAsStream(BASELINE_FILE)
			?: error("missing test resource: $BASELINE_FILE")
		res.use { props.load(it) }
		return props
	}

	private companion object {
		private const val BASELINE_FILE = "real-apk-baseline.properties"
	}
}

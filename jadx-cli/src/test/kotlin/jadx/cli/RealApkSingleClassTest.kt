package jadx.cli

import jadx.api.JadxArgs
import jadx.api.JadxDecompiler
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 对**真实 APK 中已知曾导致反编译挂死/超慢的类**做限时回归：
 *
 * 1. `BasicTextFieldKt`（Compose 大方法）—— 曾触发 [jadx.core.dex.visitors.regions.variables.ProcessVariables]
 *    中 `isAllUseAfter` 的 O(n²) 路径：`HashSet(usePlaces)` + 逐块 `isRegionContainsRegion`，
 *    在 21k 类的 Notes debug APK 上把整个反编译卡死在 99% (>20 分钟)。
 *    修复方式：区域树 DFS 序号索引 + O(1) 区间比较（RegionOrderIndex）。
 *
 * 2. `em1`（R8 混淆类，Calculator release）—— 曾触发 ModVisitor
 *    "Code variable not set" 的 `checkNotNull` 崩溃（上游同路径不崩）。
 *
 * 测试默认跳过（真实 APK 不入库），需指定 APK 目录：
 * ```
 * JADX_REAL_APKS=$PWD/tests/apks ./gradlew :jadx-cli:test --tests "*RealApkSingleClassTest*"
 * ```
 *
 * 断言口径：单类反编译必须在该时间预算内**完成**（挂死即超时失败），
 * 并允许存在基线内的方法级错误（RegionMaker 等上游既有限制，见 real-apk-baseline.properties）。
 */
class RealApkSingleClassTest {

	private data class Case(
		val apkName: String,
		val className: String,
		val maxSeconds: Long,
		val maxErrors: Int,
	)

	private val cases = listOf(
		// ProcessVariables O(n^2) 挂死回归：修复前 >20min，修复后 ~25s（含加载）
		Case("Notes-notes-13-foss-debug.apk", "androidx.compose.foundation.text.BasicTextFieldKt", 150, 50),
		// RegionMaker 区域爆炸回归：上游 Attempt two 伪汇聚候选把 outBlock 拉到分支块自身/中间块，
		// 修复前 >7min 超时（511 块产出 22.8 万区域节点），修复后 ~22s
		Case("Notes-notes-13-foss-debug.apk", "androidx.compose.foundation.text.CoreTextFieldKt", 150, 50),
		// ModVisitor "Code variable not set" 崩溃回归
		Case("Calculator-release-calculator-10-foss-release.apk", "em1", 120, 10),
	)

	@Test
	fun `known slow single classes decompile within time budget`() {
		val dir = System.getProperty("jadx.real.apks") ?: System.getenv("JADX_REAL_APKS")
		assumeTrue(dir != null && File(dir).isDirectory) {
			"set JADX_REAL_APKS (or -Djadx.real.apks) to a directory containing real *.apk"
		}
		val apkDir = File(dir!!)
		val failures = ArrayList<String>()
		for (case in cases) {
			val apk = File(apkDir, case.apkName)
			assumeTrue(apk.isFile) { "missing test APK: ${apk.absolutePath}" }
			val result = runSingleClass(apk, case.className, case.maxSeconds)
			if (result.timedOut) {
				failures += "${case.className}: TIMED OUT after ${case.maxSeconds}s (hang regression?)"
				continue
			}
			println(
				"RealApkSingleClass ${case.className}: ${result.durationMs}ms errors=${result.errors} " +
					"(budget ${case.maxSeconds}s / ${case.maxErrors})",
			)
			if (result.durationMs > TimeUnit.SECONDS.toMillis(case.maxSeconds)) {
				failures += "${case.className}: ${result.durationMs}ms > budget ${case.maxSeconds}s"
			}
			if (result.errors > case.maxErrors) {
				failures += "${case.className}: errors=${result.errors} > ${case.maxErrors}"
			}
		}
		assertThat(failures).`as`("single-class regression failures").isEmpty()
	}

	private fun runSingleClass(apk: File, className: String, maxSeconds: Long): SingleClassResult {
		val outDir = File(apk.parentFile.parentFile, "out/single-class-test").apply {
			deleteRecursively()
			mkdirs()
		}
		val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
		var errors = -1
		var timedOut = true
		val start = System.currentTimeMillis()
		try {
			val future = executor.submit<Any?> {
				val args = JadxArgs()
				args.inputFiles.add(apk)
				args.outDir = outDir
				args.classFilter = java.util.function.Predicate<String> { it == className }
				JadxDecompiler(args).use { jadx ->
					jadx.load()
					jadx.save()
					errors = jadx.getErrorsCount()
				}
				null
			}
			future.get(maxSeconds, TimeUnit.SECONDS)
			timedOut = false
		} catch (e: java.util.concurrent.TimeoutException) {
			timedOut = true
		} finally {
			executor.shutdownNow()
		}
		return SingleClassResult(timedOut, System.currentTimeMillis() - start, errors)
	}

	private data class SingleClassResult(val timedOut: Boolean, val durationMs: Long, val errors: Int)
}

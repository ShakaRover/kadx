package jadx.tests.external

import jadx.api.CommentsLevel
import jadx.api.JadxArgs
import jadx.api.JadxDecompiler
import jadx.api.JadxInternalAccess
import jadx.core.dex.nodes.ClassNode
import jadx.core.dex.nodes.MethodNode
import jadx.core.dex.nodes.RootNode
import jadx.core.utils.exceptions.JadxRuntimeException
import jadx.tests.api.utils.TestUtils
import org.assertj.core.api.Assertions.assertThat
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File

/**
 * 外部测试（`jadx-external-tests`）的公共基类。
 *
 * 原 Java 类为 `abstract`，这里保留全部 protected/public API，并保持可覆写成员为 `open`，
 * 以便仓库外的 Java 子类零改动继承。
 */
abstract class BaseExternalTest : TestUtils() {

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(BaseExternalTest::class.java)
	}

	/**
	 * 当前反编译器实例。用 `@JvmField` 保留与 Java 版本一致的 protected 字段，
	 * 供 Java 子类直接读写。
	 */
	@JvmField
	protected var decompiler: JadxDecompiler? = null

	protected abstract fun getSamplesDir(): String

	protected open fun prepare(inputFile: String): JadxArgs = prepare(File(getSamplesDir(), inputFile))

	protected open fun prepare(input: File): JadxArgs {
		val args = JadxArgs()
		args.inputFiles.add(input)
		args.outDir = File("../jadx-external-tests-tmp")
		args.isSkipFilesSave = true
		args.isSkipResources = true
		args.isShowInconsistentCode = true
		args.commentsLevel = CommentsLevel.DEBUG
		return args
	}

	protected open fun decompile(jadxArgs: JadxArgs): JadxDecompiler = decompile(jadxArgs, null, null)

	protected open fun decompile(jadxArgs: JadxArgs, clsPatternStr: String?): JadxDecompiler = decompile(jadxArgs, clsPatternStr, null)

	protected open fun decompile(jadxArgs: JadxArgs, clsPatternStr: String?, mthPatternStr: String?): JadxDecompiler {
		val jadx = JadxDecompiler(jadxArgs)
		decompiler = jadx
		jadx.load()

		if (clsPatternStr == null) {
			jadx.save()
		} else {
			processByPatterns(jadx, clsPatternStr, mthPatternStr)
		}
		printErrorReport(jadx)
		return jadx
	}

	private fun processByPatterns(jadx: JadxDecompiler, clsPattern: String, mthPattern: String?) {
		val root: RootNode = JadxInternalAccess.getRoot(jadx)
		var processed = 0
		for (classNode in root.getClasses(true)) {
			val clsFullName = classNode.classInfo.fullName
			if (clsFullName == clsPattern) {
				if (processCls(mthPattern, classNode)) {
					processed++
				}
			}
		}
		assertThat(processed).`as`("No classes processed").isGreaterThan(0)
	}

	private fun processCls(mthPattern: String?, classNode: ClassNode): Boolean {
		classNode.load()
		var decompile = false
		if (mthPattern == null) {
			decompile = true
		} else {
			for (mth in classNode.methods) {
				if (isMthMatch(mth, mthPattern)) {
					decompile = true
					break
				}
			}
		}
		if (!decompile) {
			return false
		}
		try {
			classNode.decompile()
		} catch (e: Exception) {
			throw JadxRuntimeException("Class process failed", e)
		}
		LOG.info("----------------------------------------------------------------")
		LOG.info("Print class: {} from: {}", classNode.fullName, classNode.inputFileName)
		if (mthPattern != null) {
			printMethods(classNode, mthPattern)
		} else {
			LOG.info("Code: \n{}", classNode.getCode())
		}
		TestUtils.checkCode(classNode, false)
		return true
	}

	private fun isMthMatch(mth: MethodNode, mthPattern: String): Boolean {
		val shortId = mth.methodInfo.shortId
		return isMatch(shortId, mthPattern)
	}

	private fun isMatch(str: String, pattern: String): Boolean {
		if (str == pattern) {
			return true
		}
		return str.startsWith(pattern)
	}

	private fun printMethods(classNode: ClassNode, mthPattern: String) {
		val dashLine = "======================================================================================"
		for (mth in classNode.methods) {
			if (isMthMatch(mth, mthPattern)) {
				LOG.info(
					"Print method: {}\n{}\n{}\n{}",
					mth.methodInfo.rawFullId,
					dashLine,
					mth.codeStr,
					dashLine,
				)
			}
		}
	}

	private fun printErrorReport(jadx: JadxDecompiler) {
		jadx.printErrorsReport()
		assertThat(jadx.getErrorsCount()).isEqualTo(0)
	}
}

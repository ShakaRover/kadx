package jadx.api

import jadx.core.utils.exceptions.JadxArgsValidateException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File

/**
 * 启动参数校验器：在真正加载输入文件之前检查参数是否合法，并补齐输出目录。
 *
 * 公共 API：jadx-cli / jadx-gui 通过 `JadxArgsValidator.validate(jadx)` 调用。
 * 用 Kotlin `object` 单例 + `@JvmStatic` 保持 Java 静态调用写法不变（原类只有静态方法、私有构造器）。
 */
object JadxArgsValidator {
	private val LOG: Logger = LoggerFactory.getLogger(JadxArgsValidator::class.java)

	/** 校验参数；不合法时抛出 [JadxArgsValidateException]。 */
	@JvmStatic
	fun validate(jadx: JadxDecompiler) {
		val args = jadx.getArgs()
		checkInputFiles(jadx, args)
		validateOutDirs(args)

		if (LOG.isDebugEnabled) {
			LOG.debug("Effective jadx args: {}", args)
		}
	}

	private fun checkInputFiles(jadx: JadxDecompiler, args: JadxArgs) {
		val inputFiles = args.inputFiles
		if (inputFiles.isEmpty() && jadx.getCustomCodeLoaders().isEmpty()) {
			throw JadxArgsValidateException("Please specify input file")
		}
		for (file in inputFiles) {
			checkFile(file)
		}
	}

	private fun validateOutDirs(args: JadxArgs) {
		var outDir = args.outDir
		val srcDir = args.outDirSrc
		val resDir = args.outDirRes
		if (outDir == null) {
			outDir = when {
				srcDir != null -> srcDir
				resDir != null -> resDir
				else -> makeDirFromInput(args)
			}
			args.outDir = outDir
		}
		if (srcDir == null) {
			args.outDirSrc = File(args.outDir, JadxArgs.DEFAULT_SRC_DIR)
		}
		if (resDir == null) {
			args.outDirRes = File(args.outDir, JadxArgs.DEFAULT_RES_DIR)
		}

		checkDir(args.outDir, "Output")
		checkDir(args.outDirSrc, "Source output")
		checkDir(args.outDirRes, "Resources output")
	}

	private fun makeDirFromInput(args: JadxArgs): File {
		val outDirName: String
		val inputFiles = args.inputFiles
		if (inputFiles.isEmpty()) {
			outDirName = JadxArgs.DEFAULT_OUT_DIR
		} else {
			val file = inputFiles[0]
			val name = file.name
			val pos = name.lastIndexOf('.')
			outDirName = if (pos != -1) {
				name.substring(0, pos)
			} else {
				name + '-' + JadxArgs.DEFAULT_OUT_DIR
			}
		}
		LOG.info("output directory: {}", outDirName)
		return File(outDirName)
	}

	private fun checkFile(file: File) {
		if (!file.exists()) {
			throw JadxArgsValidateException("File not found " + file.absolutePath)
		}
	}

	private fun checkDir(dir: File?, desc: String) {
		if (dir != null && dir.exists() && !dir.isDirectory) {
			throw JadxArgsValidateException(desc + " directory exists as file " + dir)
		}
	}
}

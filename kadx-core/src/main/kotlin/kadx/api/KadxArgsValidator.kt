package kadx.api

import kadx.core.utils.exceptions.KadxArgsValidateException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File

/**
 * 启动参数校验器：在真正加载输入文件之前检查参数是否合法，并补齐输出目录。
 *
 * 公共 API：kadx-cli / kadx-gui 通过 `KadxArgsValidator.validate(kadx)` 调用。
 * 用 Kotlin `object` 单例 + `@JvmStatic` 保持 Java 静态调用写法不变（原类只有静态方法、私有构造器）。
 */
object KadxArgsValidator {
	private val LOG: Logger = LoggerFactory.getLogger(KadxArgsValidator::class.java)

	/** 校验参数；不合法时抛出 [KadxArgsValidateException]。 */
	@JvmStatic
	fun validate(kadx: KadxDecompiler) {
		val args = kadx.getArgs()
		checkInputFiles(kadx, args)
		validateOutDirs(args)

		if (LOG.isDebugEnabled) {
			LOG.debug("Effective kadx args: {}", args)
		}
	}

	private fun checkInputFiles(kadx: KadxDecompiler, args: KadxArgs) {
		val inputFiles = args.inputFiles
		if (inputFiles.isEmpty() && kadx.getCustomCodeLoaders().isEmpty()) {
			throw KadxArgsValidateException("Please specify input file")
		}
		for (file in inputFiles) {
			checkFile(file)
		}
	}

	private fun validateOutDirs(args: KadxArgs) {
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
			args.outDirSrc = File(args.outDir, KadxArgs.DEFAULT_SRC_DIR)
		}
		if (resDir == null) {
			args.outDirRes = File(args.outDir, KadxArgs.DEFAULT_RES_DIR)
		}

		checkDir(args.outDir, "Output")
		checkDir(args.outDirSrc, "Source output")
		checkDir(args.outDirRes, "Resources output")
	}

	private fun makeDirFromInput(args: KadxArgs): File {
		val outDirName: String
		val inputFiles = args.inputFiles
		if (inputFiles.isEmpty()) {
			outDirName = KadxArgs.DEFAULT_OUT_DIR
		} else {
			val file = inputFiles[0]
			val name = file.name
			val pos = name.lastIndexOf('.')
			outDirName = if (pos != -1) {
				name.substring(0, pos)
			} else {
				name + '-' + KadxArgs.DEFAULT_OUT_DIR
			}
		}
		LOG.info("output directory: {}", outDirName)
		return File(outDirName)
	}

	private fun checkFile(file: File) {
		if (!file.exists()) {
			throw KadxArgsValidateException("File not found " + file.absolutePath)
		}
	}

	private fun checkDir(dir: File?, desc: String) {
		if (dir != null && dir.exists() && !dir.isDirectory) {
			throw KadxArgsValidateException(desc + " directory exists as file " + dir)
		}
	}
}

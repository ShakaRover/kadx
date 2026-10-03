package jadx.cli

import jadx.api.ICodeInfo
import jadx.api.JadxDecompiler
import jadx.core.dex.attributes.AFlag
import jadx.core.dex.nodes.ClassNode
import jadx.core.dex.visitors.SaveCode
import jadx.core.utils.exceptions.JadxArgsValidateException
import jadx.core.utils.exceptions.JadxRuntimeException
import jadx.core.utils.files.FileUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File

/**
 * “单类模式”处理：只反编译并保存用户指定的一个类。
 *
 * **做什么**：当 `--single-class` 或 `--single-class-output` 被指定时，找到目标类，
 * 反编译后按约定路径写出单个源码文件；否则返回 `false` 表示走正常全量保存流程。
 *
 * **为什么这样写**：原 Java 是静态工具方法，调用方以 `SingleClassMode.process(...)`
 * 调用，因此放入 `companion object`。
 */
class SingleClassMode {

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(SingleClassMode::class.java)

		/**
		 * 执行单类模式。
		 *
		 * @return 是否处理了单类（true 表示无需再走全量保存）
		 */
		fun process(jadx: JadxDecompiler, cliArgs: JadxCLIArgs): Boolean {
			val singleClass = cliArgs.singleClass
			val singleClassOutput = cliArgs.singleClassOutput
			if (singleClass == null && singleClassOutput == null) {
				return false
			}
			val root = checkNotNull(jadx.getRoot())
			val clsForProcess: ClassNode
			if (singleClass != null) {
				var cls = root.resolveClass(singleClass)
				if (cls == null) {
					// 按“别名全名”再找一次
					cls = root.getClasses().firstOrNull { it.classInfo.aliasFullName == singleClass }
				}
				if (cls == null) {
					throw JadxArgsValidateException("Input class not found: $singleClass")
				}
				if (AFlag.DONT_GENERATE in cls) {
					throw JadxArgsValidateException("Input class can't be saved by current jadx settings (marked as DONT_GENERATE)")
				}
				if (cls.isInner()) {
					cls = cls.topParentClass
					LOG.warn("Input class is inner, parent class will be saved: {}", cls.fullName)
				}
				clsForProcess = cls
			} else {
				// 只指定了 singleClassOutput：要求当前只加载了一个可生成的类
				val classes = root.getClasses().filter { !it.isInner() && AFlag.DONT_GENERATE !in it }
				val size = classes.size
				if (size == 1) {
					clsForProcess = classes[0]
				} else {
					throw JadxArgsValidateException("Found $size classes, single class output can't be used")
				}
			}
			val codeInfo: ICodeInfo
			try {
				codeInfo = clsForProcess.decompile()
			} catch (e: Exception) {
				throw JadxRuntimeException("Class decompilation failed", e)
			}
			val fileExt = SaveCode.getFileExtension(root)
			val out: File
			if (singleClassOutput == null) {
				out = File(jadx.getArgs().outDirSrc, clsForProcess.classInfo.aliasFullPath + fileExt)
			} else {
				if (singleClassOutput.endsWith(fileExt)) {
					// 视为文件名
					out = File(singleClassOutput)
				} else {
					// 视为目录
					out = File(singleClassOutput, clsForProcess.shortName + fileExt)
				}
			}
			val resultOut = FileUtils.prepareFile(out)
			if (clsForProcess.classInfo.hasAlias()) {
				LOG.info(
					"Saving class '{}' (alias: '{}') to file '{}'",
					clsForProcess.classInfo.fullName,
					clsForProcess.fullName,
					resultOut.absolutePath,
				)
			} else {
				LOG.info("Saving class '{}' to file '{}'", clsForProcess.fullName, resultOut.absolutePath)
			}
			SaveCode.save(codeInfo.getCodeStr(), resultOut)
			return true
		}
	}
}

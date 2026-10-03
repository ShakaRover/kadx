package jadx.core.dex.visitors

import jadx.api.ICodeInfo
import jadx.api.JadxArgs
import jadx.core.dex.attributes.AFlag
import jadx.core.dex.nodes.ClassNode
import jadx.core.dex.nodes.RootNode
import jadx.core.utils.exceptions.JadxRuntimeException
import jadx.core.utils.files.FileUtils
import org.slf4j.LoggerFactory
import java.io.File
import java.io.PrintWriter
import java.nio.charset.StandardCharsets

/**
 * 把生成的代码写盘的工具类。
 *
 * **做什么**：提供把 [ICodeInfo] / 字符串保存到文件的静态方法，并根据输出格式决定扩展名。
 * 会跳过标记为 [AFlag.DONT_GENERATE] 的类、空代码、以及用户关闭保存的场景。
 *
 * **为什么用 `object` 单例**：原 Java 类只有私有构造器 + 全静态方法，Kotlin 用 `object`
 * 最自然；`@JvmStatic` 保证 Java 侧 `SaveCode.save(...)`、`SaveCode.getFileExtension(...)`
 * 等调用完全不变（jadx-cli / jadx-gui 仍按静态方式调用）。
 */
object SaveCode {
	private val LOG = LoggerFactory.getLogger(SaveCode::class.java)

	fun save(dir: File, cls: ClassNode, code: ICodeInfo?) {
		if (cls.contains(AFlag.DONT_GENERATE)) {
			return
		}
		if (code == null) {
			throw JadxRuntimeException("Code not generated for class " + cls.fullName)
		}
		// Java 里用 == 比较引用，这里必须保持 ===（ICodeInfo.EMPTY 是共享单例）
		if (code === ICodeInfo.EMPTY) {
			return
		}
		val codeStr = code.getCodeStr()
		if (codeStr.isEmpty()) {
			return
		}
		val args = cls.root().getArgs()
		if (args.isSkipFilesSave) {
			return
		}
		val fileName = cls.classInfo.aliasFullPath + getFileExtension(cls.root())
		if (!args.security.isValidEntryName(fileName)) {
			return
		}
		save(codeStr, File(dir, fileName))
	}

	fun save(codeInfo: ICodeInfo, file: File) {
		save(codeInfo.getCodeStr(), file)
	}

	fun save(code: String, file: File) {
		val outFile = FileUtils.prepareFile(file)
		try {
			PrintWriter(outFile, StandardCharsets.UTF_8).use { out ->
				out.println(code)
			}
		} catch (e: Exception) {
			LOG.error("Save file error", e)
		}
	}

	fun getFileExtension(root: RootNode): String {
		val outputFormat = root.getArgs().outputFormat
		return when (outputFormat) {
			JadxArgs.OutputFormatEnum.JAVA -> ".java"
			JadxArgs.OutputFormatEnum.JSON -> ".json"
			else -> throw JadxRuntimeException("Unknown output format: $outputFormat")
		}
	}
}

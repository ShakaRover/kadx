package kadx.api

import kadx.core.deobf.FileTypeDetector
import kadx.core.utils.StringUtils
import kadx.core.utils.exceptions.KadxException
import kadx.core.xmlgen.ResContainer
import kadx.core.xmlgen.entry.ResourceEntry
import kadx.zip.IZipEntry
import java.io.File

/**
 * 资源文件：kadx 在输出资源目录时使用它表示一个待保存的资源。
 *
 * 这是公共 API（kadx-cli / kadx-gui / 插件都会使用），因此：
 * - 静态工厂 [createResourceFile] 放在 companion 并用 `@JvmStatic`，Java 写法保持不变；
 * - 所有 getter/setter 保留显式函数形态，JVM 方法名与原来一致；
 * - `decompiler` 允许为 null（[ResourceFileContainer] / [ResourceFileContent] 会传入 null）。
 */
open class ResourceFile protected constructor(
	private val decompiler: KadxDecompiler?,
	private val name: String,
	private var type: ResourceType,
) {
	private var zipEntry: IZipEntry? = null
	private var deobfName: String? = null

	/** 原始资源名（未去混淆）。 */
	fun getOriginalName(): String = name

	/** 去混淆后的资源名；未设置时回退到原始名。 */
	fun getDeobfName(): String = deobfName ?: name

	/** 设置去混淆后的资源名。 */
	fun setDeobfName(resFullName: String) {
		this.deobfName = resFullName
	}

	/** 资源类型。 */
	fun getType(): ResourceType = type

	/** 加载资源内容（默认通过 [ResourcesLoader] 解码）。子类可覆写以直接返回已知内容。 */
	open fun loadContent(): ResContainer {
		// 基类的 loadContent 只会在 decompiler 非空时被调用（两个子类都覆写了该方法），
		// 这里用 checkNotNull 保持与原 Java 解引用 null 时抛 NPE 的行为一致。
		return ResourcesLoader.loadContent(checkNotNull(decompiler), this)
	}

	/**
	 * 根据资源表条目为资源设置别名（去混淆名）。
	 *
	 * @param entry      资源表条目，提供类型/配置/键名
	 * @param useHeaders 是否读取文件头来推断真实扩展名（更准确，但需要读取内容）
	 * @return 别名与原名称不同（即确实发生重命名）时返回 true
	 */
	fun setAlias(entry: ResourceEntry, useHeaders: Boolean): Boolean {
		val sb = StringBuilder()
		sb.append("res/").append(entry.typeName).append(entry.config)
		sb.append("/").append(entry.keyName)

		if (useHeaders) {
			try {
				val maxBytesToReadLimit = 4096
				val bytes = ResourcesLoader.decodeStream(this) { size, inputStream ->
					val bytesToRead: Int = when {
						size > 0 -> Math.min(size, maxBytesToReadLimit.toLong()).toInt()
						size == 0L -> 0
						else -> maxBytesToReadLimit
					}
					if (bytesToRead == 0) {
						ByteArray(0)
					} else {
						inputStream.readNBytes(bytesToRead)
					}
				}

				val fileExtension = FileTypeDetector.detectFileExtension(bytes)
				if (!StringUtils.isEmpty(fileExtension)) {
					sb.append(fileExtension)
				} else {
					sb.append(getExtFromName(name))
				}
			} catch (ignored: KadxException) {
				// 读取文件头失败时忽略，继续使用按名称推断的扩展名
			}
		} else {
			sb.append(getExtFromName(name))
		}
		val alias = sb.toString()
		if (alias != name) {
			setDeobfName(alias)
			type = ResourceType.getFileType(alias)
			return true
		}
		return false
	}

	/**
	 * 从名称中截取扩展名。
	 *
	 * 特殊处理 `.9.png`（aapt2 资源压缩时始终保留该扩展名）。
	 */
	private fun getExtFromName(name: String): String {
		// 九宫格图片 .9.png 的扩展名始终保留
		if (name.contains(".9.png")) {
			return ".9.png"
		}
		val lastDot = name.lastIndexOf('.')
		if (lastDot != -1) {
			return name.substring(lastDot)
		}
		return ""
	}

	/** 关联的 zip 条目（若资源来自压缩包）。 */
	fun getZipEntry(): IZipEntry? = zipEntry

	/** 设置关联的 zip 条目（包内使用）。 */
	internal fun setZipEntry(zipEntry: IZipEntry?) {
		this.zipEntry = zipEntry
	}

	/** 关联的反编译器实例（可能为 null）。 */
	fun getDecompiler(): KadxDecompiler? = decompiler

	override fun toString(): String = "ResourceFile{name='$name', type=$type}"

	companion object {
		/** 由磁盘文件创建资源文件。 */
		@JvmStatic
		fun createResourceFile(decompiler: KadxDecompiler, file: File, type: ResourceType): ResourceFile = ResourceFile(decompiler, file.absolutePath, type)

		/**
		 * 由名称创建资源文件。
		 *
		 * 若名称未通过安全校验（路径穿越等），返回 null，调用方需判空。
		 */
		@JvmStatic
		fun createResourceFile(decompiler: KadxDecompiler, name: String, type: ResourceType): ResourceFile? {
			if (!decompiler.getArgs().security.isValidEntryName(name)) {
				return null
			}
			return ResourceFile(decompiler, name, type)
		}
	}
}

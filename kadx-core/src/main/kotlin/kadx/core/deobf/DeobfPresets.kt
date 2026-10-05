package kadx.core.deobf

import kadx.api.KadxArgs
import kadx.api.args.GeneratedRenamesMappingFileMode
import kadx.api.deobf.IAliasProvider
import kadx.api.deobf.impl.AlwaysRename
import kadx.core.dex.info.ClassInfo
import kadx.core.dex.info.FieldInfo
import kadx.core.dex.info.MethodInfo
import kadx.core.dex.nodes.ClassNode
import kadx.core.dex.nodes.FieldNode
import kadx.core.dex.nodes.MethodNode
import kadx.core.dex.nodes.PackageNode
import kadx.core.dex.nodes.RootNode
import kadx.core.utils.files.FileUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.Collections
import java.util.regex.Pattern

/**
 * 反混淆映射（presets）文件读写。
 *
 * **用途**：把“原名 → 别名”的对应关系保存到磁盘（`.jobf` 文件），下次反编译同一 APK
 * 时直接复用，保证多次运行得到**稳定的名字**。
 *
 * 映射文件按行存储，格式为 `<类型> <原名> = <别名>`：
 * - `p` 包、`c` 类、`f` 字段、`m` 方法；`v` 为已废弃类型（忽略）。
 * - `#` 开头的行是注释。
 *
 * 本类持有四张映射表，并提供 [load]（读）、[save]（写）、[fill]（从 dex 树收集）、
 * [apply]（应用到 dex 树）等操作。
 */
class DeobfPresets private constructor(val deobfMapFile: Path) {

	val pkgPresetMap = HashMap<String, String>()
	val clsPresetMap = HashMap<String, String>()
	val fldPresetMap = HashMap<String, String>()
	val mthPresetMap = HashMap<String, String>()

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(DeobfPresets::class.java)

		private val MAP_FILE_CHARSET: Charset = StandardCharsets.UTF_8

		/** 构建 [DeobfPresets]：确定映射文件路径并返回实例。 */
		fun build(root: RootNode): DeobfPresets {
			val deobfMapPath = getPathDeobfMapPath(root)
			if (root.args.generatedRenamesMappingFileMode != GeneratedRenamesMappingFileMode.IGNORE) {
				LOG.debug("Deobfuscation map file set to: {}", deobfMapPath)
			}
			return DeobfPresets(deobfMapPath)
		}

		/** 计算映射文件路径：优先用参数指定，否则放在输入文件同目录下的 `<基名>.jobf`。 */
		private fun getPathDeobfMapPath(root: RootNode): Path {
			val kadxArgs: KadxArgs = root.args
			val deobfMapFile = kadxArgs.generatedRenamesMappingFile
			if (deobfMapFile != null) {
				return deobfMapFile.toPath()
			}
			val inputFilePath = kadxArgs.inputFiles[0].toPath().toAbsolutePath()
			val baseName = FileUtils.getPathBaseName(inputFilePath)
			return inputFilePath.parent.resolve("$baseName.jobf")
		}

		/**
		 * 拆分 `p 原名 = 别名` 形式的行：去掉前两个字符（类型 + 空格）后按 `=` 切分。
		 *
		 * 这里用 Java 的 [Pattern.split]，与原始 `String.split` 一样会**丢弃末尾空串**。
		 */
		private fun splitAndTrim(str: String): Array<String> {
			val v = Pattern.compile("=").split(str.substring(2))
			for (i in v.indices) {
				v[i] = v[i].trim()
			}
			return v
		}
	}

	/**
	 * 从映射文件加载预设；文件不存在时返回 `false`，读取失败时记录错误并返回 `false`。
	 */
	fun load(): Boolean {
		if (!Files.exists(deobfMapFile)) {
			return false
		}
		LOG.info("Loading obfuscation map from: {}", deobfMapFile.toAbsolutePath())
		try {
			val lines = Files.readAllLines(deobfMapFile, MAP_FILE_CHARSET)
			for (rawLine in lines) {
				val line = rawLine.trim()
				if (line.isEmpty() || line.startsWith("#")) {
					continue
				}
				val va = splitAndTrim(line)
				if (va.size != 2) {
					continue
				}
				val origName = va[0]
				val alias = va[1]
				when (line[0]) {
					'p' -> pkgPresetMap[origName] = alias
					'c' -> clsPresetMap[origName] = alias
					'f' -> fldPresetMap[origName] = alias
					'm' -> mthPresetMap[origName] = alias
					'v' -> {} // 已废弃类型，忽略
				}
			}
			return true
		} catch (e: Exception) {
			LOG.error("Failed to load deobfuscation map file '{}'", deobfMapFile.toAbsolutePath(), e)
			return false
		}
	}

	/** 把四张映射表写入文件（排序后输出，保证内容稳定）；映射为空时跳过。 */
	@Throws(IOException::class)
	fun save() {
		val list = ArrayList<String>()
		for ((key, value) in pkgPresetMap) {
			list.add("p $key = $value")
		}
		for ((key, value) in clsPresetMap) {
			list.add("c $key = $value")
		}
		for ((key, value) in fldPresetMap) {
			list.add("f $key = $value")
		}
		for ((key, value) in mthPresetMap) {
			list.add("m $key = $value")
		}
		Collections.sort(list)
		if (list.isEmpty()) {
			if (LOG.isDebugEnabled) {
				LOG.debug("Deobfuscation map is empty, not saving it")
			}
			return
		}
		Files.write(
			deobfMapFile,
			list,
			MAP_FILE_CHARSET,
			StandardOpenOption.WRITE,
			StandardOpenOption.CREATE,
			StandardOpenOption.TRUNCATE_EXISTING,
		)
		LOG.info("Deobfuscation map file saved as: {}", deobfMapFile)
	}

	/** 从 dex 树中收集所有已重命名的节点，填入映射表。 */
	fun fill(root: RootNode) {
		for (pkg in root.getPackages()) {
			if (pkg.isLeaf()) { // 忽略中间包
				if (pkg.hasParentAlias()) {
					pkgPresetMap[pkg.getPkgInfo().fullName] = pkg.getAliasPkgInfo().fullName
				} else if (pkg.hasAlias()) {
					pkgPresetMap[pkg.getPkgInfo().fullName] = pkg.getAliasPkgInfo().name
				}
			}
		}
		for (cls in root.getClasses()) {
			val classInfo = cls.classInfo
			if (classInfo.hasAlias()) {
				clsPresetMap[classInfo.makeRawFullName()] = classInfo.aliasShortName
			}
			for (fld in cls.fields) {
				val fieldInfo = fld.getFieldInfo()
				if (fieldInfo.hasAlias()) {
					fldPresetMap[fieldInfo.rawFullId] = fld.alias
				}
			}
			for (mth in cls.methods) {
				val methodInfo = mth.methodInfo
				if (methodInfo.hasAlias()) {
					mthPresetMap[methodInfo.rawFullId] = methodInfo.alias
				}
			}
		}
	}

	/** 把映射表应用到 dex 树：用 [AlwaysRename] 条件强制重命名，别名取自映射表。 */
	fun apply(root: RootNode) {
		DeobfuscatorVisitor.process(
			root,
			AlwaysRename.INSTANCE,
			object : IAliasProvider {
				override fun forPackage(pkg: PackageNode): String? = pkgPresetMap[pkg.getPkgInfo().fullName]

				override fun forClass(cls: ClassNode): String? = getForCls(cls.classInfo)

				override fun forField(fld: FieldNode): String? = getForFld(fld.getFieldInfo())

				override fun forMethod(mth: MethodNode): String? = getForMth(mth.methodInfo)
			},
		)
	}

	/** 把已有映射的规模同步给别名提供者，避免新名字与已加载的名字冲突。 */
	fun initIndexes(aliasProvider: IAliasProvider) {
		aliasProvider.initIndexes(pkgPresetMap.size, clsPresetMap.size, fldPresetMap.size, mthPresetMap.size)
	}

	fun getForCls(cls: ClassInfo): String? {
		if (clsPresetMap.isEmpty()) {
			return null
		}
		return clsPresetMap[cls.makeRawFullName()]
	}

	fun getForFld(fld: FieldInfo): String? {
		if (fldPresetMap.isEmpty()) {
			return null
		}
		return fldPresetMap[fld.rawFullId]
	}

	fun getForMth(mth: MethodInfo): String? {
		if (mthPresetMap.isEmpty()) {
			return null
		}
		return mthPresetMap[mth.rawFullId]
	}

	fun clear() {
		pkgPresetMap.clear()
		clsPresetMap.clear()
		fldPresetMap.clear()
		mthPresetMap.clear()
	}
}

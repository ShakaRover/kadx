package kadx.core.dex.info

import kadx.core.dex.instructions.args.ArgType
import kadx.core.dex.nodes.ClassNode
import kadx.core.dex.nodes.RootNode
import kadx.core.utils.StringUtils
import kadx.core.utils.exceptions.KadxRuntimeException
import java.io.File

/**
 * 类的元信息（ClassInfo）。
 *
 * 它缓存了一个类的：原始类型 [type]、短名、包名、完整名、外部类以及可选的别名
 * （反混淆后名字）。注意它与 AST 节点 [ClassNode] 不同，[ClassInfo] 只描述名字，
 * 不持有方法/字段等图结构，因此可以安全地作为缓存 key。
 *
 * Kotlin 转换说明：
 * - 本类在 Java 中重写了 `equals`/`hashCode`（按 [type] 判等），Kotlin 侧原样保留，
 *   不使用 `data class`，避免改变既有判等语义；
 * - `getXxx()` 简单访问器改为属性，JVM 访问器名与原 Java 一致；
 * - `getPackage()` 因 `package` 是 Kotlin 关键字，保留为显式函数；
 * - 静态工厂方法放入 companion 并标注 `@JvmStatic`。
 */
class ClassInfo private constructor(
	root: RootNode,
	val type: ArgType,
	canBeInner: Boolean,
) : Comparable<ClassInfo> {

	/** 短类名（不含包名） */
	var shortName: String = ""
		private set

	/** 完整类名（含包名；内部类用 '.' 连接） */
	var fullName: String = ""
		private set

	/** 包名；内部类为 null（包名需从外部类获取） */
	private var pkg: String? = null

	/** 外部类；顶层类为 null */
	var parentClass: ClassInfo? = null
		private set

	/** 别名信息；无别名时为 null */
	private var alias: ClassAliasInfo? = null

	init {
		splitAndApplyNames(root, type, canBeInner)
	}

	companion object {
		/** 从 [ArgType] 获取（或创建）[ClassInfo]，并写入缓存。 */
		fun fromType(root: RootNode, type: ArgType): ClassInfo {
			val clsType = checkClassType(type)
			val cls = root.infoStorage.getCls(clsType)
			if (cls != null) {
				return cls
			}
			val canBeInner = root.getArgs().isMoveInnerClasses
			val newClsInfo = ClassInfo(root, clsType, canBeInner)
			return root.infoStorage.putCls(newClsInfo)
		}

		/** 按完整类名获取（或创建）[ClassInfo]。 */
		fun fromName(root: RootNode, clsName: String): ClassInfo = fromType(root, ArgType.`object`(clsName))

		/** 不走缓存，直接构造一个新的 [ClassInfo]（用于重命名等场景）。 */
		fun fromNameWithoutCache(root: RootNode, fullClsName: String, canBeInner: Boolean): ClassInfo = ClassInfo(root, ArgType.`object`(fullClsName), canBeInner)

		/** 校验并规范化类类型：数组退化为 Object，泛型对象去掉泛型参数。 */
		private fun checkClassType(type: ArgType): ArgType {
			if (type.isArray()) {
				// TODO: 处理在数组类中声明的方法（如 int[] 的 clone）
				return ArgType.OBJECT
			}
			if (!type.isObject() || type.isGenericType()) {
				throw KadxRuntimeException("Not class type: $type")
			}
			if (type.isGeneric()) {
				return ArgType.`object`(type.getObject())
			}
			return type
		}

		/**
		 * 拼接完整类名。
		 *
		 * @param pkg         包名（顶层类用）
		 * @param shortName   短名
		 * @param parentClass 外部类（内部类用）
		 * @param alias       是否使用别名
		 * @param raw         是否使用原始内部类分隔符 '$'（否则用 '.'）
		 */
		private fun makeFullClsName(pkg: String?, shortName: String, parentClass: ClassInfo?, alias: Boolean, raw: Boolean): String {
			if (parentClass != null) {
				val parentFullName: String
				val innerSep = if (raw) '$' else '.'
				parentFullName = if (alias) {
					if (raw) parentClass.makeAliasRawFullName() else parentClass.aliasFullName
				} else {
					if (raw) parentClass.makeRawFullName() else parentClass.fullName
				}
				return parentFullName + innerSep + shortName
			}
			val pkgName = checkNotNull(pkg)
			return if (pkgName.isEmpty()) shortName else "$pkgName.$shortName"
		}
	}

	/**
	 * 修改短名（重命名）。
	 *
	 * 如果新名字与原名相同且包名也未变，则清除别名；否则创建/更新别名对象。
	 */
	fun changeShortName(aliasName: String?) {
		val newAlias: ClassAliasInfo?
		val aliasPkgName = aliasPkg
		if (shortName == aliasName || StringUtils.isEmpty(aliasName)) {
			if (getPackage() == aliasPkgName) {
				newAlias = null
			} else {
				newAlias = ClassAliasInfo(aliasPkgName, shortName)
			}
		} else {
			newAlias = ClassAliasInfo(aliasPkgName, checkNotNull(aliasName))
		}
		if (newAlias != null) {
			fillAliasFullName(newAlias)
		}
		this.alias = newAlias
	}

	/** 修改包名（仅限顶层类）。 */
	fun changePkg(newPkg: String) {
		if (isInner) {
			throw KadxRuntimeException("Can't change package for inner class: $this")
		}
		if (aliasPkg != newPkg) {
			val newAlias = ClassAliasInfo(newPkg, aliasShortName)
			fillAliasFullName(newAlias)
			this.alias = newAlias
		}
	}

	/** 同时修改包名与短名（仅限顶层类）。 */
	fun changePkgAndName(newPkg: String, newShortName: String) {
		if (isInner) {
			throw KadxRuntimeException("Can't change package for inner class")
		}
		val newAlias = ClassAliasInfo(newPkg, newShortName)
		fillAliasFullName(newAlias)
		this.alias = newAlias
	}

	/** 为顶层类别名补齐完整名（内部类的完整名在访问时动态拼接）。 */
	private fun fillAliasFullName(alias: ClassAliasInfo) {
		if (parentClass == null) {
			alias.fullName = makeFullClsName(alias.pkg, alias.shortName, null, true, false)
		}
	}

	/** 别名包名：内部类跟随外部类；无别名时退回原包名。 */
	val aliasPkg: String
		get() {
			if (isInner) {
				return checkNotNull(parentClass).aliasPkg
			}
			return alias?.pkg ?: getPackage()
		}

	/** 别名短名；无别名时退回原短名。 */
	val aliasShortName: String
		get() = alias?.shortName ?: shortName

	/** 别名完整名；无别名时退回原完整名。 */
	val aliasFullName: String
		get() {
			val a = alias
			if (a != null) {
				return a.fullName ?: makeAliasFullName()
			}
			val pc = parentClass
			if (pc != null && pc.hasAlias()) {
				return makeAliasFullName()
			}
			return fullName
		}

	/** 是否存在别名（自身或任一外部类）。 */
	fun hasAlias(): Boolean {
		val a = alias
		if (a != null && a.shortName != shortName) {
			return true
		}
		return parentClass?.hasAlias() ?: false
	}

	/** 别名包名是否与原包名不同。 */
	fun hasAliasPkg(): Boolean = getPackage() != aliasPkg

	/** 清除别名。 */
	fun removeAlias() {
		alias = null
	}

	private fun makeFullName(): String = makeFullClsName(pkg, shortName, parentClass, false, false)

	/** 原始完整名（内部类用 '$' 分隔，符合字节码/DEX 命名）。 */
	fun makeRawFullName(): String = makeFullClsName(pkg, shortName, parentClass, false, true)

	/** 用别名拼接完整名。 */
	fun makeAliasFullName(): String = makeFullClsName(aliasPkg, aliasShortName, parentClass, true, false)

	/** 用别名拼接原始完整名（'$' 分隔）。 */
	fun makeAliasRawFullName(): String = makeFullClsName(aliasPkg, aliasShortName, parentClass, true, true)

	/** 别名对应的输出文件路径（包名转目录分隔符）。 */
	val aliasFullPath: String
		get() {
			val fileName = aliasNameWithoutPackage.replace('.', '_')
			val aliasPkgName = aliasPkg
			if (aliasPkgName.isEmpty()) {
				return fileName
			}
			return aliasPkgName.replace('.', File.separatorChar) + File.separatorChar + fileName
		}

	/**
	 * 返回包名。
	 *
	 * 注意：`package` 是 Kotlin 硬关键字，无法声明为属性，因此保留显式函数，
	 * JVM 方法名 `getPackage()` 与原 Java 一致。
	 */
	fun getPackage(): String {
		val pc = parentClass
		if (pc != null) {
			return pc.getPackage()
		}
		return pkg ?: throw KadxRuntimeException("Package is null for not inner class")
	}

	fun isDefaultPackage(): Boolean = getPackage().isEmpty()

	/** 原始类型名（如 `Ljava/lang/String;`）。 */
	val rawName: String get() = type.getObject()

	/** 不含包名的别名（内部类用 '.' 连接外部类别名）。 */
	val aliasNameWithoutPackage: String
		get() {
			val pc = parentClass
			if (pc == null) {
				return aliasShortName
			}
			return pc.aliasNameWithoutPackage + '.' + aliasShortName
		}

	/** 最外层的外部类；顶层类返回 null。 */
	val topParentClass: ClassInfo?
		get() {
			val pc = parentClass
			if (pc != null) {
				val topCls = pc.topParentClass
				return topCls ?: pc
			}
			return null
		}

	/** 是否为内部类。 */
	val isInner: Boolean get() = parentClass != null

	/** 强制按顶层类重新解析名字。 */
	fun notInner(root: RootNode) {
		splitAndApplyNames(root, type, false)
		parentClass = null
	}

	/** 转为内部类，外部类为 [parent]。 */
	fun convertToInner(parent: ClassNode) {
		splitAndApplyNames(parent.root(), type, true)
		parentClass = parent.classInfo
	}

	/** 按当前内部类状态刷新名字（类型不变时重命名后调用）。 */
	fun updateNames(root: RootNode) {
		splitAndApplyNames(root, type, isInner)
	}

	/**
	 * 从 [ArgType] 拆分出包名与短名，并识别内部类（'$' 分隔）。
	 *
	 * 若 [canBeInner] 为 true 且名字里含 '$'，则递归解析外部类并把自己挂到其下。
	 */
	private fun splitAndApplyNames(root: RootNode, type: ArgType, canBeInner: Boolean) {
		val fullObjectName = type.getObject()
		val clsPkg: String
		var clsName: String
		val dot = fullObjectName.lastIndexOf('.')
		if (dot == -1) {
			clsPkg = ""
			clsName = fullObjectName
		} else {
			clsPkg = fullObjectName.substring(0, dot)
			clsName = fullObjectName.substring(dot + 1)
		}

		var innerCls = false
		if (canBeInner) {
			val sep = clsName.lastIndexOf('$')
			if (sep > 0 && sep != clsName.length - 1) {
				var parClsName = clsPkg + '.' + clsName.substring(0, sep)
				if (clsPkg.isEmpty()) {
					parClsName = clsName.substring(0, sep)
				}
				pkg = null
				parentClass = fromName(root, parClsName)
				clsName = clsName.substring(sep + 1)
				innerCls = true
			}
		}
		if (!innerCls) {
			pkg = clsPkg
			parentClass = null
		}
		shortName = clsName
		fullName = makeFullName()
	}

	override fun toString(): String = fullName

	override fun hashCode(): Int = type.hashCode()

	override fun equals(other: Any?): Boolean {
		if (this === other) {
			return true
		}
		if (other is ClassInfo) {
			return type == other.type
		}
		return false
	}

	override fun compareTo(other: ClassInfo): Int = rawName.compareTo(other.rawName)
}

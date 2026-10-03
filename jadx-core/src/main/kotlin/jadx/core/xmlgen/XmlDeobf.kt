package jadx.core.xmlgen

import jadx.core.dex.instructions.args.ArgType
import jadx.core.dex.nodes.RootNode

/**
 * XML 反混淆辅助工具。
 *
 * 用于把 `android:name` 属性、XML 标签等在反混淆过程中被改名的类名还原为别名全名；
 * 并提供“同名属性去重”的判断（同一元素内重复属性只输出一次）。
 */
object XmlDeobf {

	/**
	 * 尝试把 XML 中的潜在类名映射到已解析类的别名全名。
	 *
	 * 仅当名称包含 `.`（像类名）且能在类信息存储中命中时才返回结果，否则返回 null。
	 * [packageName] 非空且名称以 `.` 开头时，按相对类名补全为完整类名。
	 */
	fun deobfClassName(root: RootNode, potentialClassName: String, packageName: String?): String? {
		var className = potentialClassName
		if (className.indexOf('.') == -1) {
			return null
		}
		if (packageName != null && className.startsWith(".")) {
			className = packageName + className
		}
		val clsType = ArgType.`object`(className)
		val classInfo = root.infoStorage.getCls(clsType)
		if (classInfo == null) {
			// unknown class reference
			return null
		}
		return classInfo.aliasFullName
	}

	/**
	 * 判断属性是否重复：向 [attrCache] 添加 [attrFullName]，若已存在（add 返回 false）则视为重复。
	 */
	fun isDuplicatedAttr(attrFullName: String, attrCache: MutableSet<String>): Boolean = !attrCache.add(attrFullName)
}

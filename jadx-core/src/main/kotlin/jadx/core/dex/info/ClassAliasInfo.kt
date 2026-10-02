package jadx.core.dex.info

import jadx.core.utils.StringUtils

/**
 * 类的“别名”信息：记录反混淆/重命名后类所在的包、短名与全名。
 *
 * 原 Java 中本类是包级私有（package-private），仅供 [ClassInfo] 使用，
 * 因此 Kotlin 侧声明为 `internal`，不扩大对外可见性。
 *
 * Kotlin 转换说明：getter/setter 直接改为属性（`pkg`/`shortName`/`fullName`），
 * JVM 访问器名与原 Java 完全一致（`getPkg()` / `getShortName()` / `getFullName()` /
 * `setFullName(...)`）。
 */
internal class ClassAliasInfo(
	/** 别名所在的包；可能与原包相同，也可能为 null（默认包） */
	val pkg: String?,
	/** 别名短名（不含包名） */
	val shortName: String,
) {
	/** 别名的完整类名，创建后由 [ClassInfo] 填充 */
	var fullName: String? = null

	init {
		if (StringUtils.isEmpty(shortName)) {
			throw IllegalArgumentException("Class alias can't be empty")
		}
	}

	override fun toString(): String = "Alias{$shortName, pkg=$pkg, fullName=$fullName}"
}

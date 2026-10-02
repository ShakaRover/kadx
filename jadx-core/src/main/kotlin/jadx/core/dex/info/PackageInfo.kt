package jadx.core.dex.info

import jadx.core.dex.nodes.RootNode

/**
 * 包元信息（PackageInfo）。
 *
 * 描述一个包的全名、短名与父包，并通过 [InfoStorage] 保证同一包只有一个实例。
 *
 * Kotlin 转换说明：
 * - `getFullName()`/`getName()`/`getParentPkg()` 改为属性，JVM 访问器名不变；
 * - 原 Java 的 `public static synchronized` 工厂方法用 companion + `@JvmStatic`
 *   + `@Synchronized` 平替（互斥语义在 companion 内保持一致）；
 * - 原 Java 按 `fullName` 重写 `equals`/`hashCode`，Kotlin 原样保留。
 */
class PackageInfo private constructor(
	/** 包全名（如 `com.example.app`） */
	val fullName: String,
	/** 父包；根包为 null */
	val parentPkg: PackageInfo?,
	/** 包短名（最后一段） */
	val name: String,
) {

	companion object {
		/** 按完整包名获取（或创建）[PackageInfo]，并递归创建父包。 */
		@JvmStatic
		@Synchronized
		fun fromFullPkg(root: RootNode, fullPkg: String): PackageInfo {
			val existPkg = root.getInfoStorage().getPkg(fullPkg)
			if (existPkg != null) {
				return existPkg
			}
			val newPkg: PackageInfo
			val lastDot = fullPkg.lastIndexOf('.')
			if (lastDot == -1) {
				// 未知根包
				newPkg = PackageInfo(fullPkg, null, fullPkg)
			} else {
				val parentPkg = fromFullPkg(root, fullPkg.substring(0, lastDot))
				newPkg = PackageInfo(fullPkg, parentPkg, fullPkg.substring(lastDot + 1))
			}
			root.getInfoStorage().putPkg(newPkg)
			return newPkg
		}

		/** 按父包 + 短名获取（或创建）[PackageInfo]。 */
		@JvmStatic
		@Synchronized
		fun fromShortName(root: RootNode, parent: PackageInfo?, shortName: String): PackageInfo {
			val fullPkg = if (parent == null) shortName else parent.fullName + '.' + shortName
			val existPkg = root.getInfoStorage().getPkg(fullPkg)
			if (existPkg != null) {
				return existPkg
			}
			val newPkg = PackageInfo(fullPkg, parent, shortName)
			root.getInfoStorage().putPkg(newPkg)
			return newPkg
		}
	}

	fun isRoot(): Boolean = parentPkg == null

	fun isDefaultPkg(): Boolean = fullName.isEmpty()

	override fun equals(other: Any?): Boolean {
		if (this === other) {
			return true
		}
		if (other !is PackageInfo) {
			return false
		}
		return fullName == other.fullName
	}

	override fun hashCode(): Int = fullName.hashCode()

	override fun toString(): String = fullName
}

package jadx.gui.treemodel

import jadx.api.ResourceFile

/**
 * 资源文件内部的子资源。
 *
 * **做什么**：当单个资源文件（如 `resources.arsc`）被解析成多个子项时，
 * 用本节点表示其中一个子项；[makeLongString] 会加上基础文件前缀以便区分同名文件。
 *
 * **为什么不是 `data class`**：相等性由 [baseRes] + 名称 + 类型决定，且需要按身份参与树比较。
 */
class JSubResource(
	baseRes: JResource,
	resFile: ResourceFile?,
	name: String,
	shortName: String,
	type: JResource.JResType,
) : JResource(resFile, name, shortName, type) {

	/** 所属的基础资源文件节点。原 Java 为 public 字段，这里用 `@JvmField` 保留。 */
	@JvmField
	var baseRes: JResource = requireNotNull(baseRes)

	fun getBaseRes(): JResource = baseRes

	override fun makeLongString(): String = baseRes.makeLongString() + SUB_RES_PREFIX + super.makeLongString()

	override fun equals(other: Any?): Boolean {
		if (this === other) {
			return true
		}
		if (other !is JSubResource) {
			return false
		}
		return baseRes == other.baseRes &&
			getName() == other.getName() &&
			getType() == other.getType()
	}

	override fun hashCode(): Int = baseRes.hashCode() + 31 * super.hashCode()

	companion object {
		/** 子资源在长名称中的分隔前缀。 */
		const val SUB_RES_PREFIX = ":/"
	}
}

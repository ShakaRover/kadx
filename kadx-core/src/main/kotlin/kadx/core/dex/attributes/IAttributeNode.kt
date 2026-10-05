package kadx.core.dex.attributes

import kadx.api.plugins.input.data.annotations.IAnnotation
import kadx.api.plugins.input.data.attributes.IKadxAttrType
import kadx.api.plugins.input.data.attributes.IKadxAttribute

/**
 * 属性节点接口：任何可以挂载 [AFlag] / [IKadxAttribute] 的节点都实现本接口。
 *
 * **设计目的**：把“属性存储”能力抽象出来，使类、方法、字段、指令、基本块、区域等
 * 不同类型的节点都能统一地添加/查询/删除属性，而无需各自重复实现一套存储逻辑。
 * 默认实现见 [AttrNode]。
 *
 * **Kotlin 转换说明**：
 * - 原 Java 接口方法全部保持方法形态（不转成 Kotlin 属性），因为 `getXxx()` 与属性语法
 *   在 JVM 上虽然同名，但调用方（尤其是 Java 子类）对二者语义敏感；保持方法最机械、最安全。
 * - [get] 原 Java 可能返回 `null`，因此返回类型标为 `T?`，绝不使用 `!!`。
 */
interface IAttributeNode {

	fun add(flag: AFlag)

	fun addAttr(attr: IKadxAttribute)

	fun addAttrs(list: List<IKadxAttribute>)

	fun <T> addAttr(type: IKadxAttrType<AttrList<T>>, obj: T)

	fun copyAttributesFrom(attrNode: AttrNode)

	fun <T : IKadxAttribute> copyAttributeFrom(attrNode: AttrNode, attrType: AType<T>)

	fun <T : IKadxAttribute> rewriteAttributeFrom(attrNode: AttrNode, attrType: AType<T>)

	operator fun contains(flag: AFlag): Boolean

	operator fun <T : IKadxAttribute> contains(type: IKadxAttrType<T>): Boolean

	/** 按类型查询属性，不存在时返回 null（原 Java 允许返回 null） */
	fun <T : IKadxAttribute> get(type: IKadxAttrType<T>): T?

	fun getAnnotation(cls: String): IAnnotation?

	fun <T> getAll(type: IKadxAttrType<AttrList<T>>): List<T>

	fun remove(flag: AFlag)

	fun <T : IKadxAttribute> remove(type: IKadxAttrType<T>)

	fun removeAttr(attr: IKadxAttribute)

	fun clearAttributes()

	fun getAttributesStringsList(): List<String>

	fun getAttributesString(): String

	fun isAttrStorageEmpty(): Boolean
}

package kadx.core.dex.attributes.nodes

import kadx.api.plugins.input.data.attributes.IKadxAttribute
import kadx.core.dex.attributes.AType
import kadx.core.dex.nodes.FieldNode

/**
 * 枚举映射属性：记录 DEX 中“枚举 switch 映射表”字段的键值内容。
 *
 * **背景**：Kotlin/Java 编译器为 `switch(enum)` 生成一个合成类，内部有一个静态数组
 * 把枚举序数映射为 0..N 的整数。还原 switch 时，需要读取该数组的实际内容。
 *
 * **Kotlin 转换说明**：内部类 [KeyValueMap] 的 `put` 原为包级私有，Kotlin 无包级私有，
 * 这里保持为普通方法（仅本文件内部使用）。
 */
class EnumMapAttr : IKadxAttribute {

	/** 一个枚举映射字段的键值容器 */
	class KeyValueMap {
		private val map: MutableMap<Any?, Any?> = HashMap()

		fun get(key: Any?): Any? = map[key]

		fun put(key: Any?, value: Any?) {
			map[key] = value
		}
	}

	/** 字段 → 键值映射；延迟创建，可能为 null */
	private var fieldsMap: MutableMap<FieldNode, KeyValueMap>? = null

	/** 取指定字段的映射，不存在返回 null */
	fun getMap(field: FieldNode): KeyValueMap? = fieldsMap?.get(field)

	/** 添加一条键值对（字段的映射不存在时自动创建） */
	fun add(field: FieldNode, key: Any?, value: Any?) {
		val map = fieldsMap ?: HashMap<FieldNode, KeyValueMap>().also { fieldsMap = it }
		map.getOrPut(field) { KeyValueMap() }.put(key, value)
	}

	fun isEmpty(): Boolean = fieldsMap?.isEmpty() ?: true

	override val attrType: AType<EnumMapAttr> get() = AType.ENUM_MAP

	override fun toString(): String = "Enum fields map: $fieldsMap"
}

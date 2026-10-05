package kadx.api.plugins.input.data.impl

import kadx.api.plugins.input.data.IFieldRef

/**
 * 字段引用的可变默认实现（构造后可通过 setter 修改）。
 *
 * **背景**：kadx-java-input 解析 class file 时先创建本对象，再逐步填充
 * 父类/名字/类型。原 Java 还有一个无参构造器，但全仓库无调用方，故省略。
 *
 * @param parentClassType 字段所属类的完整类型名
 * @param name 字段名
 * @param type 字段的描述符/类型字符串
 */
public class KadxFieldRef(
	private var parentClassTypeValue: String?,
	private var nameValue: String?,
	private var typeValue: String?,
) : IFieldRef {

	override val parentClassType: String? get() = parentClassTypeValue

	public fun setParentClassType(parentClassTypeValue: String?) {
		this.parentClassTypeValue = parentClassTypeValue
	}

	override val name: String? get() = nameValue

	public fun setName(nameValue: String?) {
		this.nameValue = nameValue
	}

	override val type: String? get() = typeValue

	public fun setType(typeValue: String?) {
		this.typeValue = typeValue
	}

	override fun toString(): String = "$parentClassTypeValue->$nameValue:$typeValue"
}

package jadx.api.plugins.input.data.impl

import jadx.api.plugins.input.data.IFieldRef

/**
 * 字段引用的可变默认实现（构造后可通过 setter 修改）。
 *
 * **背景**：jadx-java-input 解析 class file 时先创建本对象，再逐步填充
 * 父类/名字/类型。原 Java 还有一个无参构造器，但全仓库无调用方，故省略。
 *
 * @param parentClassType 字段所属类的完整类型名
 * @param name 字段名
 * @param type 字段的描述符/类型字符串
 */
public class JadxFieldRef(
	private var parentClassType: String?,
	private var name: String?,
	private var type: String?,
) : IFieldRef {

	override fun getParentClassType(): String? = parentClassType

	public fun setParentClassType(parentClassType: String?) {
		this.parentClassType = parentClassType
	}

	override fun getName(): String? = name

	public fun setName(name: String?) {
		this.name = name
	}

	override fun getType(): String? = type

	public fun setType(type: String?) {
		this.type = type
	}

	override fun toString(): String = "$parentClassType->$name:$type"
}

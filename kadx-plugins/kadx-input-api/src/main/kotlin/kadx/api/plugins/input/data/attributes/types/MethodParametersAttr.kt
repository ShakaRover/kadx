package kadx.api.plugins.input.data.attributes.types

import kadx.api.plugins.input.data.AccessFlags
import kadx.api.plugins.input.data.AccessFlagsScope
import kadx.api.plugins.input.data.attributes.KadxAttrType
import kadx.api.plugins.input.data.attributes.PinnedAttribute

/**
 * 方法参数属性：存储方法的参数名和访问标志（MethodParameters attribute）。
 *
 * **背景**：javac 在开启 -parameters 或存在调试信息时写入该属性，
 * 反编译器用它恢复形参名。每个 [Info] 对应一个参数。
 *
 * @param list 参数信息列表，顺序与源码形参一致
 *
 * **Kotlin 转换说明**：原 Java 用私有 final 字段 + `getList()` getter，
 * 这里改用主构造器属性 `val list`；嵌套类 [Info] 在 Kotlin 中声明为
 * nested class（非 inner），字节码上等价于 Java 的 static 内部类，
 * Java 调用方仍按 `MethodParametersAttr.Info` 引用。

 * **open 说明**：原 Java 类非 final，kadx-java-input 的 JavaMethodParametersAttr 继承它，故声明为 `open`。
 */
public open class MethodParametersAttr(
	/** 参数信息列表 */
	public val list: List<Info>,
) : PinnedAttribute() {

	/**
	 * 单个参数的信息（JVM static 嵌套类）。
	 *
	 * @param accFlags 参数访问标志位（如 final）
	 * @param name 参数名；未知时可能为空串
	 */
	public class Info(
		/** 参数访问标志位 */
		public val accFlags: Int,
		/** 参数名 */
		public val name: String,
	) {

		/** 调试字符串，格式与原 Java 一致：`<格式化后的标志><参数名>` */
		override fun toString(): String = AccessFlags.format(accFlags, AccessFlagsScope.METHOD) + name
	}

	/**
	 * 返回本属性的类型标识：[KadxAttrType.METHOD_PARAMETERS]。
	 *
	 * 原 Java 声明返回 `IKadxAttrType<MethodParametersAttr>`，这里用其子类型
	 * `KadxAttrType<MethodParametersAttr>` 做协变覆写（字节码兼容）。
	 */
	override val attrType: KadxAttrType<MethodParametersAttr> get() = KadxAttrType.METHOD_PARAMETERS

	/** 调试字符串，格式与原 Java 一致：`METHOD_PARAMETERS: <列表>`（冒号后有空格）*/
	override fun toString(): String = "METHOD_PARAMETERS: $list"
}

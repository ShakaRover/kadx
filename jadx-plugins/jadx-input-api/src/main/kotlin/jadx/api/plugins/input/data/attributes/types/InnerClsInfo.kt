package jadx.api.plugins.input.data.attributes.types

import jadx.api.plugins.input.data.AccessFlags
import jadx.api.plugins.input.data.AccessFlagsScope

/**
 * 内部类信息：描述一个嵌套类的元数据（InnerClasses attribute 的单条记录）。
 *
 * **背景**：class file / Dex 的 InnerClasses 属性列出所有内部/匿名/局部类，
 * 每条记录包含内部类名、外部类名、简单名和访问标志。
 *
 * @param innerCls 内部类的完整类名（内部形式），如 "com.example.Outer$Inner"
 * @param outerCls 外部类的完整类名；顶层类或无外部类时为 null
 * @param name 简单名（源码中写的名字）；匿名类为 null
 * @param accessFlags 访问标志位（public/final/abstract 等，见 [AccessFlags]）
 *
 * **Kotlin 转换说明**：原 Java 用私有 final 字段 + getter，这里改用主构造器属性。
 * `outerCls` / `name` 在原 Java 中标注 @Nullable，这里如实声明为可空类型。
 */
public class InnerClsInfo(
	/** 内部类的完整类名（内部形式）*/
	public val innerCls: String,
	/** 外部类的完整类名，可为 null */
	public val outerCls: String?,
	/** 简单名；匿名类为 null */
	public val name: String?,
	/** 访问标志位 */
	public val accessFlags: Int,
) {

	/**
	 * 调试字符串，格式与原 Java 完全一致：
	 * `InnerCls{<innerCls>, outerCls=<outerCls>, name=<name>, accessFlags=<格式化后的标志>}`
	 *
	 * null 值在两种语言里拼接字符串时都显示为 "null"。
	 */
	override fun toString(): String = "InnerCls{$innerCls, outerCls=$outerCls, name=$name, accessFlags=${AccessFlags.format(accessFlags, AccessFlagsScope.CLASS)}}"
}

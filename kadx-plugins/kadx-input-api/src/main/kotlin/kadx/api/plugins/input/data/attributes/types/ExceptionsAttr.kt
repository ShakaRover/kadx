package kadx.api.plugins.input.data.attributes.types

import kadx.api.plugins.input.data.attributes.KadxAttrType
import kadx.api.plugins.input.data.attributes.PinnedAttribute

/**
 * 异常属性：存储方法声明的受检异常列表（Exceptions attribute）。
 *
 * **背景**：对应 Java 源码中的 `throws` 子句，如
 * ```java
 * public void save() throws IOException, SQLException { ... }
 * ```
 *
 * @param list 异常类名列表（内部形式，如 "java.io.IOException"）
 *
 * **Kotlin 转换说明**：原 Java 用私有 final 字段 + `getList()` getter，
 * 这里改用主构造器属性 `val list`，字节码生成的 getter 完全相同。

 * **open 说明**：原 Java 类非 final，kadx-java-input 的 JavaExceptionsAttr 继承它，故声明为 `open`。
 */
public open class ExceptionsAttr(
	/** 异常类名列表（内部形式）*/
	public val list: List<String>,
) : PinnedAttribute() {

	/**
	 * 返回本属性的类型标识：[KadxAttrType.EXCEPTIONS]。
	 *
	 * 原 Java 声明返回 `IKadxAttrType<ExceptionsAttr>`，这里用其子类型
	 * `KadxAttrType<ExceptionsAttr>` 做协变覆写（字节码兼容）。
	 */
	override val attrType: KadxAttrType<ExceptionsAttr> get() = KadxAttrType.EXCEPTIONS

	/** 调试字符串，格式与原 Java 一致：`EXCEPTIONS:<列表>`（冒号后无空格）*/
	override fun toString(): String = "EXCEPTIONS:$list"
}

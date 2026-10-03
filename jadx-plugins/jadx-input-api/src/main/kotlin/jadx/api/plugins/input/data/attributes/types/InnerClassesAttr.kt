package jadx.api.plugins.input.data.attributes.types

import jadx.api.plugins.input.data.attributes.JadxAttrType
import jadx.api.plugins.input.data.attributes.PinnedAttribute

/**
 * 内部类属性：存储一个类的所有嵌套类信息（InnerClasses attribute）。
 *
 * **背景**：对应 class file / Dex 的 InnerClasses 属性，记录所有内部/匿名/局部类。
 * key 为内部类的完整类名，value 为该条 [InnerClsInfo] 记录。
 *
 * @param map 内部类名 → [InnerClsInfo] 的映射
 *
 * **Kotlin 转换说明**：原 Java 用私有 final 字段 + `getMap()` getter，
 * 这里改用主构造器属性 `val map`，字节码生成的 getter 完全相同。

 * **open 说明**：原 Java 类非 final，jadx-java-input 的 JavaInnerClsAttr 继承它，故声明为 `open`。
 */
public open class InnerClassesAttr(
	/** 内部类名 → [InnerClsInfo] 的映射 */
	public val map: Map<String, InnerClsInfo>,
) : PinnedAttribute() {

	/**
	 * 返回本属性的类型标识：[JadxAttrType.INNER_CLASSES]。
	 *
	 * 原 Java 声明为协变的具体类型，这里保持一致。
	 */
	override val attrType: JadxAttrType<InnerClassesAttr> get() = JadxAttrType.INNER_CLASSES

	/** 调试字符串，格式与原 Java 一致：`INNER_CLASSES:<映射>`（冒号后无空格）*/
	override fun toString(): String = "INNER_CLASSES:$map"
}

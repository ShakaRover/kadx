package jadx.api.plugins.input.data.attributes.types

import jadx.api.plugins.input.data.attributes.JadxAttrType
import jadx.api.plugins.input.data.attributes.PinnedAttribute

/**
 * 源文件属性：记录类对应的原始 .java 文件名（SourceFile attribute）。
 *
 * **背景**：javac 编译时会在 class file 中写入 SourceFile 属性，
 * 反编译器用它来恢复输出文件的命名。
 *
 * @param fileName 源文件名，如 "MainActivity.java"
 *
 * **Kotlin 转换说明**：原 Java 用私有 final 字段 + `getFileName()` getter，
 * 这里改用主构造器属性 `val fileName`，字节码生成的 getter 完全相同。

 * **open 说明**：原 Java 类非 final，jadx-java-input 的 JavaSourceFileAttr 继承它，故声明为 `open`。
 */
public open class SourceFileAttr(
	/** 源文件名 */
	public val fileName: String,
) : PinnedAttribute() {

	/**
	 * 返回本属性的类型标识：[JadxAttrType.SOURCE_FILE]。
	 *
	 * 原 Java 声明为协变的具体类型，这里保持一致。
	 */
	override fun getAttrType(): JadxAttrType<SourceFileAttr> = JadxAttrType.SOURCE_FILE

	/** 调试字符串，格式与原 Java 一致：`SOURCE:<文件名>`（注意冒号后无空格）*/
	override fun toString(): String = "SOURCE:$fileName"
}

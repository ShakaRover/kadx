package kadx.plugins.input.dex.utils

/**
 * 简单的内存版 [IDexData] 实现：把一段 DEX 字节数组连同文件名包装起来。
 *
 * **背景**：供 smali-input 等插件把"已生成/内嵌"的 DEX 内容直接喂给解析管线，
 * 无需落盘读取文件。两个构造参数均不可为 null（与原 Java 的 Objects.requireNonNull 一致）。
 */
public class SimpleDexData(
	private val fileNameValue: String,
	private val contentValue: ByteArray,
) : IDexData {

	override val fileName: String get() = fileNameValue

	override val content: ByteArray get() = contentValue

	override fun toString(): String = "DexData{$fileNameValue, size=${content.size}}"
}

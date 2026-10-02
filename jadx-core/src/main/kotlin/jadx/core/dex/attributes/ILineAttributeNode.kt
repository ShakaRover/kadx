package jadx.core.dex.attributes

/**
 * 带源码行号的节点接口。
 *
 * **两个位置概念**（容易混淆，务必区分）：
 * - [getSourceLine]：节点在**原始源码**中对应的行号（来自调试信息），可能为 0（未知）。
 * - [getDefPosition]：节点在**反编译生成代码**中的字符偏移（声明位置），用于 UI 定位。
 *
 * **Kotlin 转换说明**：保持方法形态而非 Kotlin 属性，因为 Java 实现类（如
 * `jadx.api.metadata.ICodeNodeRef`）也声明了同名 `getDefPosition/setDefPosition`，
 * 方法形态能确保两边签名精确一致。
 */
interface ILineAttributeNode {

	fun getSourceLine(): Int

	fun setSourceLine(sourceLine: Int)

	fun getDefPosition(): Int

	fun setDefPosition(pos: Int)
}

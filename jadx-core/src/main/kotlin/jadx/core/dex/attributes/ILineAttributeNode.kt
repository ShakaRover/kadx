package jadx.core.dex.attributes

/**
 * 带源码行号的节点接口。
 *
 * **两个位置概念**（容易混淆，务必区分）：
 * - [sourceLine]：节点在**原始源码**中对应的行号（来自调试信息），可能为 0（未知）。
 * - [defPosition]：节点在**反编译生成代码**中的字符偏移（声明位置），用于 UI 定位。
 *
 * **Kotlin 转换说明**：只读 getter 转成 Kotlin 属性，JVM 上仍生成 `getSourceLine()` /
 * `getDefPosition()`；写入仍保留显式 `setSourceLine/setDefPosition`。
 */
interface ILineAttributeNode {

	val sourceLine: Int

	fun setSourceLine(sourceLine: Int)

	val defPosition: Int

	fun setDefPosition(pos: Int)
}

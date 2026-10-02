package jadx.api.metadata

import jadx.api.metadata.impl.CodeMetadataStorage
import java.util.function.BiFunction

/**
 * 单个类的代码元数据：字符位置 -> 代码注解，以及生成行号 -> 源码行号的映射。
 *
 * **做什么**：反编译时收集注解，供 UI 跳转、同步高亮、重命名等使用。
 * 位置以“字符索引”表示（对应代码面板里的光标位置）。
 *
 * **Kotlin 转换说明**：所有 getter / 查询方法保持显式函数形态，Java 调用方零改动；
 * [EMPTY] 用 `companion object` + `@JvmField` 暴露为接口静态字段，Java 侧
 * `ICodeMetadata.EMPTY` 写法不变。
 */
interface ICodeMetadata {

	/** 空元数据单例（比较时请用 `===`）。 */
	companion object {
		@JvmField
		val EMPTY: ICodeMetadata = CodeMetadataStorage.empty()
	}

	/** 精确位置上的注解，没有则返回 null。 */
	fun getAt(position: Int): ICodeAnnotation?

	/** 不大于 [position] 的最近一个注解（即向上查找），没有则返回 null。 */
	fun getClosestUp(position: Int): ICodeAnnotation?

	/** 从 [position] 向上查找指定类型的注解。 */
	fun searchUp(position: Int, annType: ICodeAnnotation.AnnType): ICodeAnnotation?

	/** 在 [position, limitPos] 范围内向上查找指定类型的注解。 */
	fun searchUp(position: Int, limitPos: Int, annType: ICodeAnnotation.AnnType): ICodeAnnotation?

	/**
	 * 从 [startPos] 向更小位置遍历注解。
	 *
	 * @param visitor 返回非 null 值即停止遍历，并作为结果返回
	 */
	fun <T> searchUp(startPos: Int, visitor: BiFunction<Int, ICodeAnnotation, T>): T?

	/**
	 * 从 [startPos] 向更大位置遍历注解。
	 *
	 * @param visitor 返回非 null 值即停止遍历，并作为结果返回
	 */
	fun <T> searchDown(startPos: Int, visitor: BiFunction<Int, ICodeAnnotation, T>): T?

	/** 取 [position] 处所属的节点（可能是外层类或方法）。 */
	fun getNodeAt(position: Int): ICodeNodeRef?

	/** 取 [position] 下方的任意类 / 方法定义。 */
	fun getNodeBelow(position: Int): ICodeNodeRef?

	/** 位置 -> 注解的映射。 */
	fun getAsMap(): Map<Int, ICodeAnnotation>

	/** 生成行号 -> dex 调试行号的映射。 */
	fun getLineMapping(): Map<Int, Int>
}

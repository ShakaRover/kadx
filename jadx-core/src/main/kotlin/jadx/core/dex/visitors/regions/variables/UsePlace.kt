package jadx.core.dex.visitors.regions.variables

import jadx.core.dex.nodes.IBlock
import jadx.core.dex.nodes.IRegion
import java.util.Objects

/**
 * “变量使用位置”：一个区域 + 该区域内的一个基本块。
 *
 * **用途**：[CollectUsageRegionVisitor] 遍历区域树时，用它标记“某条指令位于哪个区域的哪个块里”，
 * [ProcessVariables] 再据此判断变量声明点是否在所有使用点之前。
 *
 * Kotlin 转换说明：
 * - 原 Java 的 `public final` 字段被 Java 代码按字段访问，故用 `@JvmField` 保留同名字段；
 * - 同时保留显式 [getRegion]/[getBlock]（原 getter），两种访问方式都兼容；
 * - 原类手写了 `equals/hashCode`（按区域与块判等），这里原样保留，不使用 `data class`。
 */
class UsePlace(
	@JvmField val region: IRegion,
	@JvmField val block: IBlock,
) {

	fun getRegion(): IRegion = region

	fun getBlock(): IBlock = block

	override fun equals(o: Any?): Boolean {
		if (this === o) {
			return true
		}
		if (o == null || javaClass != o.javaClass) {
			return false
		}
		val usePlace = o as UsePlace
		return Objects.equals(region, usePlace.region) &&
			Objects.equals(block, usePlace.block)
	}

	override fun hashCode(): Int = Objects.hash(region, block)

	override fun toString(): String = "UsePlace{region=" + region + ", block=" + block + '}'
}

package jadx.core.dex.attributes.nodes

import jadx.api.plugins.input.data.attributes.IJadxAttrType
import jadx.api.plugins.input.data.attributes.IJadxAttribute
import jadx.core.dex.attributes.AType
import jadx.core.dex.nodes.BlockNode

/**
 * 临时边属性：记录“块切分过程中临时添加的一条边”的来源块。
 *
 * **用途**：[jadx.core.dex.visitors.blocks.BlockSplitter] 在切分基本块时会临时连边，
 * 完成后需要根据本属性把临时边撤回，避免污染最终 CFG。
 *
 * **Kotlin 转换说明**：[block] 声明为只读属性，生成的 `getBlock()` 与原 JVM 方法名一致。
 */
class TmpEdgeAttr(val block: BlockNode) : IJadxAttribute {

	override val attrType: IJadxAttrType<TmpEdgeAttr> get() = AType.TMP_EDGE

	override fun toString(): String = "TMP_EDGE: $block"
}

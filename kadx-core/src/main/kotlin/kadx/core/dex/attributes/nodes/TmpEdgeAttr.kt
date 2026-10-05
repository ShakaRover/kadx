package kadx.core.dex.attributes.nodes

import kadx.api.plugins.input.data.attributes.IKadxAttrType
import kadx.api.plugins.input.data.attributes.IKadxAttribute
import kadx.core.dex.attributes.AType
import kadx.core.dex.nodes.BlockNode

/**
 * 临时边属性：记录“块切分过程中临时添加的一条边”的来源块。
 *
 * **用途**：[kadx.core.dex.visitors.blocks.BlockSplitter] 在切分基本块时会临时连边，
 * 完成后需要根据本属性把临时边撤回，避免污染最终 CFG。
 *
 * **Kotlin 转换说明**：[block] 声明为只读属性，生成的 `getBlock()` 与原 JVM 方法名一致。
 */
class TmpEdgeAttr(val block: BlockNode) : IKadxAttribute {

	override val attrType: IKadxAttrType<TmpEdgeAttr> get() = AType.TMP_EDGE

	override fun toString(): String = "TMP_EDGE: $block"
}

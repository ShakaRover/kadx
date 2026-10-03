package jadx.core.dex.attributes.nodes

import jadx.api.plugins.input.data.attributes.IJadxAttrType
import jadx.api.plugins.input.data.attributes.IJadxAttribute
import jadx.core.dex.attributes.AType
import jadx.core.dex.nodes.BlockNode

/**
 * 异常拆分交叉点属性。
 *
 * **背景**：`BlockExceptionHandler` 在某些 try 区域底部会创建合成块，本属性记录该 try
 * 区域底部的“原始路径交叉点”块。这样在后续重构基本块时，可以避开这个交叉点，
 * 避免生成错误的循环结构。
 */
class ExcSplitCrossAttr(val originalPathCross: BlockNode) : IJadxAttribute {

	override val attrType: IJadxAttrType<*> get() = AType.EXC_SPLIT_CROSS

	override fun toString(): String = "ExcSplitCross -> " + originalPathCross.toString()
}

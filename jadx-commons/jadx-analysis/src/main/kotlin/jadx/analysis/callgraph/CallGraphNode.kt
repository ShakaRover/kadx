// SPDX-License-Identifier: Apache-2.0
package jadx.analysis.callgraph

import jadx.analysis.callgraph.api.ICallGraphNode
import jadx.core.dex.attributes.IAttributeNode
import jadx.core.dex.info.MethodInfo
import jadx.core.dex.nodes.MethodNode
import org.jetbrains.annotations.Nullable

/**
 * 调用图节点的实现类
 *
 * 实现了 [ICallGraphNode] 接口，代表调用图中的单个方法节点。
 * 可以只包含基本信息（MethodInfo），也可以包含完整的分析结果（MethodNode）。
 *
 * @property id 节点的唯一标识符
 * @property mthInfo 方法的基本信息
 * @property mthNode 完整的方法节点（可能为 null）
 * @property attrNode 该节点的属性容器
 */
class CallGraphNode private constructor(
	override val id: Int,
	override val methodInfo: MethodInfo,
	@Nullable override val methodNode: MethodNode?,
) : ICallGraphNode {
	// 每个节点都有独立的属性节点，用于存储方法相关的元数据
	private val attrNode = CallGraphAttrNode()

	/**
	 * 仅使用基本信息创建节点（未解析状态）
	 *
	 * @param id 节点 ID
	 * @param mthInfo 方法信息
	 */
	constructor(id: Int, mthInfo: MethodInfo) : this(id, mthInfo, null)

	/**
	 * 使用完整的方法节点创建（已解析状态）
	 *
	 * @param id 节点 ID
	 * @param mthNode 方法节点，会自动提取 MethodInfo
	 */
	constructor(id: Int, mthNode: MethodNode) : this(id, mthNode.getMethodInfo(), mthNode)

	/**
	 * 判断是否已解析到具体实现
	 *
	 * 当拥有完整的 MethodNode 时认为已解析。
	 *
	 * @return true=已解析（有 MethodNode），false=未完全解析
	 */
	override val isResolved: Boolean get() = methodNode != null

	/**
	 * 获取属性节点
	 *
	 * @return 该节点的属性容器
	 */
	override fun attributes(): IAttributeNode = attrNode

	/**
	 * 字符串表示（用于调试）
	 *
	 * @return 方法的完整 ID
	 */
	override fun toString(): String = methodInfo.fullId
}

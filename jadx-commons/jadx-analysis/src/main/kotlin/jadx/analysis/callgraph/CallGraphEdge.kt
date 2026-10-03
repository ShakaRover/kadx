// SPDX-License-Identifier: Apache-2.0
package jadx.analysis.callgraph

import jadx.analysis.callgraph.api.ICallGraphEdge
import jadx.analysis.callgraph.api.ICallGraphNode
import jadx.core.dex.attributes.IAttributeNode

/**
 * 调用图边的实现类
 *
 * 实现了 [ICallGraphEdge] 接口，表示两个方法节点之间的调用关系。
 * 每条边都有自己独立的属性节点用于存储元数据。
 *
 * @property from 调用者节点（发起调用的方法）
 * @property to 被调用者节点（接收调用的方法）
 * @property attrNode 该边的属性容器
 */
class CallGraphEdge(
	override val from: ICallGraphNode,
	private val to: ICallGraphNode,
) : ICallGraphEdge {
	// 每条边都有独立的属性节点，用于存储调用相关的元数据
	private val attrNode = CallGraphAttrNode()

	/**
	 * 获取被调用者节点
	 *
	 * @return 接收调用的方法对应的节点
	 */
	override fun to(): ICallGraphNode = to

	/**
	 * 判断该调用是否已解析到具体目标
	 *
	 * 通过检查目标节点的解析状态来判断。
	 *
	 * @return true=已解析，false=未完全解析
	 */
	override val isResolved: Boolean get() = to.isResolved

	/**
	 * 获取属性节点
	 *
	 * @return 该边的属性容器
	 */
	override fun attributes(): IAttributeNode = attrNode

	/**
	 * 字符串表示（用于调试）
	 */
	override fun toString(): String = "CallGraphEdge{from=$from, to=$to}"
}

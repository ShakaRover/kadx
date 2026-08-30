// SPDX-License-Identifier: Apache-2.0
package jadx.analysis.callgraph.api

import jadx.core.dex.attributes.IAttributeNode

/**
 * 调用图边的接口，表示两个方法之间的调用关系
 *
 * 调用图中的每一条边代表一个方法调用：从调用者（from）指向被调用者（to）。
 * 边可以携带额外的属性信息，用于记录调用的类型、上下文等元数据。
 */
interface ICallGraphEdge {
	/**
	 * 获取调用边的起始节点（调用者）
	 *
	 * @return 发起调用的方法对应的节点
	 */
	fun getFrom(): ICallGraphNode

	/**
	 * 获取调用边的目标节点（被调用者）
	 *
	 * @return 接收调用的方法对应的节点
	 */
	fun to(): ICallGraphNode

	/**
	 * 判断该调用是否已解析到具体目标
	 *
	 * 对于虚函数调用、接口方法调用等，可能无法在静态分析时确定确切目标。
	 * resolved=true 表示已经确定了具体的被调用方法；
	 * resolved=false 表示这是一个未完全解析的调用（可能是多态调用）。
	 *
	 * @return true=已解析到具体目标，false=未完全解析
	 */
	fun isResolved(): Boolean

	/**
	 * 获取附加的属性信息
	 *
	 * 属性可以包含调用类型、调用上下文、异常信息等元数据。
	 *
	 * @return 属性节点，用于存储和查询各种元数据
	 */
	fun attributes(): IAttributeNode
}

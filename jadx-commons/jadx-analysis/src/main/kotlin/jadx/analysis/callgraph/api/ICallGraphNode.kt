// SPDX-License-Identifier: Apache-2.0
package jadx.analysis.callgraph.api

import jadx.core.dex.attributes.IAttributeNode
import jadx.core.dex.info.MethodInfo
import jadx.core.dex.nodes.MethodNode
import org.jetbrains.annotations.Nullable

/**
 * 调用图节点的接口，代表一个方法在调用图中的位置
 *
 * 每个节点对应程序中的一个方法，包含方法的元数据（名称、签名等）
 * 以及可选的完整方法节点信息（用于访问更详细的分析结果）。
 */
interface ICallGraphNode {
	/**
	 * 获取节点的唯一标识符
	 *
	 * ID 在整个调用图中是唯一的，可用于快速查找和索引。
	 *
	 * @return 正整数 ID
	 */
	fun getId(): Int

	/**
	 * 获取方法的基本信息
	 *
	 * 包含方法的名称、签名、所属类等元数据，但不包含完整的分析结果。
	 * 这是轻量级的访问方式，适合只需要基本信息时使用。
	 *
	 * @return 方法信息对象
	 */
	fun getMethodInfo(): MethodInfo

	/**
	 * 获取完整的方法节点（如果可用）
	 *
	 * MethodNode 包含方法的完整分析结果，包括字节码、控制流图等。
	 * 可能返回 null，取决于构建调用图时是否加载了完整信息。
	 *
	 * @return 方法节点，如果未加载则返回 null
	 */
	@Nullable
	fun getMethodNode(): MethodNode?

	/**
	 * 判断该节点是否已解析到具体实现
	 *
	 * 对于抽象方法、接口方法或外部库的方法，可能无法获取完整实现。
	 * resolved=true 表示已经定位到了具体的方法实现；
	 * resolved=false 表示这是一个未完全解析的引用。
	 *
	 * @return true=已解析到具体实现，false=未完全解析
	 */
	fun isResolved(): Boolean

	/**
	 * 获取附加的属性信息
	 *
	 * 属性可以包含节点的类型（入口点、外部调用等）、优先级、注释等元数据。
	 *
	 * @return 属性节点，用于存储和查询各种元数据
	 */
	fun attributes(): IAttributeNode
}

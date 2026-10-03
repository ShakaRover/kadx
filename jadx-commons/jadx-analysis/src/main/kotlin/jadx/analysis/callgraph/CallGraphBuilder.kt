// SPDX-License-Identifier: Apache-2.0
package jadx.analysis.callgraph

import jadx.analysis.callgraph.api.ICallGraph
import jadx.analysis.callgraph.api.ICallGraphBuilder
import jadx.analysis.callgraph.api.ICallGraphEdge
import jadx.api.JadxDecompiler
import jadx.core.dex.info.ClassInfo
import jadx.core.dex.info.MethodInfo
import jadx.core.dex.nodes.ClassNode
import jadx.core.dex.nodes.MethodNode
import org.jetbrains.annotations.Nullable
import java.util.concurrent.atomic.AtomicInteger

/**
 * 调用图构建器的实现类
 *
 * 实现了 [ICallGraphBuilder] 接口，负责遍历所有类和方**，收集调用关系并构建成图。
 * 支持包名过滤和只保留已解析调用的选项。
 *
 * @property decompiler jadx 反编译器实例，提供访问 Dex 文件的入口
 * @property resolvedOnly 是否只包含已解析的调用边
 * @property pkgFilter 包名过滤器（以点号结尾）
 */
class CallGraphBuilder(
	private val decompiler: JadxDecompiler,
) : ICallGraphBuilder {
	// 默认包含所有调用，包括未解析的虚拟调用
	private var resolvedOnly = false

	// 包名过滤器，null 表示不过滤
	@Nullable private var pkgFilter: String? = null

	/**
	 * 设置是否只保留已解析的调用边
	 *
	 * @param resolved true=只保留已解析的边，false=包含所有边（默认）
	 * @return this 引用，用于链式调用
	 */
	override fun resolvedOnly(resolved: Boolean): ICallGraphBuilder {
		this.resolvedOnly = resolved
		return this
	}

	/**
	 * 设置包名过滤器
	 *
	 * 只包含匹配指定包名的类和方法。会自动在末尾添加点号。
	 *
	 * @param pkgFilter 包名过滤模式（如 "com.example"）
	 * @return this 引用，用于链式调用
	 */
	override fun includePackages(pkgFilter: String): ICallGraphBuilder {
		// 确保过滤器以点号结尾，便于后续 startsWith 匹配
		this.pkgFilter = if (pkgFilter.endsWith(".")) pkgFilter else "$pkgFilter."
		return this
	}

	/**
	 * 构建调用图
	 *
	 * @return 构建完成的 [ICallGraph] 实例
	 */
	override fun build(): ICallGraph {
		val edges = collectEdges()
		val pkgFilterValue = pkgFilter
		return CallGraph(decompiler.getArgs(), edges, resolvedOnly, pkgFilterValue)
	}

	/**
	 * 收集所有调用边
	 *
	 * 遍历所有类和方法，建立调用关系图。
	 *
	 * @return 包含所有边的列表
	 */
	private fun collectEdges(): List<ICallGraphEdge> {
		// 原子计数器，用于生成唯一的节点 ID
		val nodeId = AtomicInteger()
		// MethodInfo -> CallGraphNode 的缓存映射，避免重复创建节点
		val nodes = mutableMapOf<MethodInfo, CallGraphNode>()
		// 存储所有调用边
		val edges = mutableListOf<ICallGraphEdge>()
		// 捕获包过滤器值用于闭包
		val pkgFilterValue = pkgFilter

		// 遍历所有类（包括内部类）
		for (cls in checkNotNull(decompiler.getRoot()).classes) {
			// 跳过不匹配包名过滤器的类
			if (ignorePkg(cls.classInfo, pkgFilterValue)) {
				continue
			}

			// 遍历类的所有方法
			for (mth in cls.methods) {
				// 获取或创建当前方法的节点
				val thisNode = getCallGraphNode(mth, nodes, nodeId)

				// 收集调用当前方法的其他方法（谁调用了这个方法）
				for (use in mth.useIn) {
					if (ignorePkg(checkNotNull(use.declaringClass).classInfo, pkgFilterValue)) {
						continue
					}
					val useInNode = getCallGraphNode(use, nodes, nodeId)
					// 添加边：调用者 -> 被调用者
					edges.add(CallGraphEdge(useInNode, thisNode))
				}

				// 如果不过滤未解析的调用，也收集它们
				if (!resolvedOnly) {
					for (used in mth.getUnresolvedUsed()) {
						if (ignorePkg(used.declClass, pkgFilterValue)) {
							continue
						}
						val usedNode = getCallGraphNode(used, nodes, nodeId)
						// 添加边：当前方法 -> 未解析的目标
						edges.add(CallGraphEdge(thisNode, usedNode))
					}
				}
			}
		}

		return edges
	}

	/**
	 * 判断类是否应该被忽略（基于包名过滤器）
	 *
	 * @param clsInfo 类的信息
	 * @return true=应忽略，false=应包含
	 */
	private fun ignorePkg(clsInfo: ClassInfo, pkgFilterValue: String?): Boolean {
		// 没有过滤器时全部包含
		pkgFilterValue ?: return false
		// 类名不以过滤器开头则忽略
		return !clsInfo.fullName.startsWith(pkgFilterValue)
	}

	/**
	 * 获取或创建方法节点（从 MethodNode）
	 *
	 * @param mth 方法节点
	 * @param nodes 缓存映射
	 * @param nodeId ID 计数器
	 * @return 对应的方法节点
	 */
	private fun getCallGraphNode(
		mth: MethodNode,
		nodes: MutableMap<MethodInfo, CallGraphNode>,
		nodeId: AtomicInteger,
	): CallGraphNode = nodes.computeIfAbsent(mth.methodInfo) {
		CallGraphNode(nodeId.incrementAndGet(), mth)
	}

	/**
	 * 获取或创建方法节点（从 MethodInfo）
	 *
	 * @param mth 方法信息
	 * @param nodes 缓存映射
	 * @param nodeId ID 计数器
	 * @return 对应的方法节点
	 */
	private fun getCallGraphNode(
		mth: MethodInfo,
		nodes: MutableMap<MethodInfo, CallGraphNode>,
		nodeId: AtomicInteger,
	): CallGraphNode = nodes.computeIfAbsent(mth) {
		CallGraphNode(nodeId.incrementAndGet(), mth)
	}
}

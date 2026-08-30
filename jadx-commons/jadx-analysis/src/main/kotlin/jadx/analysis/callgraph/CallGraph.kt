// SPDX-License-Identifier: Apache-2.0
package jadx.analysis.callgraph

import jadx.analysis.callgraph.api.ICallGraph
import jadx.analysis.callgraph.api.ICallGraphEdge
import jadx.api.JadxArgs
import java.nio.file.Path

/**
 * 调用图的实现类
 *
 * 实现了 [ICallGraph] 接口，持有调用边的列表并提供导出功能。
 * 这是一个内部类（package-private），通过 [JadxCallGraph.builder] 的构建流程创建。
 *
 * @property args jadx 配置参数，用于导出时传递上下文
 * @property edges 调用图中所有的边
 */
class CallGraph internal constructor(
	private val args: JadxArgs,
	private val edges: List<ICallGraphEdge>,
	private val resolvedOnly: Boolean,
	private val pkgFilter: String?,
) : ICallGraph {
	/**
	 * 获取所有调用边
	 *
	 * @return 包含所有边的列表
	 */
	override fun edges(): List<ICallGraphEdge> = edges

	/**
	 * 导出为 DOT 格式（Graphviz）
	 *
	 * @param path 输出文件路径
	 */
	override fun writeDot(path: Path) {
		CallGraphExportDot(args, this).writeTo(path)
	}

	/**
	 * 导出为 JSON 格式
	 *
	 * @param path 输出文件路径
	 */
	override fun writeJson(path: Path) {
		CallGraphExportJson(this).writeTo(path)
	}
}

// SPDX-License-Identifier: Apache-2.0
package jadx.analysis.callgraph

import jadx.analysis.callgraph.api.ICallGraph
import jadx.analysis.callgraph.api.ICallGraphEdge
import jadx.analysis.callgraph.api.ICallGraphNode
import jadx.api.JadxArgs
import jadx.api.impl.SimpleCodeWriter
import jadx.core.utils.DotGraphUtils
import jadx.core.utils.files.FileUtils
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
import java.nio.file.StandardOpenOption.WRITE
import java.util.Comparator

/**
 * 调用图 DOT 格式导出器
 *
 * 将调用图转换为 Graphviz DOT 格式，可用于可视化。
 * DOT 是一种描述有向图的文本格式，可以用 `dot` 命令渲染成图片。
 *
 * @property args jadx 配置参数
 * @property callGraph 要导出的调用图
 */
class CallGraphExportDot(
	private val args: JadxArgs,
	private val callGraph: ICallGraph,
) {
	/**
	 * 写入 DOT 内容到文件
	 *
	 * @param path 输出文件路径
	 */
	fun writeTo(path: Path) {
		try {
			FileUtils.makeDirsForFile(path)
			Files.writeString(
				path,
				writeToString(),
				StandardCharsets.UTF_8,
				WRITE,
				TRUNCATE_EXISTING,
				CREATE,
			)
		} catch (e: IOException) {
			throw RuntimeException("Failed to save DOT file: $path", e)
		}
	}

	/**
	 * 生成 DOT 格式字符串
	 *
	 * @return DOT 格式的调用图描述
	 */
	fun writeToString(): String {
		// 收集所有节点（去重）
		val nodeMap = mutableMapOf<Int, Node>()
		for (edge in callGraph.edges()) {
			addNode(edge.from, nodeMap)
			addNode(edge.to(), nodeMap)
		}

		// 按 ID 排序节点
		val nodes = nodeMap.values.toList().sortedWith(Comparator.comparingInt { it.id })

		// 构建 DOT 输出
		val cw = SimpleCodeWriter(args)
		cw.add("digraph CallGraph {")

		// 输出所有节点定义
		for (node in nodes) {
			cw.startLine()
			addNodeName(cw, node.id)
			cw.add("[shape=record,label=")
			cw.add(DotGraphUtils.escape(node.method))
			cw.add("]")
		}

		// 输出所有边定义
		for (edge in callGraph.edges()) {
			cw.startLine()
			addNodeName(cw, edge.from.id)
			cw.add(" -> ")
			addNodeName(cw, edge.to().id)
			cw.add(';')
		}

		cw.startLine('}')
		return cw.getCodeStr()
	}

	/**
	 * 添加节点名称到输出流
	 *
	 * @param cw 代码写入器
	 * @param id 节点 ID
	 */
	private fun addNodeName(cw: SimpleCodeWriter, id: Int) {
		cw.add('N')
		cw.add(id.toString())
	}

	/**
	 * 添加节点到映射（如果不存在）
	 *
	 * @param cgNode 调用图节点
	 * @param nodeMap 节点映射表
	 */
	private fun addNode(cgNode: ICallGraphNode, nodeMap: MutableMap<Int, Node>) {
		nodeMap.computeIfAbsent(cgNode.id) { id ->
			val node = Node()
			node.id = id
			node.method = cgNode.methodInfo.rawFullId
			node
		}
	}

	/**
	 * 内部节点数据结构
	 *
	 * @property id 节点 ID
	 * @property method 方法的完整标识符
	 */
	class Node {
		var id: Int = 0
		var method: String = ""
	}
}

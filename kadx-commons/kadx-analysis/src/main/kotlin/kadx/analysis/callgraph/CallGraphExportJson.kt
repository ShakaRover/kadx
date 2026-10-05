// SPDX-License-Identifier: Apache-2.0
package kadx.analysis.callgraph

import com.google.gson.GsonBuilder
import com.google.gson.Strictness
import kadx.analysis.callgraph.api.ICallGraph
import kadx.analysis.callgraph.api.ICallGraphEdge
import kadx.analysis.callgraph.api.ICallGraphNode
import kadx.core.utils.files.FileUtils
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
import java.nio.file.StandardOpenOption.WRITE
import java.util.Comparator

/**
 * 调用图 JSON 格式导出器
 *
 * 将调用图转换为 JSON 格式，便于程序化处理。
 * 使用 Gson 库进行序列化，输出包含节点和边的完整信息。
 *
 * @property callGraph 要导出的调用图
 * @property gson Gson 实例，配置了安全选项
 */
class CallGraphExportJson(
	private val callGraph: ICallGraph,
) {
	// 配置 Gson：禁用 JDK 不安全特性、禁止内部类序列化、严格模式
	private val gson = GsonBuilder()
		.disableJdkUnsafe()
		.disableInnerClassSerialization()
		.setStrictness(Strictness.STRICT)
		// .setPrettyPrinting() // TODO: add option for pretty print?
		.create()

	/**
	 * 写入 JSON 内容到文件
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
			throw RuntimeException("Failed to save JSON file: $path", e)
		}
	}

	/**
	 * 生成 JSON 格式字符串
	 *
	 * @return JSON 格式的调用图描述
	 */
	fun writeToString(): String {
		val edges = mutableListOf<Edge>()
		val nodeMap = mutableMapOf<Int, Node>()

		// 遍历所有边，收集节点和构建 JSON 边对象
		for (edge in callGraph.edges()) {
			val fromNode = edge.from
			val toNode = edge.to()
			addNode(fromNode, nodeMap)
			addNode(toNode, nodeMap)

			val jsonEdge = Edge()
			jsonEdge.getFrom = fromNode.id
			jsonEdge.to = toNode.id
			jsonEdge.resolved = edge.isResolved
			edges.add(jsonEdge)
		}

		// 按 ID 排序节点
		val nodes = nodeMap.values.toList().sortedWith(Comparator.comparingInt { it.id })

		// 构建根对象并序列化
		val rootNode = RootNode()
		rootNode.nodes = nodes
		rootNode.edges = edges
		return gson.toJson(rootNode)
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
	 * JSON 根节点结构
	 *
	 * @property nodes 所有节点的列表
	 * @property edges 所有边的列表
	 */
	class RootNode {
		var nodes: List<Node>? = null
		var edges: List<Edge>? = null
	}

	/**
	 * JSON 节点结构
	 *
	 * @property id 节点 ID
	 * @property method 方法的完整标识符
	 */
	class Node {
		var id: Int = 0
		var method: String = ""
	}

	/**
	 * JSON 边结构
	 *
	 * @property getFrom 起始节点 ID
	 * @property to 目标节点 ID
	 * @property resolved 是否已解析到具体目标
	 */
	class Edge {
		var getFrom: Int = 0
		var to: Int = 0
		var resolved: Boolean = false
	}
}

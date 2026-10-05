// SPDX-License-Identifier: Apache-2.0
package kadx.analysis.callgraph.api

import java.nio.file.Path

/**
 * 调用图的顶层接口，表示整个程序的调用关系图
 *
 * 调用图是一个有向图，节点代表方法，边代表方法之间的调用关系。
 * 这个接口提供了访问图中所有边的能力，以及将图导出为不同格式的功能。
 */
interface ICallGraph {
	/**
	 * 获取调用图中的所有边
	 *
	 * 每条边代表一个方法调用关系。遍历这些边可以重建完整的调用图结构。
	 * Java/Kotlin 代码都使用 `.edges()` 调用
	 *
	 * @return 包含所有调用边的列表，不可修改
	 */
	fun edges(): List<ICallGraphEdge>

	/**
	 * 将调用图导出为 DOT 格式（Graphviz）
	 *
	 * DOT 是一种描述图的文本格式，可以用 Graphviz 工具渲染成可视化图形。
	 * 导出的文件可以直接用 `dot -Tpng file.dot -o output.png` 命令转换。
	 *
	 * @param path 输出文件的绝对路径
	 */
	fun writeDot(path: Path)

	/**
	 * 将调用图导出为 JSON 格式
	 *
	 * JSON 格式适合程序化处理，可以方便地被其他工具或脚本读取。
	 * 导出的 JSON 包含节点和边的完整信息。
	 *
	 * @param path 输出文件的绝对路径
	 */
	fun writeJson(path: Path)
}

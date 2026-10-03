package jadx.gui.ui.graphs

/**
 * 图中一条有向边（用节点 ID 表示）。
 *
 * **做什么**：`CallGraphDialog` / `ClassMethodGraphDialog` 用 [Edge] 去重边，
 * 因此必须提供基于 `(source, dest)` 的 `equals/hashCode`（原 Java 手写实现，Kotlin 原样保留）。
 *
 * 属性 [source] / [dest] 生成 `getSource()` / `getDest()`，与原 JVM 方法名一致。
 */
internal class Edge(val source: Int, val dest: Int) {

	override fun equals(other: Any?): Boolean {
		if (other !is Edge) {
			return false
		}
		return source == other.source && dest == other.dest
	}

	override fun hashCode(): Int = source + 31 * dest
}

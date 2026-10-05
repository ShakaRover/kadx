package jadx.gui.ui

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Test
import javax.swing.tree.TreeNode

/**
 * 空项目加载路径的回归测试。
 *
 * MainWindow.clearTree() 会 `treeModel.setRoot(null)`；当模型 root 已是 null 时，
 * Swing 的 DefaultTreeModel.setRoot 仍会以 **null** 回调 nodeStructureChanged(node)。
 * 上游 Java 覆写（TreeNode 平台参数）可空透传、super 实现对 null 直接忽略；
 * Kotlin 覆写若声明非空参数，会在「损坏项目按空项目加载」流程中触发内在空检查 NPE
 * （FilterableTreeModel.nodeStructureChanged, parameter node）。
 *
 * 用 Unsafe 分配实例以跳过构造器（避免依赖 MainWindow），用反射调用以同时
 * 兼容旧（非空参数）与新（可空参数）签名。
 */
class FilterableTreeModelTest {

	@Test
	fun `nodeStructureChanged tolerates null node`() {
		val unsafe = sun.misc.Unsafe::class.java
			.getDeclaredField("theUnsafe")
			.apply { isAccessible = true }
			.get(null) as sun.misc.Unsafe
		val model = unsafe.allocateInstance(FilterableTreeModel::class.java) as FilterableTreeModel
		val method = FilterableTreeModel::class.java
			.getMethod("nodeStructureChanged", TreeNode::class.java)
		assertDoesNotThrow {
			method.invoke(model, null)
		}
	}
}

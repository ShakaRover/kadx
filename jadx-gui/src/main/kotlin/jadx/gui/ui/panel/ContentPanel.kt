package jadx.gui.ui.panel

import jadx.gui.settings.JadxSettings
import jadx.gui.treemodel.JNode
import jadx.gui.ui.MainWindow
import jadx.gui.ui.tab.TabbedPane
import jadx.gui.ui.tab.TabsController
import org.slf4j.LoggerFactory
import javax.swing.JPanel

/**
 * 所有「内容面板」的抽象基类。
 *
 * **做什么**：内容面板是标签栏里真正显示内容的 Swing 组件（代码、图片、字体、HTML、调试器等）。
 * 基类持有所属的 [TabbedPane] 与对应的树节点 [JNode]，并提供取主窗口、设置、控制器等便捷方法。
 *
 * **线程模型**：所有方法都假定在 Swing 事件线程（EDT）上调用。
 *
 * **Java 互操作**：原 Java 的 `protected` 字段 `tabbedPane`/`node` 仍以 `@JvmField`
 * 暴露，Java 子类（如 `QuarkReportPanel`）可直接访问；同时保留显式 `getXxx()` 函数，
 * 让已有的 Kotlin 调用点（如 `AbstractCodeArea.getContentPanel().getTabbedPane()`）零改动。
 */
abstract class ContentPanel protected constructor(panel: TabbedPane, jnode: JNode) : JPanel() {

	/**
	 * 所属标签栏；[dispose] 后置为 `null`。
	 *
	 * 用 `@JvmField` 保留原 Java 的 `protected` 字段语义（Java 子类直接读写）。
	 */
	@JvmField
	protected var tabbedPane: TabbedPane? = panel

	/** 本面板对应的树节点；[dispose] 后置为 `null`。 */
	@JvmField
	protected var node: JNode? = jnode

	/** 根据当前设置刷新界面（由子类实现）。 */
	abstract fun loadSettings()

	/** 所属标签栏。 */
	fun getTabbedPane(): TabbedPane = checkNotNull(tabbedPane)

	/** 标签栏控制器。 */
	val tabsController: TabsController get() = getTabbedPane().tabsController

	/** 主窗口。 */
	val mainWindow: MainWindow get() = getTabbedPane().getMainWindow()

	/** 对应的树节点。 */
	fun getNode(): JNode = checkNotNull(node)

	/**
	 * 滚动到代码位置。
	 *
	 * 基类默认无实现，仅打印警告；支持代码定位的子类会覆写。
	 */
	open fun scrollToPos(pos: Int) {
		LOG.warn("ContentPanel.scrollToPos method not implemented, class: {}", javaClass.simpleName)
	}

	/** 全局设置。 */
	val settings: JadxSettings get() = mainWindow.getSettings()

	/** 该节点是否支持快速标签页。 */
	fun supportsQuickTabs(): Boolean = getNode().supportsQuickTabs()

	/** 释放面板资源：断开对标签栏与节点的引用。 */
	open fun dispose() {
		tabbedPane = null
		node = null
	}

	companion object {
		private val LOG = LoggerFactory.getLogger(ContentPanel::class.java)
	}
}

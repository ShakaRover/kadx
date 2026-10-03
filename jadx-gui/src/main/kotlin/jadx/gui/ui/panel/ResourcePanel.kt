package jadx.gui.ui.panel

import jadx.api.ResourceType
import jadx.api.resources.ResourceContentType
import jadx.gui.treemodel.JResource
import jadx.gui.ui.codearea.AbstractCodeArea
import jadx.gui.ui.codearea.AbstractCodeContentPanel
import jadx.gui.ui.codearea.BinaryContentPanel
import jadx.gui.ui.codearea.CodeContentPanel
import jadx.gui.ui.tab.TabbedPane
import jadx.gui.utils.NLS
import java.awt.BorderLayout
import java.awt.Component
import java.util.Collections
import java.util.IdentityHashMap
import javax.swing.JTabbedPane
import javax.swing.SwingUtilities
import javax.swing.border.EmptyBorder
import javax.swing.event.ChangeListener

/**
 * 资源文件面板：根据资源类型在多个子标签页中展示内容。
 *
 * **做什么**：图片显示图片页 + 十六进制页，字体显示字体页 + 十六进制页；
 * 二进制资源只显示十六进制页，可识别语法的文本资源显示代码页，其余同时显示代码页与十六进制页。
 * 子标签页首次被选中时，若实现了 [ILazyLoad] 则触发懒加载。
 *
 * **线程模型**：懒加载通过 `SwingUtilities.invokeLater` 调度到 EDT，保持原模型。
 *
 * **命名说明**：原 Java 子类用一个同名字段 `tabbedPane` 遮蔽了父类字段；
 * Kotlin 不允许子类重名属性，故这里改名为私有的 [resourceTabs]（纯内部字段，对外无影响）。
 */
class ResourcePanel(panel: TabbedPane, resource: JResource) : AbstractCodeContentPanel(panel, resource) {

	private val resourceTabs: JTabbedPane

	/**
	 * 记录已经加载过的标签页组件。
	 *
	 * 只需要按引用去重，因此使用基于 [IdentityHashMap] 的集合，
	 * 避免对大对象调用 `hashCode()`/`equals()`。
	 */
	private val initializedTabs: MutableSet<Component> = Collections.newSetFromMap(IdentityHashMap())

	init {
		layout = BorderLayout()
		border = EmptyBorder(0, 0, 0, 0)

		resourceTabs = JTabbedPane(JTabbedPane.BOTTOM)
		resourceTabs.setBorder(EmptyBorder(0, 0, 0, 0))
		resourceTabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT)
		buildTabs(panel, resource)
		val changeListener = ChangeListener { _ ->
			val selected = resourceTabs.getSelectedComponent()
			if (initializedTabs.add(selected)) {
				if (selected is ILazyLoad) {
					SwingUtilities.invokeLater { selected.loadData() }
				}
			}
			getMainWindow().updateHexViewMenuEnabled()
		}
		resourceTabs.addChangeListener(changeListener)
		add(resourceTabs, BorderLayout.CENTER)
		changeListener.stateChanged(null)
	}

	private fun buildTabs(panel: TabbedPane, resource: JResource) {
		val resourceType = checkNotNull(resource.getResFile()).getType()
		when (resourceType) {
			ResourceType.IMG -> {
				resourceTabs.addTab(NLS.str("tabs.image"), ImagePanel(panel, resource))
				resourceTabs.addTab(NLS.str("tabs.hex"), BinaryContentPanel(panel, resource))
				return
			}

			ResourceType.FONT -> {
				resourceTabs.addTab(NLS.str("tabs.font"), FontPanel(panel, resource))
				resourceTabs.addTab(NLS.str("tabs.hex"), BinaryContentPanel(panel, resource))
				return
			}

			else -> Unit
		}

		if (resource.getContentType() == ResourceContentType.CONTENT_BINARY) {
			resourceTabs.addTab(NLS.str("tabs.hex"), BinaryContentPanel(panel, resource))
			return
		}

		if (resource.hasSyntaxByExtension()) {
			resourceTabs.addTab(NLS.str("tabs.code"), CodeContentPanel(panel, resource))
			return
		}

		// unknown file type, show both text and binary
		resourceTabs.addTab(NLS.str("tabs.code"), CodeContentPanel(panel, resource))
		resourceTabs.addTab(NLS.str("tabs.hex"), BinaryContentPanel(panel, resource))
	}

	override fun getCodeArea(): AbstractCodeArea? {
		val selectedCodePanel = getSelectedCodePanel()
		if (selectedCodePanel != null) {
			return selectedCodePanel.getCodeArea()
		}
		for (i in 0 until resourceTabs.getTabCount()) {
			val component = resourceTabs.getComponentAt(i)
			if (component is AbstractCodeContentPanel) {
				return component.getCodeArea()
			}
		}
		return null
	}

	override fun scrollToPos(pos: Int) {
		val selectedCodePanel = getSelectedCodePanel()
		if (selectedCodePanel != null) {
			selectedCodePanel.scrollToPos(pos)
			return
		}
		for (i in 0 until resourceTabs.getTabCount()) {
			val component = resourceTabs.getComponentAt(i)
			if (component is AbstractCodeContentPanel) {
				resourceTabs.setSelectedIndex(i)
				component.scrollToPos(pos)
				return
			}
		}
	}

	override fun getChildrenComponent(): Component {
		val selectedComponent = resourceTabs.getSelectedComponent()
		if (selectedComponent is AbstractCodeContentPanel) {
			return selectedComponent.getChildrenComponent()
		}
		return checkNotNull(selectedComponent)
	}

	override fun loadSettings() {
		for (i in 0 until resourceTabs.getTabCount()) {
			val component = resourceTabs.getComponentAt(i)
			if (component is ContentPanel) {
				component.loadSettings()
			}
		}
		updateUI()
	}

	override fun dispose() {
		for (i in 0 until resourceTabs.getTabCount()) {
			val component = resourceTabs.getComponentAt(i)
			if (component is ContentPanel) {
				component.dispose()
			}
		}
		super.dispose()
	}

	private fun getSelectedCodePanel(): AbstractCodeContentPanel? {
		val selectedComponent = resourceTabs.getSelectedComponent()
		if (selectedComponent is AbstractCodeContentPanel) {
			return selectedComponent
		}
		return null
	}
}

package kadx.gui.settings.ui

import kadx.api.plugins.gui.ISettingsGroup
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

/**
 * 设置页面中一组「标签 + 控件」行的容器。
 *
 * **做什么**：内部用 [GridBagLayout] 排版每一行（左侧标签、右侧控件），
 * 并统一处理提示文本、无障碍名称以及控件禁用时标签同步禁用。
 *
 * **为什么是 `open`**：[SubSettingsGroup] 需要继承它。
 */
open class SettingsGroup(private val title: String) : ISettingsGroup {

	private val panel: JPanel
	private val gridPanel: JPanel
	private val c: GridBagConstraints
	private var row: Int = 0

	init {
		gridPanel = JPanel(GridBagLayout())
		c = GridBagConstraints()
		c.insets = Insets(5, 5, 5, 5)
		c.weighty = 1.0

		panel = JPanel()
		panel.layout = BorderLayout(5, 5)
		panel.border = BorderFactory.createTitledBorder(title)
		panel.add(gridPanel, BorderLayout.PAGE_START)
	}

	fun addRow(label: String, comp: JComponent): JLabel = addRow(label, null, comp)

	fun addRow(label: String, tooltip: String?, comp: JComponent): JLabel {
		val rowLbl = JLabel(label)
		rowLbl.labelFor = comp
		rowLbl.horizontalAlignment = SwingConstants.LEFT
		if (tooltip != null) {
			rowLbl.toolTipText = tooltip
			comp.toolTipText = tooltip
		} else {
			comp.toolTipText = label
		}
		comp.accessibleContext.accessibleName = label

		c.gridy = row++
		c.gridx = 0
		c.gridwidth = 1
		c.anchor = GridBagConstraints.LINE_START
		c.weightx = 0.1
		c.fill = GridBagConstraints.LINE_START
		gridPanel.add(rowLbl, c)
		c.gridx = 1
		c.gridwidth = GridBagConstraints.REMAINDER
		c.anchor = GridBagConstraints.LINE_START
		c.weightx = 0.7
		c.fill = GridBagConstraints.LINE_START

		gridPanel.add(comp, c)
		comp.addPropertyChangeListener("enabled") { evt -> rowLbl.isEnabled = evt.newValue as Boolean }
		return rowLbl
	}

	fun end() {
		gridPanel.add(Box.createVerticalGlue())
	}

	override fun buildComponent(): JComponent = panel

	override fun getTitle(): String = title

	fun getGridPanel(): JPanel = gridPanel

	override fun toString(): String = title
}

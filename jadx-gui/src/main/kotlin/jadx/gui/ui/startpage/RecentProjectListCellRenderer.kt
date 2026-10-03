package jadx.gui.ui.startpage

import jadx.gui.utils.Icons
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Font
import java.awt.Graphics
import java.awt.Rectangle
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer
import javax.swing.UIManager
import javax.swing.plaf.basic.BasicButtonUI

/**
 * “最近项目”列表的单元格渲染器。
 *
 * **做什么**：展示项目名与绝对路径，右侧一个“移除”按钮；按钮在鼠标悬停时高亮。
 * [paint] 里会记录移除按钮的坐标，供列表的鼠标监听判断点击是否落在按钮上。
 *
 * **为什么保留 `ListCellRenderer` 覆写签名**：Swing 的 `JList` 按接口回调。
 */
class RecentProjectListCellRenderer(baseFont: Font) :
	JPanel(BorderLayout(5, 0)),
	ListCellRenderer<RecentProjectItem> {

	private val fileNameLabel: JLabel = JLabel()
	private val pathLabel: JLabel = JLabel()
	private val removeProjectBtn: JButton = JButton()

	private val defaultBackground = UIManager.getColor("List.background")
	private val defaultForeground = UIManager.getColor("List.foreground")

	private val selectedBackground = UIManager.getColor("List.selectionBackground")
	private val selectedForeground = UIManager.getColor("List.selectionForeground")

	private var removeBtnBounds: Rectangle? = null

	init {
		isOpaque = true
		border = BorderFactory.createEmptyBorder(5, 10, 5, 5)

		fileNameLabel.font = baseFont.deriveFont(Font.BOLD, baseFont.size.toFloat())

		pathLabel.font = baseFont.deriveFont(baseFont.size - 2f)
		pathLabel.foreground = UIManager.getColor("Label.disabledForeground")

		val textPanel = JPanel(BorderLayout())
		textPanel.isOpaque = false
		textPanel.add(fileNameLabel, BorderLayout.NORTH)
		textPanel.add(pathLabel, BorderLayout.SOUTH)

		removeProjectBtn.icon = Icons.CLOSE_INACTIVE
		removeProjectBtn.isOpaque = false
		removeProjectBtn.setUI(BasicButtonUI())
		removeProjectBtn.isContentAreaFilled = false
		removeProjectBtn.isFocusable = false
		removeProjectBtn.border = null
		removeProjectBtn.isBorderPainted = false

		add(textPanel, BorderLayout.CENTER)
		add(removeProjectBtn, BorderLayout.EAST)
	}

	override fun getListCellRendererComponent(
		list: JList<out RecentProjectItem>,
		value: RecentProjectItem,
		index: Int,
		isSelected: Boolean,
		cellHasFocus: Boolean,
	): Component {
		fileNameLabel.text = value.projectName
		pathLabel.text = value.absolutePath

		val isThisRemoveButtonHovered = index == StartPagePanel.hoveredRemoveBtnIndex
		removeProjectBtn.icon = if (isThisRemoveButtonHovered) Icons.CLOSE else Icons.CLOSE_INACTIVE
		removeProjectBtn.isRolloverEnabled = isThisRemoveButtonHovered

		if (isSelected) {
			background = selectedBackground
			fileNameLabel.foreground = selectedForeground
			pathLabel.foreground = selectedForeground.darker()
			removeProjectBtn.foreground = selectedForeground
		} else {
			background = defaultBackground
			fileNameLabel.foreground = defaultForeground
			pathLabel.foreground = UIManager.getColor("Label.disabledForeground")
			removeProjectBtn.foreground = defaultForeground
		}

		toolTipText = value.absolutePath
		return this
	}

	/**
	 * 覆写绘制以计算移除按钮的边界。
	 * 这对 `JList` 上的鼠标监听判断点击/悬停是否落在按钮上至关重要。
	 */
	override fun paint(g: Graphics) {
		super.paint(g)
		// 取边界前先确保按钮布局有效
		removeProjectBtn.doLayout()
		// 计算按钮相对于本渲染面板的边界
		val x = width - removeProjectBtn.width - border.getBorderInsets(this).right
		val y = (height - removeProjectBtn.height) / 2
		removeBtnBounds = Rectangle(x, y, removeProjectBtn.width, removeProjectBtn.height)
	}

	/**
	 * 返回移除按钮在渲染组件坐标系中的边界。
	 * 这对 `JList` 上的鼠标监听判断点击是否落在图标上至关重要。
	 */
	val removeIconBounds: Rectangle? get() = removeBtnBounds

	companion object {
		private const val serialVersionUID: Long = 5550591869239586857L
	}
}

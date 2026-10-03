/*
 * The MIT License (MIT)
 * Copyright (c) 2015 TERAI Atsuhiro
 * Copyright (c) 2024 Skylot
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package jadx.gui.ui.tab.dnd

import jadx.gui.settings.JadxSettings
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Insets
import java.awt.Point
import java.awt.Rectangle
import java.awt.image.BufferedImage
import javax.swing.JComponent
import javax.swing.UIManager

/**
 * 拖拽时显示在 glass pane 上的“幽灵”面板。
 *
 * **做什么**：绘制插入位置标记、随光标移动的标签副本（位图或彩色矩形）。
 *
 * **为什么不是 `data class`**：这是有状态的 Swing 组件。
 */
class TabDndGhostPane(dnd: TabDndController, settings: JadxSettings) : JComponent() {

	private val dnd: TabDndController = dnd
	private val lineRect = Rectangle()
	private val location = Point()
	private var ghostImage: BufferedImage? = null
	private val settings: JadxSettings = settings
	private var tabDndGhostType = TabDndGhostType.OUTLINE
	private var ghostSize: Dimension? = null
	private var ghostColor: Color = Color(0, 100, 255)
	private var insets: Insets = Insets(0, 0, 0, 0)

	init {
		loadSettings()
	}

	fun loadSettings() {
		val systemColor = UIManager.getColor("Component.focusColor")
		ghostColor = systemColor ?: Color(0, 100, 255)

		val ins = UIManager.getInsets("TabbedPane.tabInsets")
		insets = ins ?: Insets(0, 0, 0, 0)

		tabDndGhostType = settings.tabDndGhostType
	}

	fun setTargetRect(x: Int, y: Int, width: Int, height: Int) {
		lineRect.setBounds(x, y, width, height)
	}

	fun setGhostImage(ghostImage: BufferedImage?) {
		this.ghostImage = ghostImage
	}

	fun setGhostSize(ghostSize: Dimension) {
		ghostSize.setSize(ghostSize.width + insets.left + insets.right, ghostSize.height + insets.top + insets.bottom)
		this.ghostSize = ghostSize
	}

	fun setGhostType(tabDndGhostType: TabDndGhostType) {
		this.tabDndGhostType = tabDndGhostType
	}

	val ghostType: TabDndGhostType get() = tabDndGhostType

	fun setColor(color: Color) {
		ghostColor = color
	}

	val color: Color get() = ghostColor

	fun setPoint(pt: Point) {
		location.setLocation(pt)
	}

	override fun isOpaque(): Boolean = false

	override fun setVisible(v: Boolean) {
		super.setVisible(v)
		if (!v) {
			setTargetRect(0, 0, 0, 0)
			setGhostImage(null)
			setGhostSize(Dimension())
		}
	}

	override fun paintComponent(g: Graphics) {
		val g2 = g.create() as Graphics2D
		dnd.onPaintGlassPane(g2)
		renderMark(g2)
		renderGhost(g2)
		g2.dispose()
	}

	private fun renderGhost(g: Graphics2D) {
		when (tabDndGhostType) {
			TabDndGhostType.IMAGE -> {
				g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f))
				val image = ghostImage ?: return
				val x = location.getX() - image.getWidth(this) / 2.0
				val y = location.getY() - image.getHeight(this) / 2.0
				g.drawImage(image, x.toInt(), y.toInt(), this)
			}

			TabDndGhostType.OUTLINE -> {
				g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.2f))
				val size = ghostSize ?: return
				val x = location.getX() - size.getWidth() / 2.0
				val y = location.getY() - size.getHeight() / 2.0
				g.setPaint(ghostColor)
				g.fillRect(x.toInt(), y.toInt(), size.width, size.height)
			}

			TabDndGhostType.TARGET_MARK -> {
			}
		}
	}

	private fun renderMark(g: Graphics2D) {
		g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.7f))
		g.setPaint(ghostColor)
		g.fill(lineRect)
	}
}

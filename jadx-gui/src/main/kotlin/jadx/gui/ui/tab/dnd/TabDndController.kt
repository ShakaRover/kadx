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
import jadx.gui.ui.tab.TabbedPane
import java.awt.Component
import java.awt.Dimension
import java.awt.Graphics2D
import java.awt.GraphicsConfiguration
import java.awt.GraphicsDevice
import java.awt.GraphicsEnvironment
import java.awt.Point
import java.awt.Rectangle
import java.awt.dnd.DnDConstants
import java.awt.dnd.DragSource
import java.awt.dnd.DropTarget
import java.awt.image.BufferedImage
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JTabbedPane
import javax.swing.SwingUtilities
import javax.swing.plaf.metal.MetalTabbedPaneUI

/**
 * 标签页拖拽（Drag & Drop）控制器。
 *
 * **做什么**：负责识别标签拖拽手势、在 glass pane 上绘制“幽灵”预览与插入标记、
 * 计算目标位置，并在释放时交换标签顺序。
 *
 * **线程模型**：全部逻辑运行在 EDT（AWT 拖拽事件线程即 EDT），保持原 Swing 实现不变。
 *
 * **为什么不是 `data class`**：这是有状态的控制器。
 */
class TabDndController(tabbedPane: TabbedPane, settings: JadxSettings) {

	private val pane: JTabbedPane

	private val tabDndGhostPane: TabDndGhostPane

	/** 正在被拖拽的标签下标（-1 表示无）。 */
	var dragTabIndex = -1

	/** 是否绘制随光标移动的半透明标签副本。 */
	private var drawGhost = true

	/** 是否绘制滚动触发区域（仅调试用）。 */
	private var paintScrollTriggerAreas = false

	private var rectBackward = Rectangle()
	private var rectForward = Rectangle()

	private var isDragging = false

	init {
		tabbedPane.setDnd(this)
		this.pane = tabbedPane

		tabDndGhostPane = TabDndGhostPane(this, settings)

		DropTarget(tabDndGhostPane, DnDConstants.ACTION_COPY_OR_MOVE, TabDndTargetListener(this), true)
		DragSource.getDefaultDragSource().createDefaultDragGestureRecognizer(
			tabbedPane,
			DnDConstants.ACTION_COPY_OR_MOVE,
			TabDndGestureListener(this),
		)
	}

	/**
	 * 检查是否正在靠近边缘，若是则通过程序化点击系统的滚动按钮来滚动。
	 *
	 * @param glassPt 光标在 TabbedPane 坐标系中的位置。
	 */
	fun scrollIfNeeded(glassPt: Point) {
		val r = tabAreaBounds
		val isHorizontal = isHorizontalTabPlacement(pane.getTabPlacement())

		// 尽量不同时计算两个方向，优先向前。
		if (isHorizontal) {
			rectForward.setBounds(
				r.x + r.width - SCROLL_AREA_SIZE - SCROLL_AREA_EXTRA,
				r.y,
				SCROLL_AREA_SIZE + SCROLL_AREA_EXTRA,
				r.height,
			)
		} else {
			rectForward.setBounds(
				r.x,
				r.y + r.height - SCROLL_AREA_SIZE - SCROLL_AREA_EXTRA,
				r.width,
				SCROLL_AREA_SIZE + SCROLL_AREA_EXTRA,
			)
		}
		rectForward = SwingUtilities.convertRectangle(pane.getParent(), rectForward, tabDndGhostPane)
		if (rectForward.contains(glassPt)) {
			clickScrollButton(ACTION_SCROLL_FORWARD)
		}

		// 向后。
		if (isHorizontal) {
			rectBackward.setBounds(r.x, r.y, SCROLL_AREA_SIZE, r.height)
		} else {
			rectBackward.setBounds(r.x, r.y, r.width, SCROLL_AREA_SIZE)
		}
		rectBackward = SwingUtilities.convertRectangle(pane.getParent(), rectBackward, tabDndGhostPane)
		if (rectBackward.contains(glassPt)) {
			clickScrollButton(ACTION_SCROLL_BACKWARD)
		}
	}

	private fun clickScrollButton(actionKey: String) {
		var forwardButton: JButton? = null
		var backwardButton: JButton? = null
		for (c in pane.getComponents()) {
			if (c is JButton) {
				if (forwardButton == null) {
					forwardButton = c
				} else {
					backwardButton = c
					break
				}
			}
		}
		val scrollButton = if (ACTION_SCROLL_FORWARD == actionKey) forwardButton else backwardButton
		if (scrollButton != null && scrollButton.isEnabled) {
			scrollButton.doClick()
		}
	}

	/**
	 * 根据光标位置查找目标标签下标。
	 * 若光标位于某个标签的前半部分，返回该标签下标；位于后半部分则返回下一个下标。
	 *
	 * @param glassPt 光标在 TabbedPane 坐标系中的位置。
	 * @return 目标标签下标。
	 */
	fun getTargetTabIndex(glassPt: Point): Int {
		val tabPt = SwingUtilities.convertPoint(tabDndGhostPane, glassPt, pane)
		val isHorizontal = isHorizontalTabPlacement(pane.getTabPlacement())
		for (i in 0 until pane.getTabCount()) {
			val r = pane.getBoundsAt(i)

			// 前半部分。
			if (isHorizontal) {
				r.width = r.width / 2 + 1
			} else {
				r.height = r.height / 2 + 1
			}
			if (r.contains(tabPt)) {
				return i
			}

			// 后半部分。
			if (isHorizontal) {
				r.x = r.x + r.width
			} else {
				r.y = r.y + r.height
			}
			if (r.contains(tabPt)) {
				return i + 1
			}
		}

		val count = pane.getTabCount()
		if (count == 0) {
			return -1
		}
		val lastRect = pane.getBoundsAt(count - 1)
		val d = if (isHorizontal) Point(1, 0) else Point(0, 1)
		lastRect.translate(lastRect.width * d.x, lastRect.height * d.y)
		return if (lastRect.contains(tabPt)) count else -1
	}

	fun swapTabs(oldIdx: Int, newIdx: Int) {
		if (newIdx < 0 || oldIdx == newIdx) {
			return
		}
		val cmp = pane.getComponentAt(oldIdx)
		val tab = pane.getTabComponentAt(oldIdx)
		val title = pane.getTitleAt(oldIdx)
		val icon = pane.getIconAt(oldIdx)
		val tip = pane.getToolTipTextAt(oldIdx)
		val isEnabled = pane.isEnabledAt(oldIdx)
		val insertIdx = if (oldIdx > newIdx) newIdx else (newIdx - 1)
		pane.remove(oldIdx)
		pane.insertTab(title, icon, cmp, tip, insertIdx)
		pane.setEnabledAt(insertIdx, isEnabled)
		if (isEnabled) {
			pane.setSelectedIndex(insertIdx)
		}
		pane.setTabComponentAt(insertIdx, tab)
	}

	fun updateTargetMark(tabIdx: Int) {
		val isSideNeighbor = tabIdx < 0 || dragTabIndex == tabIdx || tabIdx == dragTabIndex + 1
		if (isSideNeighbor) {
			tabDndGhostPane.setTargetRect(0, 0, 0, 0)
			return
		}
		val boundsRect = pane.getBoundsAt(Math.max(0, tabIdx - 1))
		val r = SwingUtilities.convertRectangle(pane, boundsRect, tabDndGhostPane)
		val a = Math.min(tabIdx, 1)
		if (isHorizontalTabPlacement(pane.getTabPlacement())) {
			tabDndGhostPane.setTargetRect(
				r.x + r.width * a - DROP_TARGET_MARK_SIZE / 2,
				r.y,
				DROP_TARGET_MARK_SIZE,
				r.height,
			)
		} else {
			tabDndGhostPane.setTargetRect(
				r.x,
				r.y + r.height * a - DROP_TARGET_MARK_SIZE / 2,
				r.width,
				DROP_TARGET_MARK_SIZE,
			)
		}
	}

	fun initGlassPane(tabPt: Point) {
		pane.getRootPane().setGlassPane(tabDndGhostPane)
		if (drawGhost) {
			val c = pane.getTabComponentAt(dragTabIndex) ?: return
			val d = c.getPreferredSize()
			when (tabDndGhostPane.ghostType) {
				TabDndGhostType.IMAGE -> {
					val env = GraphicsEnvironment.getLocalGraphicsEnvironment()
					val device: GraphicsDevice = env.getDefaultScreenDevice()
					val config: GraphicsConfiguration = device.getDefaultConfiguration()
					val image = config.createCompatibleImage(d.width, d.height, BufferedImage.TRANSLUCENT)
					val g2 = image.createGraphics()
					SwingUtilities.paintComponent(g2, c, tabDndGhostPane, 0, 0, d.width, d.height)
					g2.dispose()
					tabDndGhostPane.setGhostImage(image)
					pane.setTabComponentAt(dragTabIndex, c)
				}

				TabDndGhostType.OUTLINE -> {
					tabDndGhostPane.setGhostSize(d)
				}

				TabDndGhostType.TARGET_MARK -> {
				}
			}
		}
		val glassPt = SwingUtilities.convertPoint(pane, tabPt, tabDndGhostPane)
		tabDndGhostPane.setPoint(glassPt)
		tabDndGhostPane.setVisible(true)
	}

	val tabAreaBounds: Rectangle get() {
		val tabbedRect = pane.getBounds()
		val selectedComponent = pane.getSelectedComponent()
		val compRect = if (selectedComponent != null) selectedComponent.getBounds() else Rectangle()
		val tabPlacement = pane.getTabPlacement()
		if (isHorizontalTabPlacement(tabPlacement)) {
			tabbedRect.height = tabbedRect.height - compRect.height
			if (tabPlacement == JTabbedPane.BOTTOM) {
				tabbedRect.y += compRect.y + compRect.height
			}
		} else {
			tabbedRect.width = tabbedRect.width - compRect.width
			if (tabPlacement == JTabbedPane.RIGHT) {
				tabbedRect.x += compRect.x + compRect.width
			}
		}
		tabbedRect.grow(2, 2)
		return tabbedRect
	}

	fun onPaintGlassPane(g: Graphics2D) {
		val isScrollLayout = pane.getTabLayoutPolicy() == JTabbedPane.SCROLL_TAB_LAYOUT
		if (isScrollLayout && paintScrollTriggerAreas) {
			g.setPaint(tabDndGhostPane.color)
			g.fill(rectBackward)
			g.fill(rectForward)
		}
	}

	fun onStartDrag(pt: Point): Boolean {
		setDragging(true)
		val idx = pane.indexAtLocation(pt.x, pt.y)
		val selIdx = pane.getSelectedIndex()
		val isTabRunsRotated =
			pane.getUI() !is MetalTabbedPaneUI && pane.getTabLayoutPolicy() == JTabbedPane.WRAP_TAB_LAYOUT && idx != selIdx
		dragTabIndex = if (isTabRunsRotated) selIdx else idx
		if (dragTabIndex >= 0 && pane.isEnabledAt(dragTabIndex)) {
			initGlassPane(pt)
			return true
		}

		return false
	}

	fun loadSettings() {
		tabDndGhostPane.loadSettings()
	}

	fun isDragging(): Boolean = isDragging

	fun setDragging(dragging: Boolean) {
		isDragging = dragging
	}

	val dndGhostPane: TabDndGhostPane? get() = tabDndGhostPane

	companion object {
		private const val DROP_TARGET_MARK_SIZE = 4
		private const val SCROLL_AREA_SIZE = 30
		private const val SCROLL_AREA_EXTRA = 30 // 让滚动按钮区域稍大一点。
		private const val ACTION_SCROLL_FORWARD = "scrollTabsForwardAction"
		private const val ACTION_SCROLL_BACKWARD = "scrollTabsBackwardAction"

		fun isHorizontalTabPlacement(tabPlacement: Int): Boolean = tabPlacement == JTabbedPane.TOP || tabPlacement == JTabbedPane.BOTTOM
	}
}

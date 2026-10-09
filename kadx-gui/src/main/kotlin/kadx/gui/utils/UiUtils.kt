@file:Suppress("ktlint:standard:property-naming")

package kadx.gui.utils

import kadx.commons.app.KadxCommonEnv
import kadx.commons.app.KadxSystemInfo
import kadx.core.dex.info.AccessInfo
import kadx.core.dex.instructions.args.ArgType
import kadx.core.utils.StringUtils
import kadx.core.utils.Utils
import kadx.core.utils.exceptions.KadxRuntimeException
import kadx.gui.jobs.ITaskProgress
import kadx.gui.ui.codearea.AbstractCodeArea
import org.intellij.lang.annotations.MagicConstant
import org.jetbrains.annotations.TestOnly
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.awt.Color
import java.awt.Component
import java.awt.Image
import java.awt.MouseInfo
import java.awt.Point
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.Window
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.awt.event.ActionEvent
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.io.File
import java.net.URL
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.Icon
import javax.swing.ImageIcon
import javax.swing.JComponent
import javax.swing.JOptionPane
import javax.swing.JTextField
import javax.swing.JTree
import javax.swing.KeyStroke
import javax.swing.RootPaneContainer
import javax.swing.SwingUtilities
import javax.swing.tree.TreeNode
import javax.swing.tree.TreePath

/**
 * GUI 通用工具集合（图标、剪贴板、内存、线程与 Swing 辅助）。
 *
 * **做什么**：集中放置 GUI 各处都会用到的静态工具方法。
 *
 * **为什么用 `object` + `@JvmStatic`/`@JvmField`**：原 Java 全部是静态成员，
 * 这样能保证 Java 调用方（以及已迁移的 Kotlin 调用方）零改动。
 *
 * **Swing 线程模型**：[uiRun]/[uiRunAndWait]/[bgRun] 使用 `SwingUtilities` 与单线程
 * Executor 投递任务。
 */
object UiUtils {

	private val LOG: Logger = LoggerFactory.getLogger(UiUtils::class.java)

	/** 是否开启 GUI 调试断言（线程守卫等）。 */
	val KADX_GUI_DEBUG: Boolean = KadxCommonEnv.getBool("KADX_GUI_DEBUG", false)

	/**
	 * 我们尽量保持空闲的最小堆内存（字节）。
	 *
	 * 低于该值应用可能耗尽堆，导致 GC “发疯”（所有核 100% 且界面无响应）。
	 * 因为最大堆在 JVM 启动后固定，所以这里可以一次性计算并缓存。
	 */
	val MIN_FREE_MEMORY: Long = calculateMinFreeMemory()

	/** 什么都不做、且 `toString()` 有意义的 [Runnable]。 */
	val EMPTY_RUNNABLE: Runnable = object : Runnable {
		override fun run() {
			// 空实现
		}

		override fun toString(): String = "EMPTY_RUNNABLE"
	}

	/** 单线程后台执行器：保证所有后台任务按提交顺序串行执行。 */
	private val BACKGROUND_THREAD: ExecutorService = Executors.newSingleThreadExecutor(Utils.simpleThreadFactory("utils-bg"))

	/** 加载 SVG 图标（找不到时抛异常）。 */
	fun openSvgIcon(name: String): ImageIcon {
		val iconPath = "icons/$name.svg"
		val icon = com.formdev.flatlaf.extras.FlatSVGIcon(iconPath)
		val found = try {
			icon.hasFound()
		} catch (e: Exception) {
			throw KadxRuntimeException("Failed to load icon: $iconPath", e)
		}
		if (!found) {
			throw KadxRuntimeException("Icon not found: $iconPath")
		}
		return icon
	}

	/** 加载 16x16 PNG 图标（找不到时抛异常）。 */
	fun openIcon(name: String): ImageIcon {
		val iconPath = "/icons-16/$name.png"
		val resource: URL = UiUtils::class.java.getResource(iconPath)
			?: throw KadxRuntimeException("Icon not found: $iconPath")
		return ImageIcon(resource)
	}

	/** 加载任意图片资源（找不到时抛异常）。 */
	fun openImage(path: String): Image {
		val resource: URL = UiUtils::class.java.getResource(path)
			?: throw KadxRuntimeException("Image not found: $path")
		return Toolkit.getDefaultToolkit().createImage(resource)
	}

	/** 把 [Runnable] 绑定到快捷键。 */
	fun addKeyBinding(comp: JComponent, key: KeyStroke, id: String, action: Runnable) {
		addKeyBinding(
			comp,
			key,
			id,
			object : AbstractAction() {
				override fun actionPerformed(e: ActionEvent) {
					action.run()
				}
			},
		)
	}

	/** 把 [Action] 绑定到快捷键。 */
	fun addKeyBinding(comp: JComponent, key: KeyStroke, id: String, action: Action) {
		comp.getInputMap().put(key, id)
		comp.getActionMap().put(id, action)
	}

	/** 移除快捷键绑定。 */
	fun removeKeyBinding(comp: JComponent, key: KeyStroke, id: String) {
		comp.getInputMap().remove(key)
		comp.getActionMap().remove(id)
	}

	/** `名称 类型` 形式的纯文本描述。 */
	fun typeFormat(name: String, type: ArgType): String = name + " " + typeStr(type)

	/** `名称 类型` 形式的 HTML 描述（名称正常显示，类型灰显）。 */
	fun typeFormatHtml(name: String, type: ArgType): String = wrapHtml(escapeHtml(name) + ' ' + fadeHtml(escapeHtml(typeStr(type))))

	/** 用灰色 `<span>` 包裹文本。 */
	fun fadeHtml(htmlStr: String): String = "<span style='color:#888888;'>$htmlStr</span>" // TODO: 从主题取色

	/** 用 `<html><body><nobr>` 包裹文本（禁止换行）。 */
	fun wrapHtml(htmlStr: String): String = "<html><body><nobr>$htmlStr</nobr></body></html>"

	/** 转义 HTML 中的尖括号。 */
	fun escapeHtml(str: String): String = str.replace("<", "&lt;").replace(">", "&gt;")

	/** 路径被截断时使用的省略号。 */
	private const val CUT_STR_REPLACE = "..."

	/**
	 * 限制字符串长度：优先保留文件名的末尾部分，仍超长则截断尾部。
	 */
	fun limitStringLength(str: String, maxLength: Int): String {
		var s = str
		if (s.length <= maxLength) {
			return s
		}
		val fileSepChar = File.separatorChar
		val firstFileSep = s.indexOf(fileSepChar)
		if (firstFileSep != -1) {
			// 去掉中间路径部分，保留盘符/根目录与文件名
			val lastFileSep = s.lastIndexOf(fileSepChar)
			if (firstFileSep == lastFileSep) {
				// 只有一个路径分隔符 -> 从分隔符前截断
				s = CUT_STR_REPLACE + s.substring(lastFileSep - 1)
			} else {
				// 保留头尾，中间用省略号
				s = s.substring(0, firstFileSep + 1) + CUT_STR_REPLACE + s.substring(lastFileSep)
			}
			if (s.length < maxLength) {
				return s
			}
		}
		// 默认截断尾部
		return s.substring(0, maxLength - CUT_STR_REPLACE.length) + CUT_STR_REPLACE
	}

	/** 把 [ArgType] 渲染成简短可读的类型字符串（用于树节点/提示）。 */
	fun typeStr(type: ArgType?): String {
		if (type == null) {
			return "null"
		}
		if (type.isObject()) {
			if (type.isGenericType()) {
				return type.getObject()
			}
			val wt = type.getWildcardType()
			if (wt != null) {
				val bound = checkNotNull(type.getWildcardBound())
				if (bound == ArgType.WildcardBound.UNBOUND) {
					return bound.str
				}
				return bound.str + typeStr(wt)
			}
			val objName = objectShortName(type.getObject())
			val outerType = type.getOuterType()
			if (outerType != null) {
				return typeStr(outerType) + '.' + objName
			}
			val genericTypes = type.getGenericTypes()
			if (genericTypes != null) {
				val generics = Utils.listToString(genericTypes, ", ") { typeStr(it) }
				return objName + '<' + generics + '>'
			}
			return objName
		}
		if (type.isArray()) {
			return typeStr(type.getArrayElement()) + "[]"
		}
		return type.toString()
	}

	/** 取类名的简单名（去掉包名）。 */
	private fun objectShortName(obj: String): String {
		val dot = obj.lastIndexOf('.')
		if (dot != -1) {
			return obj.substring(dot + 1)
		}
		return obj
	}

	/** 根据访问标志与 static/final 标记组合出带角标的图标。 */
	fun makeIcon(af: AccessInfo, pub: Icon, pri: Icon, pro: Icon, def: Icon): OverlayIcon {
		val icon: Icon = if (af.isPublic()) {
			pub
		} else if (af.isPrivate()) {
			pri
		} else if (af.isProtected()) {
			pro
		} else {
			def
		}
		val overIcon = OverlayIcon(icon)
		if (af.isFinal()) {
			overIcon.add(Icons.FINAL)
		}
		if (af.isStatic()) {
			overIcon.add(Icons.STATIC)
		}
		return overIcon
	}

	/** @return 最大堆的 20%，但不超过 512 MB（字节） */
	private fun calculateMinFreeMemory(): Long {
		val runtime = Runtime.getRuntime()
		val minFree = (runtime.maxMemory() * 0.2).toLong()
		return Math.min(minFree, 512 * 1024L * 1024L)
	}

	/** 当前空闲内存（含未分配部分）是否高于 [MIN_FREE_MEMORY]。 */
	val isFreeMemoryAvailable: Boolean get() {
		val runtime = Runtime.getRuntime()
		val maxMemory = runtime.maxMemory()
		val totalFree = runtime.freeMemory() + (maxMemory - runtime.totalMemory())
		return totalFree > MIN_FREE_MEMORY
	}

	/** 生成堆内存使用情况的可读描述。 */
	fun memoryInfo(): String {
		val runtime = Runtime.getRuntime()
		val maxMemory = runtime.maxMemory()
		val allocatedMemory = runtime.totalMemory()
		val freeMemory = runtime.freeMemory()

		return "heap: " + format(allocatedMemory - freeMemory) +
			", allocated: " + format(allocatedMemory) +
			", free: " + format(freeMemory) +
			", total free: " + format(freeMemory + maxMemory - allocatedMemory) +
			", max: " + format(maxMemory)
	}

	private fun format(mem: Long): String = (mem / (1024L * 1024L).toDouble()).toLong().toString() + "MB"

	/** 把文本写入系统剪贴板（使用自定义 owner，不持有引用）。 */
	fun setClipboardString(text: String) {
		try {
			val clipboard = Toolkit.getDefaultToolkit().getSystemClipboard()
			val transferable: Transferable = StringSelection(text)
			clipboard.setContents(transferable, null)
			LOG.debug("String '{}' copied to clipboard", text)
		} catch (e: Exception) {
			LOG.error("Failed copy string '{}' to clipboard", text, e)
		}
	}

	/** 设置窗口图标列表。 */
	fun setWindowIcons(window: Window) {
		val icons: MutableList<Image> = ArrayList()
		icons.add(openImage("/logos/kadx-logo-16px.png"))
		icons.add(openImage("/logos/kadx-logo-32px.png"))
		icons.add(openImage("/logos/kadx-logo-48px.png"))
		icons.add(openImage("/logos/kadx-logo.png"))
		window.setIconImages(icons)
	}

	/** Ctrl（macOS 上为 Command）修饰键的掩码。 */
	@field:MagicConstant(flagsFromClass = InputEvent::class)
	val CTRL_BNT_KEY: Int = getCtrlButtonMask()

	/**
	 * 计算平台上的 Ctrl（macOS 上为 Command）修饰键掩码。
	 *
	 * 注意：这里必须用一个与 [ctrlButton] 不同名的函数。原 Java 是 `getCtrlButton()`（算掩码）
	 * 加 `ctrlButton()`（返回常量），Kotlin 转换时把前者改名成了 `ctrlButton` 属性，于是
	 * [CTRL_BNT_KEY] 的初始化表达式 `ctrlButton()` 绑到了返回 [CTRL_BNT_KEY] 的那个函数上，
	 * 读到尚未初始化的 0 —— 结果所有带 Ctrl 的快捷键都退化成不带 Ctrl（Ctrl+O 显示成 O、
	 * Ctrl+Shift+O 显示成 Shift+O），[isCtrlDown] 也永远为 false。
	 */
	@Suppress("DEPRECATION")
	private fun getCtrlButtonMask(): Int = if (KadxSystemInfo.IS_MAC) {
		Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()
	} else {
		InputEvent.CTRL_DOWN_MASK
	}

	/** 获取平台的 Ctrl 修饰键掩码。 */
	@MagicConstant(flagsFromClass = InputEvent::class)
	fun ctrlButton(): Int = CTRL_BNT_KEY

	/** 判断按键事件是否仅按下了 Ctrl。 */
	fun isCtrlDown(keyEvent: KeyEvent): Boolean = keyEvent.getModifiersEx() == CTRL_BNT_KEY

	/** 给窗口注册 Esc 快捷键：按下即释放窗口。 */
	fun <T> addEscapeShortCutToDispose(window: T) where T : Window, T : RootPaneContainer {
		val stroke = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0)
		window.getRootPane().registerKeyboardAction({ window.dispose() }, stroke, JComponent.WHEN_IN_FOCUSED_WINDOW)
	}

	/**
	 * 取鼠标位置处最近的光标偏移。
	 *
	 * @return 出错时返回 -1
	 */
	@Suppress("DEPRECATION")
	fun getOffsetAtMousePosition(codeArea: AbstractCodeArea): Int = try {
		val mousePos = getMousePosition(codeArea)
		codeArea.viewToModel(mousePos)
	} catch (e: Exception) {
		LOG.error("Failed to get offset at mouse position", e)
		-1
	}

	/** 取相对于 [comp] 的鼠标坐标。 */
	fun getMousePosition(comp: Component): Point {
		val pos = MouseInfo.getPointerInfo().getLocation()
		SwingUtilities.convertPointFromScreen(pos, comp)
		return pos
	}

	/** 取鼠标事件下方的树节点（不在行内则返回 null）。 */
	fun getTreeNodeUnderMouse(tree: JTree, mouseEvent: MouseEvent): TreeNode? {
		val path: TreePath = tree.getClosestPathForLocation(mouseEvent.getX(), mouseEvent.getY()) ?: return null
		// 只允许「最近」节点位于该项所在行的右侧
		val pathBounds: Rectangle? = tree.getPathBounds(path)
		if (pathBounds != null) {
			val y = mouseEvent.getY()
			if (y < pathBounds.y || y > pathBounds.y + pathBounds.height) {
				return null
			}
			if (mouseEvent.getX() < pathBounds.x) {
				// 排除展开/折叠事件
				return null
			}
		}
		val obj = path.getLastPathComponent()
		if (obj is TreeNode) {
			tree.setSelectionPath(path)
			return obj
		}
		return null
	}

	fun showMessageBox(parent: Component?, msg: String) {
		JOptionPane.showMessageDialog(parent, msg)
	}

	fun errorMessage(parent: Component?, message: String) {
		errorMessage(parent, NLS.str("message.errorTitle"), message)
	}

	fun errorMessage(parent: Component?, title: String, message: String) {
		LOG.error(message)
		JOptionPane.showMessageDialog(parent, message, title, JOptionPane.ERROR_MESSAGE)
	}

	/** 把文本复制到剪贴板（将 [StringSelection] 同时作为 owner）。 */
	fun copyToClipboard(text: String?) {
		if (StringUtils.isEmpty(text)) {
			return
		}
		val textStr = text ?: return
		try {
			val clipboard = Toolkit.getDefaultToolkit().getSystemClipboard()
			val selection = StringSelection(textStr)
			clipboard.setContents(selection, selection)
		} catch (e: Exception) {
			LOG.error("Failed copy text to clipboard", e)
		}
	}

	/**
	 * Clipboard 的 owner 字段可能持有 CodeArea 引用，会阻止整个 kadx 对象树被 GC，
	 * 造成内存泄漏。这里通过设置一个空 selection 来主动放弃所有权。
	 */
	fun resetClipboardOwner() {
		try {
			val clipboard: Clipboard? = Toolkit.getDefaultToolkit().getSystemSelection()
			if (clipboard != null) {
				val selection = StringSelection("")
				clipboard.setContents(selection, selection)
			}
		} catch (e: Exception) {
			LOG.error("Failed to reset clipboard owner", e)
		}
	}

	/** 计算任务进度百分比（0..100）。 */
	fun calcProgress(taskProgress: ITaskProgress): Int = calcProgress(taskProgress.progress().toLong(), taskProgress.total().toLong())

	fun calcProgress(done: Long, total: Long): Int {
		if (done > total) {
			LOG.debug("Task progress has invalid values: done={}, total={}", done, total)
			return 100
		}
		return Math.round(done * 100 / total.toFloat())
	}

	fun sleep(ms: Int) {
		try {
			Thread.sleep(ms.toLong())
		} catch (e: InterruptedException) {
			// 忽略中断
		}
	}

	/** 在 UI 线程异步执行（保持原 `SwingUtilities.invokeLater` 语义）。 */
	fun uiRun(runnable: Runnable) {
		SwingUtilities.invokeLater(runnable)
	}

	/** 在 UI 线程同步执行；已在 UI 线程则直接执行。 */
	fun uiRunAndWait(runnable: Runnable) {
		if (SwingUtilities.isEventDispatchThread()) {
			runnable.run()
			return
		}
		try {
			SwingUtilities.invokeAndWait(runnable)
		} catch (e: InterruptedException) {
			LOG.warn("UI thread interrupted, runnable: {}", runnable, e)
		} catch (e: Exception) {
			throw RuntimeException(e)
		}
	}

	/**
	 * 在后台线程执行任务。
	 * 使用单线程，保证所有任务按顺序执行。
	 */
	fun bgRun(runnable: Runnable) {
		BACKGROUND_THREAD.execute(runnable)
	}

	/** 调试模式下断言当前处于 UI 线程。 */
	fun uiThreadGuard() {
		if (KADX_GUI_DEBUG && !SwingUtilities.isEventDispatchThread()) {
			LOG.warn("Expect UI thread, got: {}", Thread.currentThread(), KadxRuntimeException())
		}
	}

	/** 调试模式下断言当前不处于 UI 线程。 */
	fun notUiThreadGuard() {
		if (KADX_GUI_DEBUG && SwingUtilities.isEventDispatchThread()) {
			LOG.warn("Expect background thread, got: {}", Thread.currentThread(), KadxRuntimeException())
		}
	}

	/** 调试用的周期定时器（仅在 DEBUG 日志开启时生效）。 */
	@TestOnly
	fun debugTimer(periodInSeconds: Int, action: Runnable) {
		if (!LOG.isDebugEnabled) {
			return
		}
		val timer = Timer()
		timer.scheduleAtFixedRate(
			object : TimerTask() {
				override fun run() {
					action.run()
				}
			},
			0,
			periodInSeconds * 1000L,
		)
	}

	/** 打印当前调用栈（仅测试/调试用）。 */
	@TestOnly
	fun printStackTrace(label: String) {
		LOG.debug("StackTrace: {}", label, Exception(label))
	}

	/** 按感知亮度（0..1）判断背景是否为深色。 */
	fun isDarkTheme(background: Color): Boolean {
		val brightness = (
			background.red * 0.299 +
				background.green * 0.587 +
				background.blue * 0.114
			) / 255
		return brightness < 0.5
	}

	/**
	 * 在不改变色相与饱和度的前提下调整颜色亮度。
	 *
	 * factor > 1.0 变亮，< 1.0 变暗，= 1.0 不变；亮度上限封顶为 1.0。
	 */
	fun adjustBrightness(color: Color, factor: Float): Color {
		val hsb = Color.RGBtoHSB(color.red, color.green, color.blue, null)
		hsb[2] = Math.min(1.0f, hsb[2] * factor) // 调整亮度
		return Color.getHSBColor(hsb[0], hsb[1], hsb[2])
	}

	/** 把输入框标记/取消标记为错误状态。 */
	fun highlightAsErrorField(field: JTextField, isError: Boolean) {
		if (isError) {
			field.putClientProperty("JComponent.outline", "error")
		} else {
			field.putClientProperty("JComponent.outline", "")
		}
		field.repaint()
	}

	/** 浮点数近似相等判断。 */
	fun nearlyEqual(a: Float, b: Float): Boolean = Math.abs(a - b) < 1E-6f
}

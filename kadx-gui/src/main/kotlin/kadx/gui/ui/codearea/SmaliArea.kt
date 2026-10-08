package kadx.gui.ui.codearea

import kadx.api.ICodeInfo
import kadx.gui.device.debugger.BreakpointManager
import kadx.gui.device.debugger.DbgUtils
import kadx.gui.jobs.IBackgroundTask
import kadx.gui.jobs.LoadTask
import kadx.gui.treemodel.JClass
import kadx.gui.treemodel.JNode
import kadx.gui.treemodel.TextNode
import kadx.gui.ui.codearea.sync.CodeAreaSyncee
import kadx.gui.ui.codearea.sync.CodeAreaSyncer
import kadx.gui.ui.codearea.sync.CodeAreaSyncerAbstractFactory
import kadx.gui.ui.codearea.sync.SmaliSyncer
import kadx.gui.ui.panel.ContentPanel
import kadx.gui.utils.UiUtils
import org.fife.ui.rsyntaxtextarea.FoldingAwareIconRowHeader
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea
import org.fife.ui.rsyntaxtextarea.RSyntaxTextAreaEditorKit
import org.fife.ui.rsyntaxtextarea.RSyntaxTextAreaUI
import org.fife.ui.rsyntaxtextarea.RSyntaxUtilities
import org.fife.ui.rsyntaxtextarea.Style
import org.fife.ui.rsyntaxtextarea.SyntaxConstants
import org.fife.ui.rsyntaxtextarea.SyntaxScheme
import org.fife.ui.rtextarea.Gutter
import org.fife.ui.rtextarea.GutterIconInfo
import org.fife.ui.rtextarea.IconRowHeader
import org.fife.ui.rtextarea.RTextArea
import org.fife.ui.rtextarea.RTextAreaUI
import org.slf4j.LoggerFactory
import java.awt.Color
import java.awt.Font
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.beans.PropertyChangeListener
import javax.swing.AbstractAction
import javax.swing.KeyStroke
import javax.swing.UIManager
import javax.swing.text.BadLocationException
import javax.swing.text.EditorKit
import javax.swing.text.JTextComponent

/**
 * Smali 代码区（普通 Smali 或 Dalvik 字节码视图）。
 *
 * **做什么**：使用 [SmaliModel] 抽象两种显示模式：
 * - [NormalModel]：展示类的 Smali 反汇编文本；
 * - [DebugModel]：展示 Dalvik 字节码，并支持断点、运行行高亮。
 *
 * **为什么用模型抽象**：两种模式的加载/卸载与字体来源不同，但代码区行为一致。
 */
class SmaliArea internal constructor(contentPanel: ContentPanel, node: JClass, showBytecode: Boolean) :
	AbstractCodeArea(contentPanel, node),
	CodeAreaSyncerAbstractFactory,
	CodeAreaSyncee {

	private val textNode: JNode
	private lateinit var model: SmaliModel

	init {
		setCodeFoldingEnabled(true)
		this.textNode = TextNode(node.getName())
		this.model = if (showBytecode) DebugModel() else NormalModel(this)
		setUnLoaded()
		load()
	}

	override fun getLoadTask(): IBackgroundTask = LoadTask<String>({ model.loadCode() }) { code ->
		model.loadUI(code)
		setCaretPosition(0)
		setLoaded()
	}

	override fun getCodeInfo(): ICodeInfo = ICodeInfo.EMPTY

	override fun refresh() {
		load()
	}

	override fun getNode(): JNode {
		// 该区域只包含 smali，不包含其他节点属性
		return textNode
	}

	val isShowingDalvikBytecode: Boolean get() = model is DebugModel

	override fun getJClass(): JClass = node as JClass

	fun scrollToDebugPos(pos: Int) {
		model.togglePosHighlight(pos)
	}

	override fun getFont(): Font {
		if (!::model.isInitialized || isDisposed) {
			// Swing installUI 阶段组件字体尚未设置，super.getFont() 会返回 null：
			// 上游 Java 直接返回 null 由 BasicTextUI 兜底（null 时应用 UI 默认字体），
			// 本覆写声明非空返回，必须自行回退，否则触发内在空检查 NPE 崩溃
			return super.getFont()
				?: (UIManager.getFont("TextArea.font") as? Font)
				?: Font(Font.MONOSPACED, Font.PLAIN, 12)
		}
		return model.getFont()
	}

	override fun getFontForTokenType(type: Int): Font = getFont()

	override fun createCodeAreaSyncer(): CodeAreaSyncer = SmaliSyncer(this)

	override fun sync(codeAreaSyncer: CodeAreaSyncer): Boolean = codeAreaSyncer.syncTo(this)

	override fun createRTextAreaUI(): RTextAreaUI {
		// IconRowHeader 在点击添加/删除图标时不会触发事件，
		// 因此不劫持它就无法设置断点
		return object : RSyntaxTextAreaUI(this) {
			override fun getEditorKit(tc: JTextComponent): EditorKit = object : RSyntaxTextAreaEditorKit() {
				override fun createIconRowHeader(textArea: RTextArea): IconRowHeader = object : FoldingAwareIconRowHeader(textArea as RSyntaxTextArea) {
					override fun mousePressed(e: MouseEvent) {
						val offs = textArea.viewToModel2D(e.getPoint())
						if (offs > -1) {
							model.setBreakpoint(offs)
						}
					}
				}
			}
		}
	}

	private abstract inner class SmaliModel {
		abstract fun loadCode(): String

		abstract fun loadUI(code: String)

		abstract fun unload()

		open fun getFont(): Font = super@SmaliArea.getFont()

		open fun getFontForTokenType(type: Int): Font = super@SmaliArea.getFontForTokenType(type)

		open fun setBreakpoint(off: Int) {
		}

		open fun togglePosHighlight(pos: Int) {
		}
	}

	private inner class NormalModel(smaliArea: SmaliArea) : SmaliModel() {
		init {
			smaliArea.getContentPanel().mainWindow.getEditorThemeManager().apply(smaliArea)
			setSyntaxEditingStyle(AbstractCodeArea.SYNTAX_STYLE_SMALI)
		}

		override fun loadCode(): String = getJClass().smali

		override fun loadUI(code: String) {
			setText(code)
		}

		override fun unload() {
		}
	}

	private inner class DebugModel : SmaliModel() {
		private var bpShortcut: KeyStroke? = null
		private var gutter: Gutter? = null
		private var runningHighlightTag: Any? = null // 运行行
		private val smaliV2Style = SmaliV2Style(this@SmaliArea)
		private val bpMap = HashMap<Int, BreakpointLine>()
		private val schemeListener = PropertyChangeListener {
			if (smaliV2Style.refreshTheme()) {
				setSyntaxScheme(smaliV2Style)
			}
		}

		init {
			loadV2Style()
			setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_ASSEMBLER_6502)
			addPropertyChangeListener(RSyntaxTextArea.SYNTAX_SCHEME_PROPERTY, schemeListener)
			regBreakpointEvents()
		}

		override fun loadCode(): String = DbgUtils.getSmaliCode(getJClass().getCls().getClassNode())

		override fun loadUI(code: String) {
			if (gutter == null) {
				val g = RSyntaxUtilities.getGutter(this@SmaliArea)
				gutter = g
				g.setBookmarkingEnabled(true)
				g.setIconRowHeaderInheritsGutterBackground(true)
				val baseFont = super@SmaliArea.getFont()
				g.setLineNumberFont(baseFont.deriveFont(baseFont.getSize2D() - 1.0f))
			}
			setText(code)
			loadV2Style()
			loadBreakpoints()
		}

		override fun unload() {
			removePropertyChangeListener(schemeListener)
			removeLineHighlight(runningHighlightTag)
			bpShortcut?.let { UiUtils.removeKeyBinding(this@SmaliArea, it, "set a break point") }
			BreakpointManager.removeListener(getJClass())
			bpMap.forEach { (_, v) -> v.remove() }
		}

		override fun getFont(): Font = smaliV2Style.font

		override fun getFontForTokenType(type: Int): Font = smaliV2Style.font

		private fun loadV2Style() {
			setSyntaxScheme(smaliV2Style)
		}

		private fun regBreakpointEvents() {
			val shortcut = KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0)
			bpShortcut = shortcut
			UiUtils.addKeyBinding(
				this@SmaliArea,
				shortcut,
				"set break point",
				object : AbstractAction() {
					override fun actionPerformed(e: ActionEvent) {
						setBreakpoint(getCaretPosition())
					}
				},
			)
			BreakpointManager.addListener(getJClass()) { pos -> setBreakpointDisabled(pos) }
		}

		private fun loadBreakpoints() {
			val posList = BreakpointManager.getPositions(getJClass())
			for (integer in posList) {
				setBreakpoint(integer)
			}
		}

		override fun setBreakpoint(pos: Int) {
			val line: Int
			try {
				line = getLineOfOffset(pos)
			} catch (e: BadLocationException) {
				LOG.error("Failed to get line by offset: {}", pos, e)
				return
			}
			var bpLine = bpMap.remove(line)
			if (bpLine == null) {
				bpLine = BreakpointLine(line)
				bpLine.setDisabled(false)
				bpMap[line] = bpLine
				if (!BreakpointManager.set(getJClass(), line)) {
					bpLine.setDisabled(true)
				}
			} else {
				BreakpointManager.remove(getJClass(), line)
				bpLine.remove()
			}
		}

		override fun togglePosHighlight(pos: Int) {
			val currentTag = runningHighlightTag
			if (currentTag != null) {
				removeLineHighlight(currentTag)
			}
			try {
				val line = getLineOfOffset(pos)
				runningHighlightTag = addLineHighlight(line, DEBUG_LINE_COLOR)
			} catch (e: BadLocationException) {
				LOG.error("Failed to get line by offset: {}", pos, e)
			}
		}

		private fun setBreakpointDisabled(pos: Int) {
			try {
				val line = getLineOfOffset(pos)
				bpMap.getOrPut(line) { BreakpointLine(line) }.setDisabled(true)
			} catch (e: BadLocationException) {
				LOG.error("Failed to get line by offset: {}", pos, e)
			}
		}

		private fun safeRemoveTrackingIcon(iconInfo: GutterIconInfo?) {
			val g = gutter
			if (g != null && iconInfo != null) {
				g.removeTrackingIcon(iconInfo)
			}
		}

		private inner class BreakpointLine(line: Int) {
			var highlightTag: Any? = null
			var iconInfo: GutterIconInfo? = null

			@JvmField
			var disabled = true
			val line: Int = line

			fun remove() {
				safeRemoveTrackingIcon(iconInfo)
				if (!this.disabled) {
					removeLineHighlight(highlightTag)
				}
			}

			fun setDisabled(disabled: Boolean) {
				if (disabled) {
					if (!this.disabled) {
						safeRemoveTrackingIcon(iconInfo)
						removeLineHighlight(highlightTag)
						try {
							iconInfo = gutter?.addLineTrackingIcon(line, ICON_BREAKPOINT_DISABLED)
						} catch (e: BadLocationException) {
							LOG.error("Failed to add line tracking icon", e)
						}
					}
				} else {
					if (this.disabled) {
						safeRemoveTrackingIcon(this.iconInfo)
						try {
							iconInfo = gutter?.addLineTrackingIcon(line, ICON_BREAKPOINT)
							highlightTag = addLineHighlight(line, BREAKPOINT_LINE_COLOR)
						} catch (e: BadLocationException) {
							LOG.error("Failed to remove line tracking icon", e)
						}
					}
				}
				this.disabled = disabled
			}
		}
	}

	private inner class SmaliV2Style(smaliArea: SmaliArea) : SyntaxScheme(true) {
		init {
			smaliArea.getContentPanel().mainWindow.getEditorThemeManager().apply(smaliArea)
			updateTheme()
		}

		val font: Font get() = getContentPanel().mainWindow.getSettings().smaliFont

		fun refreshTheme(): Boolean {
			val refresh = getSyntaxScheme() !== this
			if (refresh) {
				updateTheme()
			}
			return refresh
		}

		private fun updateTheme() {
			val mainStyles = getSyntaxScheme().getStyles()
			val styles = arrayOfNulls<Style>(mainStyles.size)
			for (i in mainStyles.indices) {
				val mainStyle = mainStyles[i]
				styles[i] = if (mainStyle == null) {
					Style()
				} else {
					// 字体由 getFont / getFontForTokenType 接管，这里无需设置
					Style(mainStyle.foreground, mainStyle.background, null)
				}
			}
			@Suppress("UNCHECKED_CAST")
			setStyles(styles as Array<Style>)
		}

		// RSyntaxTextArea 契约：SyntaxScheme(true) 构造时会以 null baseFont 调用
		// restoreDefaults（上游 Java 参数即 @Nullable）——参数必须可空，否则内在
		// 空检查在构造期直接 NPE
		override fun restoreDefaults(baseFont: Font?) {
			restoreDefaults(baseFont, true)
		}

		override fun restoreDefaults(baseFont: Font?, fontStyles: Boolean) {
			// 注意：这是继续使用编辑器主题的钩子，最好不要删除
		}
	}

	companion object {
		private const val serialVersionUID = 1334485631870306494L

		private val LOG = LoggerFactory.getLogger(SmaliArea::class.java)

		private val ICON_BREAKPOINT = UiUtils.openSvgIcon("debugger/db_set_breakpoint")
		private val ICON_BREAKPOINT_DISABLED = UiUtils.openSvgIcon("debugger/db_disabled_breakpoint")
		private val BREAKPOINT_LINE_COLOR = Color.decode("#ad103c")
		private val DEBUG_LINE_COLOR = Color.decode("#9c1138")
	}
}

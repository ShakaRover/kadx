package kadx.gui.ui.codearea

import kadx.api.ICodeInfo
import kadx.core.utils.StringUtils
import kadx.core.utils.exceptions.KadxRuntimeException
import kadx.gui.jobs.IBackgroundTask
import kadx.gui.treemodel.JClass
import kadx.gui.treemodel.JEditableNode
import kadx.gui.treemodel.JNode
import kadx.gui.ui.MainWindow
import kadx.gui.ui.action.JNodeAction
import kadx.gui.ui.panel.ContentPanel
import kadx.gui.utils.DefaultPopupMenuListener
import kadx.gui.utils.JumpPosition
import kadx.gui.utils.NLS
import kadx.gui.utils.UiUtils
import kadx.gui.utils.ui.DocumentUpdateListener
import kadx.gui.utils.ui.ZoomActions
import org.fife.ui.rsyntaxtextarea.AbstractTokenMakerFactory
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea
import org.fife.ui.rsyntaxtextarea.RSyntaxUtilities
import org.fife.ui.rsyntaxtextarea.SyntaxConstants
import org.fife.ui.rsyntaxtextarea.Token
import org.fife.ui.rsyntaxtextarea.TokenMakerFactory
import org.fife.ui.rsyntaxtextarea.TokenTypes
import org.fife.ui.rtextarea.Gutter
import org.fife.ui.rtextarea.RTextArea
import org.fife.ui.rtextarea.SearchContext
import org.fife.ui.rtextarea.SearchEngine
import org.slf4j.LoggerFactory
import java.awt.Dimension
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.ActionEvent
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.JCheckBoxMenuItem
import javax.swing.JMenuItem
import javax.swing.JPopupMenu
import javax.swing.JViewport
import javax.swing.SwingUtilities
import javax.swing.event.CaretEvent
import javax.swing.event.CaretListener
import javax.swing.event.PopupMenuEvent
import javax.swing.text.BadLocationException
import javax.swing.text.Caret
import javax.swing.text.DefaultCaret

/**
 * kadx 代码区的抽象基类。
 *
 * **做什么**：在 RSyntaxTextArea 之上统一处理：
 * - 加载 / 刷新内容与“已加载”状态；
 * - 代码折叠、行环绕、字号缩放；
 * - 光标词高亮、Ctrl+C 快速复制、可编辑节点的保存；
 * - 右键菜单与行号相关设置。
 *
 * **为什么用抽象类**：Java 代码区 [CodeArea] 与 Smali 代码区 [SmaliArea]
 * 共享这些行为，但加载方式与语法不同。
 */
abstract class AbstractCodeArea(panel: ContentPanel, jnode: JNode) : RSyntaxTextArea() {

	@JvmField
	protected var contentPanel: ContentPanel? = null

	@JvmField
	protected var node: JNode? = null

	private val loaded = AtomicBoolean(false)

	init {
		this.contentPanel = panel
		this.node = requireNotNull(jnode)

		setMarkOccurrences(false)
		setFadeCurrentLineHighlight(true)
		setAntiAliasingEnabled(true)
		applyEditableProperties(jnode)
		loadSettings()

		val settings = panel.mainWindow.getSettings()
		setLineWrap(settings.isCodeAreaLineWrap)

		ZoomActions.register(this, settings) { loadSettings() }

		if (jnode is JEditableNode) {
			addSaveActions(jnode)
			addChangeUpdates(jnode)
		} else {
			addCaretActions()
			addFastCopyAction()
		}
	}

	private fun applyEditableProperties(node: JNode) {
		val editable = node.isEditable()
		setEditable(editable)
		if (editable) {
			setCloseCurlyBraces(true)
			setCloseMarkupTags(true)
			setAutoIndentEnabled(true)
			setClearWhitespaceLinesEnabled(true)
		}
	}

	override fun createPopupMenu(): JPopupMenu {
		val menu = JPopupMenu()
		if (getNode().isEditable()) {
			menu.add(createPopupMenuItem(RTextArea.getAction(RTextArea.UNDO_ACTION)))
			menu.add(createPopupMenuItem(RTextArea.getAction(RTextArea.REDO_ACTION)))
			menu.addSeparator()
			menu.add(createPopupMenuItem(RTextArea.getAction(RTextArea.CUT_ACTION)))
			menu.add(createPopupMenuItem(RTextArea.getAction(RTextArea.COPY_ACTION)))
			menu.add(createPopupMenuItem(RTextArea.getAction(RTextArea.PASTE_ACTION)))
			menu.add(createPopupMenuItem(RTextArea.getAction(RTextArea.DELETE_ACTION)))
			menu.addSeparator()
			menu.add(createPopupMenuItem(RTextArea.getAction(RTextArea.SELECT_ALL_ACTION)))
		} else {
			menu.add(createPopupMenuItem(RTextArea.getAction(RTextArea.COPY_ACTION)))
			menu.add(createPopupMenuItem(RTextArea.getAction(RTextArea.SELECT_ALL_ACTION)))
		}
		appendFoldingMenu(menu)
		appendWrapLineMenu(menu)
		return menu
	}

	override fun appendFoldingMenu(popup: JPopupMenu) {
		// 仅在启用折叠时添加折叠菜单项
		if (isCodeFoldingEnabled()) {
			super.appendFoldingMenu(popup)
		}
	}

	private fun appendWrapLineMenu(popupMenu: JPopupMenu) {
		val settings = getContentPanel().mainWindow.getSettings()
		popupMenu.addSeparator()
		val wrapItem = JCheckBoxMenuItem(NLS.str("popup.line_wrap"), getLineWrap())
		wrapItem.setAction(object : AbstractAction(NLS.str("popup.line_wrap")) {
			override fun actionPerformed(e: ActionEvent) {
				val wrap = !getLineWrap()
				settings.setCodeAreaLineWrap(wrap)
				settings.sync()
				getContentPanel().getTabbedPane().tabs.forEach { v ->
					if (v is AbstractCodeContentPanel) {
						val codeArea = v.getCodeArea()
						if (codeArea != null) {
							setCodeAreaLineWrap(codeArea, wrap)
							if (v is ClassCodeContentPanel) {
								setCodeAreaLineWrap(v.smaliCodeArea, wrap)
							}
						}
					}
				}
			}
		})
		popupMenu.add(wrapItem)
		popupMenu.addPopupMenuListener(object : DefaultPopupMenuListener {
			override fun popupMenuWillBecomeVisible(e: PopupMenuEvent) {
				wrapItem.setState(getLineWrap())
			}
		})
	}

	private fun setCodeAreaLineWrap(codeArea: AbstractCodeArea, wrap: Boolean) {
		codeArea.setLineWrap(wrap)
		if (codeArea.isVisible()) {
			codeArea.repaint()
		}
	}

	private fun addCaretActions() {
		val caret: Caret = getCaret()
		if (caret is DefaultCaret) {
			caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE)
		}
		this.addFocusListener(object : FocusListener {
			// 修复光标丢失的 bug：失焦时隐藏，重新获得焦点时显示以强制重绘
			override fun focusGained(e: FocusEvent) {
				caret.setVisible(true)
			}

			override fun focusLost(e: FocusEvent) {
				caret.setVisible(false)
			}
		})
		addCaretListener(object : CaretListener {
			var lastPos = -1
			var lastText = ""

			override fun caretUpdate(e: CaretEvent) {
				val pos = getCaretPosition()
				if (lastPos != pos) {
					lastPos = pos
					lastText = highlightCaretWord(lastText, pos)
				}
			}
		})
	}

	/** Ctrl+C 会复制高亮的单词。 */
	private fun addFastCopyAction() {
		addKeyListener(object : KeyAdapter() {
			override fun keyPressed(e: KeyEvent) {
				if (e.getKeyCode() == KeyEvent.VK_C && UiUtils.isCtrlDown(e)) {
					UiUtils.copyToClipboard(selectedTokenOrWord)
				}
			}
		})
	}

	/**
	 * 若用户选中了某个单词（例如鼠标拖选）则返回它，否则返回光标下的 token。
	 * 当 token 是字符串或注释时，这样可以只控制/复制其中单词而不是整段内容。
	 */
	val selectedTokenOrWord: String? get() {
		val rc = getSelectedText()
		if (rc == null) {
			return wordUnderCaret
		}
		if (StringUtils.isEmpty(rc)) {
			return wordUnderCaret
		}
		return rc
	}

	private fun addSaveActions(node: JEditableNode) {
		addKeyListener(object : KeyAdapter() {
			override fun keyPressed(e: KeyEvent) {
				if (e.getKeyCode() == KeyEvent.VK_S && UiUtils.isCtrlDown(e)) {
					node.save(this@AbstractCodeArea.getText())
					node.setChanged(false)
				}
			}
		})
	}

	private fun addChangeUpdates(editableNode: JEditableNode) {
		getDocument().addDocumentListener(
			DocumentUpdateListener {
				if (loaded.get()) {
					editableNode.setChanged(true)
				}
			},
		)
	}

	private fun highlightCaretWord(lastText: String, pos: Int): String {
		val text = getWordByPosition(pos)
		if (StringUtils.isEmpty(text)) {
			highlightAllMatches(null)
			return ""
		}
		if (lastText != text) {
			highlightAllMatches(text)
			return text ?: lastText
		}
		return lastText
	}

	val wordUnderCaret: String? get() = getWordByPosition(getCaretPosition())

	fun getWordByPosition(offset: Int): String? {
		val token = getWordTokenAtOffset(offset) ?: return null
		val str = token.getLexeme()
		val len = str.length
		if (len > 2 && str.startsWith("\"") && str.endsWith("\"")) {
			return str.substring(1, len - 1)
		}
		return str
	}

	/**
	 * 返回偏移处的任意单词 token（非空白或特殊符号）。
	 * 若光标停在单词末尾（当前 token 已是空白），则选前一个 token。
	 */
	fun getWordTokenAtOffset(offset: Int): Token? {
		try {
			val line = this.getLineOfOffset(offset)
			val lineTokens = this.getTokenListForLine(line)
			var token: Token? = null
			var prevToken: Token? = null
			var t: Token? = lineTokens
			while (t != null && t.isPaintable()) {
				if (t.containsPosition(offset)) {
					token = t
					break
				}
				prevToken = t
				t = t.getNextToken()
			}
			if (token == null) {
				return null
			}
			if (isWordToken(token)) {
				return token
			}
			if (isWordToken(prevToken)) {
				return prevToken
			}
			return null
		} catch (e: Exception) {
			LOG.error("Failed to get token at pos: {}", offset, e)
			return null
		}
	}

	abstract fun getCodeInfo(): ICodeInfo

	fun load() {
		if (isLoaded) {
			return
		}
		val loadTask = getLoadTask()
		getContentPanel().mainWindow.getBackgroundExecutor().execute(loadTask)
	}

	/**
	 * 在此方法中实现加载并设置显示内容的逻辑，加载完成时调用 `setLoaded()`。
	 */
	abstract fun getLoadTask(): IBackgroundTask

	fun setLoaded() {
		loaded.set(true)
		discardAllEdits() // 禁用“撤销”到空状态（加载之前）
	}

	fun setUnLoaded() {
		loaded.set(false)
	}

	val isLoaded: Boolean get() = loaded.get()

	/**
	 * 在此方法中实现从缓存重新加载节点并设置新显示内容的逻辑。
	 */
	abstract fun refresh()

	open fun loadSettings() {
		loadCommonSettings(getContentPanel().mainWindow, this)
	}

	fun scrollToPos(pos: Int) {
		try {
			setCaretPosition(pos)
			centerCurrentLine()
			forceCurrentLineHighlightRepaint()
		} catch (e: Exception) {
			LOG.warn("Can't scroll to position {}", pos, e)
		}
	}

	@Suppress("deprecation")
	fun centerCurrentLine() {
		val viewport = SwingUtilities.getAncestorOfClass(JViewport::class.java, this) as? JViewport ?: return
		try {
			val r: Rectangle = modelToView(getCaretPosition()) ?: return
			val extentHeight = viewport.getExtentSize().height
			val viewSize = viewport.getViewSize() ?: return
			val viewHeight = viewSize.height

			var y = Math.max(0, r.y - extentHeight / 2)
			y = Math.min(y, viewHeight - extentHeight)

			viewport.setViewPosition(Point(0, y))
		} catch (e: BadLocationException) {
			LOG.debug("Can't center current line", e)
		}
	}

	/** @param str 为 null 时重置当前高亮 */
	private fun highlightAllMatches(str: String?) {
		try {
			val context = SearchContext(str)
			context.setMarkAll(true)
			context.setMatchCase(true)
			context.setWholeWord(true)
			SearchEngine.markAll(this, context)
		} catch (e: Throwable) {
			// 对于不正确的代码，语法解析可能失败
			LOG.debug("Search highlight failed", e)
		}
	}

	val currentPosition: JumpPosition? get() {
		val pos = getCaretPosition()
		if (pos == 0) {
			return null
		}
		return JumpPosition(getNode(), pos)
	}

	@Throws(BadLocationException::class)
	fun getLineStartFor(pos: Int): Int = getLineStartOffset(getLineOfOffset(pos))

	@Throws(BadLocationException::class)
	fun getLineAt(pos: Int): String = getLineText(getLineOfOffset(pos) + 1)

	@Throws(BadLocationException::class)
	fun getLineText(line: Int): String {
		val lineNum = line - 1
		val startOffset = getLineStartOffset(lineNum)
		val endOffset = getLineEndOffset(lineNum)
		return getText(startOffset, endOffset - startOffset)
	}

	fun getContentPanel(): ContentPanel = checkNotNull(contentPanel) { "Code area is disposed" }

	open fun getNode(): JNode = checkNotNull(node) { "Code area is disposed" }

	open fun getJClass(): JClass? {
		val n = node
		return if (n is JClass) n else null
	}

	val isDisposed: Boolean get() = node == null

	open fun dispose() {
		// 清理内部引用
		try {
			setIgnoreRepaint(true)
			setText("")
			setEnabled(false)
			setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_NONE)
			setLinkGenerator(null)
			for (mouseListener in getMouseListeners()) {
				removeMouseListener(mouseListener)
			}
			for (mouseMotionListener in getMouseMotionListeners()) {
				removeMouseMotionListener(mouseMotionListener)
			}
			val popupMenu = getPopupMenu()
			for (popupMenuListener in popupMenu.getPopupMenuListeners()) {
				popupMenu.removePopupMenuListener(popupMenuListener)
			}
			for (component in popupMenu.getComponents()) {
				if (component is JMenuItem) {
					val action: Action? = component.getAction()
					if (action is JNodeAction) {
						action.dispose()
					}
				}
			}
			popupMenu.removeAll()
		} catch (e: Throwable) {
			LOG.debug("Error on code area dispose", e)
		}
		// 代码区引用可能仍被 UI 对象持有，重置节点引用以允许 GC 整棵 kadx 对象树
		node = null
		contentPanel = null
	}

	override fun getPreferredSize(): Dimension {
		try {
			return super.getPreferredSize()
		} catch (e: Exception) {
			LOG.warn("Failed to calculate preferred size for code area", e)
			// 参考 javax.swing.JTextArea.getPreferredSize 的兼容实现
			// 作为返回 null 尺寸时的降级方案
			val d = Dimension(400, 400)
			val insets = getInsets()
			if (getColumns() != 0) {
				d.width = Math.max(d.width, getColumns() * getColumnWidth() + insets.left + insets.right)
			}
			if (getRows() != 0) {
				d.height = Math.max(d.height, getRows() * getRowHeight() + insets.top + insets.bottom)
			}
			return d
		}
	}

	companion object {
		private const val serialVersionUID = -3980354865216031972L

		private val LOG = LoggerFactory.getLogger(AbstractCodeArea::class.java)

		const val SYNTAX_STYLE_SMALI = "text/smali"

		init {
			val tokenMakerFactory = TokenMakerFactory.getDefaultInstance()
			if (tokenMakerFactory is AbstractTokenMakerFactory) {
				tokenMakerFactory.putMapping(SYNTAX_STYLE_SMALI, "kadx.gui.ui.codearea.SmaliTokenMaker")
				// 用简单 token maker 替代默认 PlainTextTokenMaker，避免解析错误
				tokenMakerFactory.putMapping(SyntaxConstants.SYNTAX_STYLE_NONE, "kadx.gui.ui.codearea.SimpleTokenMaker")
			} else {
				throw KadxRuntimeException("Unexpected TokenMakerFactory instance: " + tokenMakerFactory.javaClass)
			}
			SmaliFoldParser.register()
		}

		/** 判断 token 是否是“单词”（非空白、非分隔符/运算符/函数）。 */
		fun isWordToken(token: Token?): Boolean {
			if (token == null) {
				return false
			}
			return when (token.getType()) {
				TokenTypes.NULL, TokenTypes.WHITESPACE, TokenTypes.SEPARATOR,
				TokenTypes.OPERATOR, TokenTypes.FUNCTION,
				-> false

				TokenTypes.IDENTIFIER -> {
					if (token.length() == 1) {
						val ch = token.charAt(0)
						ch != ';' && ch != '.' && ch != ','
					} else {
						true
					}
				}

				else -> true
			}
		}

		/** 创建一个使用 kadx 通用设置的只读代码区（用于日志、搜索结果等）。 */
		fun getDefaultArea(mainWindow: MainWindow): RSyntaxTextArea {
			val area = RSyntaxTextArea()
			area.setEditable(false)
			area.setCodeFoldingEnabled(false)
			area.setAntiAliasingEnabled(true)
			loadCommonSettings(mainWindow, area)
			return area
		}

		/** 把 kadx 的编辑器主题、字体、行号字体应用到指定代码区。 */
		fun loadCommonSettings(mainWindow: MainWindow, area: RSyntaxTextArea) {
			val settings = mainWindow.getSettings()
			mainWindow.getEditorThemeManager().apply(area)
			area.setFont(settings.codeFont)
			val gutter: Gutter? = RSyntaxUtilities.getGutter(area)
			if (gutter != null) {
				gutter.setLineNumberFont(settings.codeFont)
			}
		}
	}
}

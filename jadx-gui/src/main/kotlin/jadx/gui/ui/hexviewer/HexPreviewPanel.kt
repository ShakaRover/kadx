package jadx.gui.ui.hexviewer

import jadx.gui.settings.JadxSettings
import jadx.gui.utils.NLS
import jadx.gui.utils.UiUtils
import org.exbin.auxiliary.binary_data.BinaryData
import org.exbin.auxiliary.binary_data.array.ByteArrayEditableData
import org.exbin.bined.CodeAreaCaretListener
import org.exbin.bined.CodeAreaCaretPosition
import org.exbin.bined.CodeAreaUtils
import org.exbin.bined.CodeCharactersCase
import org.exbin.bined.CodeType
import org.exbin.bined.EditMode
import org.exbin.bined.SelectionRange
import org.exbin.bined.basic.BasicCodeAreaZone
import org.exbin.bined.color.CodeAreaBasicColors
import org.exbin.bined.highlight.swing.color.CodeAreaMatchColorType
import org.exbin.bined.swing.CodeAreaPainter
import org.exbin.bined.swing.basic.DefaultCodeAreaCommandHandler
import org.exbin.bined.swing.capability.CharAssessorPainterCapable
import org.exbin.bined.swing.capability.ColorAssessorPainterCapable
import org.exbin.bined.swing.section.SectCodeArea
import org.exbin.bined.swing.section.color.SectionCodeAreaColorProfile
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import java.nio.charset.StandardCharsets
import java.util.Arrays
import javax.swing.JMenu
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu

/**
 * 十六进制预览面板：把二进制数据以十六进制 / 文本两种视角展示。
 *
 * **做什么**：内部组合了 [SectCodeArea]（ExBin 十六进制编辑器）、
 * [HexSearchBar]（搜索条）、[HexEditorHeader]（底部状态栏）、[HexInspectorPanel]（右侧值检查器），
 * 并提供数据加载、滚动定位、右键菜单（复制 / 十六进制复制 / 偏移复制）等能力。
 *
 * **线程模型**：保持原 Swing 模型，所有方法都在 EDT 上调用；数据加载由上层负责。
 */
class HexPreviewPanel(settings: JadxSettings) : JPanel() {

	private val valuesCache = ByteArray(CACHE_SIZE)
	private val hexCodeArea = SectCodeArea()
	private val defaultColors: SectionCodeAreaColorProfile
	private val header: HexEditorHeader
	private val searchBar: HexSearchBar
	private val inspector: HexInspectorPanel

	private var popupMenu: JPopupMenu? = null
	private val cutAction = JMenuItem(NLS.str("popup.cut"))
	private val copyAction = JMenuItem(NLS.str("popup.copy"))
	private val copyHexAction = JMenuItem(NLS.str("popup.copy_as_hex"))
	private val copyStringAction = JMenuItem(NLS.str("popup.copy_as_string"))
	private val pasteAction = JMenuItem(NLS.str("popup.paste"))
	private val deleteAction = JMenuItem(NLS.str("popup.delete"))
	private val selectAllAction = JMenuItem(NLS.str("popup.select_all"))
	private val copyOffsetItem = JMenuItem(NLS.str("popup.copy_offset"))
	private var popupMenuPositionZone: BasicCodeAreaZone = BasicCodeAreaZone.UNKNOWN

	init {
		hexCodeArea.setCodeFont(settings.getSmaliFont())
		hexCodeArea.setEditMode(EditMode.READ_ONLY)
		hexCodeArea.setCharset(StandardCharsets.UTF_8)
		hexCodeArea.setComponentPopupMenu(object : JPopupMenu() {
			override fun show(invoker: Component?, x: Int, y: Int) {
				popupMenuPositionZone = hexCodeArea.getPainter().getPositionZone(x, y)
				createPopupMenu()
				val menu = popupMenu
				if (menu != null &&
					popupMenuPositionZone != BasicCodeAreaZone.HEADER &&
					popupMenuPositionZone != BasicCodeAreaZone.ROW_POSITIONS
				) {
					updatePopupActionStates()
					menu.show(invoker, x, y)
				}
			}
		})

		inspector = HexInspectorPanel()
		searchBar = HexSearchBar(hexCodeArea)
		header = HexEditorHeader(hexCodeArea)
		header.setFont(settings.getUiFont())

		val painter: CodeAreaPainter = hexCodeArea.getPainter()
		defaultColors = hexCodeArea.getColorsProfile() as SectionCodeAreaColorProfile

		hexCodeArea.setColorsProfile(getColorsProfile())

		val codeAreaAssessor = BinEdCodeAreaAssessor(
			(painter as ColorAssessorPainterCapable).getColorAssessor(),
			(painter as CharAssessorPainterCapable).getCharAssessor(),
		)
		(painter as ColorAssessorPainterCapable).setColorAssessor(codeAreaAssessor)
		(painter as CharAssessorPainterCapable).setCharAssessor(codeAreaAssessor)

		layout = BorderLayout()
		add(searchBar, BorderLayout.PAGE_START)
		add(hexCodeArea, BorderLayout.CENTER)
		add(header, BorderLayout.PAGE_END)
		add(inspector, BorderLayout.EAST)

		setFocusable(true)
		addFocusListener(object : FocusListener {
			override fun focusGained(e: FocusEvent) {
				hexCodeArea.requestFocusInWindow()
			}

			override fun focusLost(e: FocusEvent) {
			}
		})
		createActions()
		enableUpdate()
	}

	fun getColorsProfile(): SectionCodeAreaColorProfile {
		val isDarkTheme = UiUtils.isDarkTheme(
			checkNotNull(defaultColors.getColor(CodeAreaBasicColors.TEXT_BACKGROUND)),
		)
		val markAllHighlightColor = if (isDarkTheme) Color.decode("#32593D") else Color.decode("#ffc800")
		val editorSelectionBackground =
			checkNotNull(defaultColors.getColor(CodeAreaBasicColors.SELECTION_BACKGROUND))
		val currentMatchColor = UiUtils.adjustBrightness(editorSelectionBackground, if (isDarkTheme) 0.6f else 1.4f)
		defaultColors.setColor(CodeAreaMatchColorType.MATCH_BACKGROUND, markAllHighlightColor)
		defaultColors.setColor(CodeAreaMatchColorType.CURRENT_MATCH_BACKGROUND, currentMatchColor)
		return defaultColors
	}

	fun isDataLoaded(): Boolean = !hexCodeArea.getContentData().isEmpty()

	fun setData(data: ByteArray?) {
		if (data != null) {
			hexCodeArea.setContentData(ByteArrayEditableData(data))
		}
	}

	fun setData(data: BinaryData?) {
		if (data != null) {
			hexCodeArea.setContentData(data)
			inspector.setContentData(data)
		}
	}

	fun scrollToOffset(pos: Int) {
		hexCodeArea.setSelection(pos.toLong(), (pos + 1).toLong())
		hexCodeArea.setActiveCaretPosition(pos.toLong())
		hexCodeArea.centerOnPosition(hexCodeArea.getActiveCaretPosition())
	}

	fun enableUpdate() {
		val caretMovedListener = CodeAreaCaretListener { updateValues() }
		hexCodeArea.addCaretMovedListener(caretMovedListener)
	}

	private fun updateValues() {
		val caretPosition = hexCodeArea.getActiveCaretPosition()
		val dataPosition = caretPosition.getDataPosition()
		val dataSize = hexCodeArea.getDataSize()

		if (dataPosition < dataSize) {
			val availableData = if (dataSize - dataPosition >= CACHE_SIZE) CACHE_SIZE else (dataSize - dataPosition).toInt()
			val contentData = hexCodeArea.getContentData()
			contentData.copyToArray(dataPosition, valuesCache, 0, availableData)
			if (availableData < CACHE_SIZE) {
				Arrays.fill(valuesCache, availableData, CACHE_SIZE, 0.toByte())
			}
		}

		inspector.setOffset(dataPosition.toInt())
	}

	private fun createActions() {
		cutAction.addActionListener { performCut() }

		copyAction.addActionListener { performCopy() }

		copyHexAction.addActionListener { performCopyAsCode() }

		copyStringAction.addActionListener { performCopy() }

		pasteAction.addActionListener { performPaste() }

		deleteAction.addActionListener {
			if (!isEditable()) {
				performDelete()
			}
		}

		selectAllAction.addActionListener { performSelectAll() }

		copyOffsetItem.addActionListener { copyOffset() }
	}

	private fun createPopupMenu() {
		val isEditable = isEditable()
		val menu = JPopupMenu()
		menu.add(copyAction)

		if (isEditable) {
			menu.add(cutAction)
			menu.add(pasteAction)
			menu.add(deleteAction)
			menu.addSeparator()
		}

		val copyMenu = JMenu(NLS.str("popup.copy_as"))
		copyMenu.add(copyHexAction)
		copyMenu.add(copyStringAction)
		menu.add(copyMenu)
		menu.add(copyOffsetItem)
		menu.add(selectAllAction)
		popupMenu = menu
	}

	private fun updatePopupActionStates() {
		val selectionExists = isSelection()
		val isEditable = !isEditable()

		cutAction.setEnabled(isEditable && selectionExists)
		copyAction.setEnabled(selectionExists)
		copyHexAction.setEnabled(selectionExists)
		copyStringAction.setEnabled(selectionExists)
		deleteAction.setEnabled(isEditable && selectionExists)

		selectAllAction.setEnabled(hexCodeArea.getDataSize() > 0)
	}

	fun getEditor(): SectCodeArea = hexCodeArea

	fun getHeader(): HexEditorHeader = header

	fun getInspector(): HexInspectorPanel = inspector

	fun getSearchBar(): HexSearchBar = searchBar

	fun showSearchBar() {
		searchBar.showAndFocus()
	}

	fun performCut() {
		hexCodeArea.cut()
	}

	fun performCopy() {
		hexCodeArea.copy()
	}

	fun performCopyAsCode() {
		(hexCodeArea.getCommandHandler() as DefaultCodeAreaCommandHandler).copyAsCode()
	}

	fun performPaste() {
		hexCodeArea.paste()
	}

	fun performDelete() {
		hexCodeArea.delete()
	}

	fun performSelectAll() {
		hexCodeArea.selectAll()
	}

	fun isSelection(): Boolean = hexCodeArea.hasSelection()

	fun isEditable(): Boolean = hexCodeArea.isEditable()

	fun canPaste(): Boolean = hexCodeArea.canPaste()

	fun copyOffset() {
		val str = header.addressString(hexCodeArea.getSelection().getStart())
		UiUtils.copyToClipboard(str)
	}

	fun dispose() {
		hexCodeArea.getContentData().dispose()
	}

	companion object {
		private const val serialVersionUID = 3261685857479120073L
		private const val CACHE_SIZE = 250

		@JvmStatic
		fun getSelectionData(core: SectCodeArea): String? {
			val selection: SelectionRange = core.getSelection()
			if (!selection.isEmpty()) {
				val first = selection.getFirst()
				val last = selection.getLast()

				val copy = core.getContentData().copy(first, last - first + 1)

				val codeType = core.getCodeType()
				val charactersCase = core.getCodeCharactersCase()

				val charsPerByte = codeType.getMaxDigitsForByte() + 1
				var textLength = (copy.getDataSize() * charsPerByte).toInt()
				if (textLength > 0) {
					textLength--
				}

				val targetData = CharArray(textLength)
				Arrays.fill(targetData, ' ')
				for (i in 0 until copy.getDataSize().toInt()) {
					CodeAreaUtils.byteToCharsCode(copy.getByte(i.toLong()), codeType, targetData, i * charsPerByte, charactersCase)
				}
				return String(targetData)
			}
			return null
		}
	}
}

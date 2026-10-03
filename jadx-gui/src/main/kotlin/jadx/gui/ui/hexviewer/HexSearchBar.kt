package jadx.gui.ui.hexviewer

import com.formdev.flatlaf.FlatClientProperties
import jadx.core.utils.StringUtils
import jadx.gui.ui.hexviewer.search.BinarySearch
import jadx.gui.ui.hexviewer.search.SearchCondition
import jadx.gui.ui.hexviewer.search.SearchParameters
import jadx.gui.ui.hexviewer.search.service.BinarySearchServiceImpl
import jadx.gui.utils.HexUtils
import jadx.gui.utils.Icons
import jadx.gui.utils.NLS
import jadx.gui.utils.TextStandardActions
import jadx.gui.utils.UiUtils
import org.exbin.auxiliary.binary_data.array.ByteArrayEditableData
import org.exbin.bined.swing.section.SectCodeArea
import java.awt.Color
import java.awt.event.ActionListener
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JTextField
import javax.swing.JToggleButton
import javax.swing.JToolBar
import javax.swing.border.EmptyBorder

/**
 * 十六进制视图顶部的搜索工具条。
 *
 * **做什么**：提供搜索输入框、区分大小写、文本/十六进制切换、上一个/下一个、
 * 全部标记、关闭等控件；把用户操作转成 [SearchParameters] 交给 [BinarySearch] 执行。
 *
 * **线程模型**：保持原 Swing 模型，所有监听器都在 EDT 上执行；真正的搜索由
 * [BinarySearch] 内部的后台线程完成。
 */
class HexSearchBar(textArea: SectCodeArea) : JToolBar() {

	private val hexCodeArea: SectCodeArea = textArea

	private val searchField = JTextField(30)
	private val resultCountLabel = JLabel()
	private val markAllCB = JToggleButton()
	private val findTypeCB = JToggleButton()
	private val matchCaseCB = JToggleButton()
	private val nextMatchButton = JButton()
	private val prevMatchButton = JButton()

	private var control: Control? = null

	init {
		val findLabel = JLabel(NLS.str("search.find") + ':')
		add(findLabel)

		searchField.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true)
		searchField.addKeyListener(object : KeyAdapter() {
			override fun keyReleased(e: KeyEvent) {
				when (e.keyCode) {
					KeyEvent.VK_ENTER -> {
						// 回车忽略，交给输入框的 ActionListener 处理
					}

					KeyEvent.VK_ESCAPE -> toggle()

					else -> control?.performFind()
				}
			}
		})
		searchField.addActionListener { control?.notifySearchChanging() }
		TextStandardActions.attach(searchField)
		add(searchField)

		val searchSettingListener = ActionListener { control?.notifySearchChanged() }

		resultCountLabel.setBorder(EmptyBorder(0, 10, 0, 10))
		resultCountLabel.setForeground(Color.GRAY)
		add(resultCountLabel)

		matchCaseCB.setIcon(Icons.ICON_MATCH)
		matchCaseCB.setSelectedIcon(Icons.ICON_MATCH_SELECTED)
		matchCaseCB.setToolTipText(NLS.str("search.match_case"))
		matchCaseCB.addActionListener(searchSettingListener)
		add(matchCaseCB)

		findTypeCB.setIcon(Icons.ICON_FIND_TYPE_TXT)
		findTypeCB.setSelectedIcon(Icons.ICON_FIND_TYPE_HEX)
		if (findTypeCB.isSelected()) {
			findTypeCB.setToolTipText(NLS.str("search.find_type_hex"))
		} else {
			findTypeCB.setToolTipText(NLS.str("search.find_type_text"))
		}
		findTypeCB.addActionListener {
			searchField.setText("")
			updateFindStatus()
			control?.notifySearchChanged()
		}
		add(findTypeCB)

		prevMatchButton.setIcon(Icons.ICON_UP)
		prevMatchButton.setToolTipText(NLS.str("search.previous"))
		prevMatchButton.addActionListener { control?.prevMatch() }
		prevMatchButton.setBorderPainted(false)
		add(prevMatchButton)

		nextMatchButton.setIcon(Icons.ICON_DOWN)
		nextMatchButton.setToolTipText(NLS.str("search.next"))
		nextMatchButton.addActionListener { control?.nextMatch() }
		nextMatchButton.setBorderPainted(false)
		add(nextMatchButton)

		markAllCB.setIcon(Icons.ICON_MARK)
		markAllCB.setSelectedIcon(Icons.ICON_MARK_SELECTED)
		markAllCB.setToolTipText(NLS.str("search.mark_all"))
		markAllCB.setSelected(true)
		markAllCB.addActionListener(searchSettingListener)
		add(markAllCB)

		val closeButton = JButton()
		closeButton.setIcon(Icons.ICON_CLOSE)
		closeButton.addActionListener { toggle() }
		closeButton.setBorderPainted(false)
		add(closeButton)

		val binarySearch = BinarySearch(this)
		binarySearch.setBinarySearchService(BinarySearchServiceImpl(hexCodeArea))
		setFloatable(false)
		setVisible(false)
	}

	/*
	 * 复刻 IntelliJ 的搜索条行为：
	 * 1.1 若用户已选中文本，则用选中内容作为搜索词；
	 * 1.2 否则沿用上一次的搜索词（没有则为空）；
	 * 2. 全选搜索框并聚焦。
	 */
	fun showAndFocus() {
		setVisible(true)

		if (hexCodeArea.hasSelection()) {
			searchField.setText(hexCodeArea.getActiveSection().toString())
		}
		val selectedText = HexPreviewPanel.getSelectionData(hexCodeArea)
		if (!StringUtils.isEmpty(selectedText)) {
			searchField.setText(selectedText)
			makeFindByHexButton()
		}

		searchField.selectAll()
		searchField.requestFocus()
	}

	fun toggle() {
		val visible = !isVisible()
		setVisible(visible)

		if (visible) {
			val preferText = HexPreviewPanel.getSelectionData(hexCodeArea)
			if (!StringUtils.isEmpty(preferText)) {
				searchField.setText(preferText)
				makeFindByHexButton()
			}
			searchField.selectAll()
			searchField.requestFocus()
		} else {
			control?.performEscape()
			hexCodeArea.requestFocus()
		}
	}

	fun setInfoLabel(text: String) {
		resultCountLabel.setText(text)
	}

	fun updateMatchCount(hasMatches: Boolean, prevMatchAvailable: Boolean, nextMatchAvailable: Boolean) {
		prevMatchButton.setEnabled(prevMatchAvailable)
		nextMatchButton.setEnabled(nextMatchAvailable)
	}

	fun setControl(control: Control?) {
		this.control = control
	}

	fun clearSearch() {
		setInfoLabel("")
		searchField.setText("")
	}

	val searchParameters: SearchParameters get() {
		val searchParameters = SearchParameters()
		searchParameters.setMatchCase(matchCaseCB.isSelected())
		searchParameters.setMatchMode(SearchParameters.MatchMode.fromBoolean(markAllCB.isSelected()))
		val searchDirection = checkNotNull(control).getSearchDirection()
		searchParameters.setSearchDirection(searchDirection)

		val startPosition: Long
		if (searchParameters.isSearchFromCursor) {
			startPosition = hexCodeArea.getActiveCaretPosition().getDataPosition()
		} else {
			startPosition = when (searchDirection) {
				SearchParameters.SearchDirection.FORWARD -> 0L
				SearchParameters.SearchDirection.BACKWARD -> hexCodeArea.getDataSize() - 1
			}
		}
		searchParameters.setStartPosition(startPosition)

		searchParameters.setCondition(SearchCondition(makeSearchCondition()))
		return searchParameters
	}

	private fun makeSearchCondition(): SearchCondition {
		val condition = SearchCondition()
		if (findTypeCB.isSelected()) {
			condition.setSearchMode(SearchCondition.SearchMode.BINARY)
		} else {
			condition.setSearchMode(SearchCondition.SearchMode.TEXT)
		}
		if (!StringUtils.isEmpty(searchField.getText())) {
			if (condition.getSearchMode() == SearchCondition.SearchMode.TEXT) {
				condition.setSearchText(searchField.getText())
			} else {
				val hexBytes = searchField.getText()
				val isValidHexInput = HexUtils.isValidHexString(hexBytes)
				UiUtils.highlightAsErrorField(searchField, !isValidHexInput)
				if (isValidHexInput) {
					condition.setBinaryData(ByteArrayEditableData(HexUtils.hexStringToByteArray(hexBytes)))
				}
			}
		}
		return condition
	}

	fun updateFindStatus() {
		UiUtils.highlightAsErrorField(searchField, false)
		val condition = makeSearchCondition()
		if (condition.getSearchMode() == SearchCondition.SearchMode.TEXT) {
			findTypeCB.setSelected(false)
			findTypeCB.setToolTipText(NLS.str("search.find_type_text"))
			matchCaseCB.setEnabled(true)
		} else {
			makeFindByHexButton()
			matchCaseCB.setEnabled(false)
		}
	}

	private fun makeFindByHexButton() {
		findTypeCB.setSelected(true)
		findTypeCB.setToolTipText(NLS.str("search.find_type_hex"))
	}

	/** 搜索框向协调器发出的操作集合。 */
	interface Control {

		fun prevMatch()

		fun nextMatch()

		fun performEscape()

		fun performFind()

		/** 搜索参数已变化。 */
		fun notifySearchChanged()

		/**
		 * 搜索参数正在变化，但未必需要立即搜索。
		 *
		 * 通常是用户正在输入文本。
		 */
		fun notifySearchChanging()

		fun getSearchDirection(): SearchParameters.SearchDirection

		fun close()
	}

	companion object {
		private const val serialVersionUID = 1836871286618633003L
	}
}

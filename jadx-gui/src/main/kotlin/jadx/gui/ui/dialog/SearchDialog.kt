package jadx.gui.ui.dialog

import com.formdev.flatlaf.FlatClientProperties
import com.formdev.flatlaf.icons.FlatSearchWithHistoryIcon
import jadx.api.JavaClass
import jadx.api.JavaPackage
import jadx.api.resources.ResourceContentType
import jadx.core.dex.nodes.PackageNode
import jadx.core.utils.ListUtils
import jadx.core.utils.StringUtils
import jadx.core.utils.Utils
import jadx.gui.jobs.ITaskInfo
import jadx.gui.jobs.ITaskProgress
import jadx.gui.search.SearchSettings
import jadx.gui.search.SearchTask
import jadx.gui.search.providers.ClassSearchProvider
import jadx.gui.search.providers.CodeSearchProvider
import jadx.gui.search.providers.CommentSearchProvider
import jadx.gui.search.providers.FieldSearchProvider
import jadx.gui.search.providers.MergedSearchProvider
import jadx.gui.search.providers.MethodSearchProvider
import jadx.gui.search.providers.ResourceFilter
import jadx.gui.search.providers.ResourceSearchProvider
import jadx.gui.treemodel.JClass
import jadx.gui.treemodel.JNode
import jadx.gui.treemodel.JResource
import jadx.gui.ui.MainWindow
import jadx.gui.utils.CacheObject
import jadx.gui.utils.ILoadListener
import jadx.gui.utils.Icons
import jadx.gui.utils.JumpPosition
import jadx.gui.utils.NLS
import jadx.gui.utils.SimpleListener
import jadx.gui.utils.TextStandardActions
import jadx.gui.utils.UiUtils
import jadx.gui.utils.cache.ValueCache
import jadx.gui.utils.flow.UiFlowUtils
import jadx.gui.utils.layout.WrapLayout
import jadx.gui.utils.ui.DocumentUpdateListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Insets
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.ItemListener
import java.util.Collections
import java.util.EnumSet
import java.util.HashSet
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.ImageIcon
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JLabel
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JSpinner
import javax.swing.JTextField
import javax.swing.JToggleButton
import javax.swing.SpinnerNumberModel
import javax.swing.WindowConstants
import javax.swing.border.TitledBorder
import javax.swing.event.ChangeListener
import kotlin.time.Duration.Companion.milliseconds

/**
 * 文本/类/注释搜索对话框。
 *
 * **做什么**：根据预设（[SearchPreset]）与选项（[SearchOptions]）构建后台搜索任务，
 * 通过 Flow 防抖地把输入变化转为搜索请求，结果填入 [CommonSearchDialog] 的表格。
 *
 * **线程模型（N1c 协程化）**：输入事件用 [Flow] 合并 + `debounce`，在 [scope]（EDT）上收集，
 * 实际搜索在单线程调度器 [searchBackgroundDispatcher] 上执行，UI 更新回到 EDT。
 */
@OptIn(FlowPreview::class)
class SearchDialog private constructor(
	mainWindow: MainWindow,
	private val searchPreset: SearchPreset,
	additionalOptions: Set<SearchOptions>,
) : CommonSearchDialog(mainWindow, NLS.str("menu.text_search")) {

	private val options: MutableSet<SearchOptions> = buildOptions(searchPreset).also { it.addAll(additionalOptions) }
	private val optionsListener: SimpleListener<Set<SearchOptions>> = SimpleListener()

	private lateinit var searchField: JTextField
	private lateinit var packageField: JTextField
	private lateinit var resExtField: JTextField
	private lateinit var resSizeLimit: JSpinner

	private var searchTask: SearchTask? = null
	private lateinit var loadAllButton: JButton
	private lateinit var loadMoreButton: JButton
	private lateinit var stopBtn: JButton
	private lateinit var sortBtn: JButton

	private var searchJob: Job? = null
	private lateinit var searchEmitter: SearchEventEmitter
	private var activeTabListener: ChangeListener? = null

	/** 协程作用域：仅用于收集搜索任务进度流，随对话框 [dispose] 取消。 */
	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Swing)
	private var progressJob: Job? = null

	private var initSearchText: String? = null
	private var initSearchPackage: String? = null

	// 待处理结果的临时列表
	private val pendingResults: MutableList<JNode> = ArrayList()

	/** 用单线程执行全部后台工作，从而无需额外同步。 */
	private val searchBackgroundExecutor: ExecutorService = Executors.newSingleThreadExecutor()

	/** 搜索执行调度器（单线程），由 [searchBackgroundExecutor] 支持。 */
	private val searchBackgroundDispatcher: ExecutorCoroutineDispatcher = searchBackgroundExecutor.asCoroutineDispatcher()

	// 跨搜索缓存
	private val includedClsCache = ValueCache<String, List<JavaClass>>()
	private val batchesCache = ValueCache<List<JavaClass>, List<List<JavaClass>>>()

	init {
		loadWindowPos()
		initUI()
		initSearchEvents()
		registerInitOnOpen()
		registerActiveTabListener()
	}

	enum class SearchPreset {
		TEXT,
		CLASS,
		COMMENT,
	}

	enum class SearchOptions {
		CLASS,
		METHOD,
		FIELD,
		CODE,
		RESOURCE,
		COMMENT,

		IGNORE_CASE,
		USE_REGEX,
		ACTIVE_TAB,
	}

	override fun dispose() {
		searchJob?.cancel()
		progressJob?.cancel()
		scope.cancel()
		resultsModel.clear()
		removeActiveTabListener()
		if (!searchBackgroundExecutor.isShutdown) {
			searchBackgroundExecutor.execute {
				stopSearchTask()
				unloadTempData()
			}
			// 关闭调度器（shutdown 会先执行完已排队的清理任务）
			searchBackgroundDispatcher.close()
		}
		super.dispose()
	}

	private fun buildOptions(preset: SearchPreset): MutableSet<SearchOptions> {
		var searchOptions = cache.getLastSearchOptions().get(preset)
		if (searchOptions == null) {
			searchOptions = EnumSet.noneOf(SearchOptions::class.java)
		}
		when (preset) {
			SearchPreset.TEXT -> if (searchOptions.isEmpty()) {
				searchOptions.add(SearchOptions.CODE)
				searchOptions.add(SearchOptions.IGNORE_CASE)
			}

			SearchPreset.CLASS -> searchOptions.add(SearchOptions.CLASS)

			SearchPreset.COMMENT -> {
				searchOptions.add(SearchOptions.COMMENT)
				searchOptions.remove(SearchOptions.ACTIVE_TAB)
			}
		}
		return searchOptions
	}

	override fun openInit() {
		val searchText = initSearchText ?: cache.getLastSearch()
		if (searchText != null) {
			searchField.setText(searchText)
			searchField.selectAll()
		}
		val searchPackage = initSearchPackage ?: cache.getLastSearchPackage()
		if (searchPackage != null) {
			packageField.setText(searchPackage)
		}
		searchField.requestFocus()
		resultsTable.initColumnWidth()

		if (options.contains(SearchOptions.COMMENT)) {
			// 空输入时展示全部注释
			searchEmitter.emitSearch()
		}
	}

	private fun initUI() {
		searchField = JTextField()
		TextStandardActions.attach(searchField)
		addSearchHistoryButton()
		searchField.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true)

		val autoSearch = mainWindow.getSettings().isUseAutoSearch
		val searchBtn = JButton(NLS.str("search_dialog.search_button"))
		searchBtn.setVisible(!autoSearch)
		searchBtn.addActionListener { searchEmitter.emitSearch() }

		val autoSearchCB = JCheckBox(NLS.str("search_dialog.auto_search"))
		autoSearchCB.isSelected = autoSearch
		autoSearchCB.addActionListener {
			val newValue = autoSearchCB.isSelected
			mainWindow.getSettings().saveUseAutoSearch(newValue)
			searchBtn.setVisible(!newValue)
			initSearchEvents()
			if (newValue) {
				searchEmitter.emitSearch()
			}
		}

		val searchButtons = JPanel()
		searchButtons.setLayout(BoxLayout(searchButtons, BoxLayout.LINE_AXIS))
		searchButtons.add(searchBtn)
		searchButtons.add(Box.createRigidArea(Dimension(5, 0)))
		searchButtons.add(makeOptionsToggleButton(NLS.str("search_dialog.ignorecase"), Icons.ICON_MATCH, Icons.ICON_MATCH_SELECTED, SearchOptions.IGNORE_CASE))
		searchButtons.add(Box.createRigidArea(Dimension(5, 0)))
		searchButtons.add(makeOptionsToggleButton(NLS.str("search_dialog.regex"), Icons.ICON_REGEX, Icons.ICON_REGEX_SELECTED, SearchOptions.USE_REGEX))
		searchButtons.add(Box.createRigidArea(Dimension(5, 0)))
		searchButtons.add(makeOptionsToggleButton(NLS.str("search_dialog.active_tab"), Icons.ICON_ACTIVE_TAB, Icons.ICON_ACTIVE_TAB_SELECTED, SearchOptions.ACTIVE_TAB))
		searchButtons.add(Box.createRigidArea(Dimension(5, 0)))
		searchButtons.add(autoSearchCB)

		val searchFieldPanel = JPanel()
		searchFieldPanel.setLayout(BorderLayout(5, 5))
		searchFieldPanel.add(JLabel(NLS.str("search_dialog.open_by_name")), BorderLayout.LINE_START)
		searchFieldPanel.add(searchField, BorderLayout.CENTER)
		searchFieldPanel.add(searchButtons, BorderLayout.LINE_END)

		val searchInPanel = JPanel()
		searchInPanel.setLayout(BoxLayout(searchInPanel, BoxLayout.LINE_AXIS))
		searchInPanel.setBorder(BorderFactory.createTitledBorder(NLS.str("search_dialog.search_in")))
		searchInPanel.add(makeOptionsCheckBox(NLS.str("search_dialog.class"), SearchOptions.CLASS))
		searchInPanel.add(makeOptionsCheckBox(NLS.str("search_dialog.method"), SearchOptions.METHOD))
		searchInPanel.add(makeOptionsCheckBox(NLS.str("search_dialog.field"), SearchOptions.FIELD))
		searchInPanel.add(makeOptionsCheckBox(NLS.str("search_dialog.code"), SearchOptions.CODE))
		searchInPanel.add(makeOptionsCheckBox(NLS.str("search_dialog.resource"), SearchOptions.RESOURCE))
		searchInPanel.add(makeOptionsCheckBox(NLS.str("search_dialog.comments"), SearchOptions.COMMENT))

		packageField = JTextField(Math.min(100, maxPkgLen))
		TextStandardActions.attach(packageField)
		packageField.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true)
		packageField.setToolTipText(NLS.str("search_dialog.limit_package"))

		val searchPackagePanel = JPanel(BorderLayout())
		searchPackagePanel.setBorder(BorderFactory.createTitledBorder(NLS.str("search_dialog.limit_package")))
		searchPackagePanel.add(packageField, BorderLayout.CENTER)
		val minPanelSize = calcMinSizeForTitledBorder(searchPackagePanel)
		searchPackagePanel.preferredSize = Dimension(maxOf(packageField.getPreferredSize().width, minPanelSize.width), minPanelSize.height)

		resExtField = JTextField(30)
		TextStandardActions.attach(resExtField)
		resExtField.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true)
		resExtField.setToolTipText(NLS.str("preferences.res_file_ext"))
		val resFilterStr = mainWindow.getProject().searchResourcesFilter
		resExtField.setText(resFilterStr)

		val resFilter = ResourceFilter.parse(resFilterStr)

		val textResBox = JCheckBox(NLS.str("search_dialog.res_text"))
		textResBox.isSelected = resFilter.getContentTypes().contains(ResourceContentType.CONTENT_TEXT)
		val binResBox = JCheckBox(NLS.str("search_dialog.res_binary"))
		binResBox.isSelected = resFilter.getContentTypes().contains(ResourceContentType.CONTENT_BINARY)

		val resContentTypeListener = ItemListener {
			try {
				val contentTypes = EnumSet.noneOf(ResourceContentType::class.java)
				if (textResBox.isSelected) {
					contentTypes.add(ResourceContentType.CONTENT_TEXT)
				}
				if (binResBox.isSelected) {
					contentTypes.add(ResourceContentType.CONTENT_BINARY)
				}
				val newStr = ResourceFilter.withContentType(resExtField.getText(), contentTypes)
				if (newStr != resExtField.getText()) {
					resExtField.setText(newStr)
				}
			} catch (e: Exception) {
				// ignore
			}
		}
		textResBox.addItemListener(resContentTypeListener)
		binResBox.addItemListener(resContentTypeListener)

		resExtField.getDocument().addDocumentListener(
			DocumentUpdateListener {
				UiUtils.uiRun {
					try {
						val filter = ResourceFilter.parse(resExtField.getText())
						textResBox.isSelected = filter.getContentTypes().contains(ResourceContentType.CONTENT_TEXT)
						binResBox.isSelected = filter.getContentTypes().contains(ResourceContentType.CONTENT_BINARY)
					} catch (e: Exception) {
						// ignore
					}
				}
			},
		)

		val resExtFilePanel = JPanel()
		resExtFilePanel.setLayout(BoxLayout(resExtFilePanel, BoxLayout.LINE_AXIS))
		resExtFilePanel.setBorder(BorderFactory.createTitledBorder(NLS.str("preferences.res_file_ext")))
		resExtFilePanel.add(resExtField)
		resExtFilePanel.add(textResBox)
		resExtFilePanel.add(binResBox)
		resExtFilePanel.preferredSize = calcMinSizeForTitledBorder(resExtFilePanel)

		resSizeLimit = JSpinner(SpinnerNumberModel(mainWindow.getProject().searchResourcesSizeLimit, 0, Int.MAX_VALUE, 1))
		resSizeLimit.setToolTipText(NLS.str("preferences.res_skip_file"))

		val sizeLimitPanel = JPanel(BorderLayout())
		sizeLimitPanel.setBorder(BorderFactory.createTitledBorder(NLS.str("preferences.res_skip_file")))
		sizeLimitPanel.add(resSizeLimit, BorderLayout.CENTER)
		sizeLimitPanel.preferredSize = calcMinSizeForTitledBorder(sizeLimitPanel)

		val optionsPanel = JPanel(WrapLayout(FlowLayout.LEFT))
		optionsPanel.add(searchInPanel)
		optionsPanel.add(searchPackagePanel)
		optionsPanel.add(resExtFilePanel)
		optionsPanel.add(sizeLimitPanel)

		optionsListener.addListener { searchOptions ->
			val codeSearch = Utils.isSetContainsAny(
				searchOptions,
				EnumSet.of(SearchOptions.CODE, SearchOptions.CLASS, SearchOptions.METHOD, SearchOptions.FIELD, SearchOptions.COMMENT),
			)
			searchPackagePanel.setVisible(codeSearch)
			val resSearch = searchOptions.contains(SearchOptions.RESOURCE)
			resExtFilePanel.setVisible(resSearch)
			sizeLimitPanel.setVisible(resSearch)
			optionsPanel.revalidate()
			optionsPanel.repaint()
		}

		val searchPane = JPanel()
		searchPane.setLayout(BoxLayout(searchPane, BoxLayout.PAGE_AXIS))
		searchPane.add(searchFieldPanel)
		searchPane.add(Box.createRigidArea(Dimension(0, 5)))
		searchPane.add(optionsPanel)

		initCommon()
		val resultsPanel = initResultsTable()
		val buttonPane = initButtonsPanel()

		val contentPanel = JPanel()
		contentPanel.setLayout(BorderLayout(5, 5))
		contentPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10))
		contentPanel.add(searchPane, BorderLayout.PAGE_START)
		contentPanel.add(resultsPanel, BorderLayout.CENTER)
		contentPanel.add(buttonPane, BorderLayout.PAGE_END)
		getContentPane().add(contentPanel)

		addComponentListener(object : ComponentAdapter() {
			override fun componentResized(e: ComponentEvent) {
				optionsPanel.revalidate()
				optionsPanel.repaint()
			}
		})
		setLocationRelativeTo(null)
		setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE)
	}

	private val maxPkgLen: Int get() {
		val cacheObject: CacheObject = mainWindow.getCacheObject()
		val maxPkgLength = cacheObject.getMaxPkgLength()
		if (maxPkgLength != 0) {
			return maxPkgLength
		}
		var max = 1
		for (pkg in mainWindow.getWrapper().rootNode.packages) {
			val len = pkg.getPkgInfo().fullName.length
			if (len > max) {
				max = len
			}
		}
		cacheObject.setMaxPkgLength(max)
		return max
	}

	/** 计算带标题边框面板最小尺寸的变通方法。 */
	private fun calcMinSizeForTitledBorder(panel: JPanel): Dimension {
		val border = panel.getBorder() as TitledBorder
		val borderInsets: Insets = border.getBorderInsets(panel)
		val insets = 2 * (borderInsets.left + borderInsets.right)
		val titleWidth = panel.getFontMetrics(border.getTitleFont()).stringWidth(border.getTitle())
		return Dimension(titleWidth + insets, panel.getPreferredSize().height)
	}

	private fun addSearchHistoryButton() {
		val searchHistoryButton = JButton(FlatSearchWithHistoryIcon(true))
		searchHistoryButton.setToolTipText(NLS.str("search_dialog.search_history"))
		searchHistoryButton.addActionListener {
			val popupMenu = JPopupMenu()
			val searchHistory = mainWindow.getProject().searchHistory
			if (searchHistory.isEmpty()) {
				popupMenu.add("(empty)")
			} else {
				for (str in searchHistory) {
					val item = popupMenu.add(str)
					item.addActionListener { searchField.setText(str) }
				}
			}
			popupMenu.show(searchHistoryButton, 0, searchHistoryButton.getHeight())
		}
		searchField.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_COMPONENT, searchHistoryButton)
	}

	override fun addResultsActions(resultsActionsPanel: JPanel) {
		loadAllButton = JButton(NLS.str("search_dialog.load_all"))
		loadAllButton.addActionListener { loadMoreResults(true) }
		loadAllButton.isEnabled = false

		loadMoreButton = JButton(NLS.str("search_dialog.load_more"))
		loadMoreButton.addActionListener { loadMoreResults(false) }
		loadMoreButton.isEnabled = false

		stopBtn = JButton(NLS.str("search_dialog.stop"))
		stopBtn.addActionListener { pauseSearch() }
		stopBtn.isEnabled = false

		sortBtn = JButton(NLS.str("search_dialog.sort_results"))
		sortBtn.addActionListener {
			synchronized(pendingResults) {
				resultsModel.sort()
				resultsTable.updateTable()
			}
		}
		sortBtn.isEnabled = false

		resultsActionsPanel.add(loadAllButton)
		resultsActionsPanel.add(Box.createRigidArea(Dimension(10, 0)))
		resultsActionsPanel.add(loadMoreButton)
		resultsActionsPanel.add(Box.createRigidArea(Dimension(10, 0)))
		resultsActionsPanel.add(stopBtn)
		resultsActionsPanel.add(Box.createRigidArea(Dimension(10, 0)))
		resultsActionsPanel.add(stopBtn)
		super.addResultsActions(resultsActionsPanel)
		resultsActionsPanel.add(Box.createRigidArea(Dimension(10, 0)))
		resultsActionsPanel.add(sortBtn)
	}
	private fun initSearchEvents() {
		searchJob?.cancel()
		searchJob = null
		searchEmitter = SearchEventEmitter()
		val searchEvents: Flow<String>
		if (mainWindow.getSettings().isUseAutoSearch) {
			searchEvents = listOf(
				UiFlowUtils.textFieldChanges(searchField),
				UiFlowUtils.textFieldEnterPress(searchField),
				UiFlowUtils.textFieldChanges(packageField),
				UiFlowUtils.textFieldEnterPress(packageField),
				UiFlowUtils.textFieldChanges(resExtField),
				UiFlowUtils.textFieldEnterPress(resExtField),
				UiFlowUtils.spinnerChanges(resSizeLimit),
				UiFlowUtils.spinnerEnterPress(resSizeLimit),
				searchEmitter.flow,
			).merge()
		} else {
			searchEvents = listOf(
				UiFlowUtils.textFieldEnterPress(searchField),
				UiFlowUtils.textFieldEnterPress(packageField),
				UiFlowUtils.textFieldEnterPress(resExtField),
				UiFlowUtils.spinnerEnterPress(resSizeLimit),
				searchEmitter.flow,
			).merge()
		}
		searchJob = scope.launch {
			searchEvents
				.debounce(SEARCH_DEBOUNCE_MS.milliseconds)
				.collect { text ->
					withContext(searchBackgroundDispatcher) {
						this@SearchDialog.search(text)
					}
				}
		}

		// 设置初始值
		optionsListener.sendUpdate(options)
	}

	private fun search(text: String) {
		UiUtils.notUiThreadGuard()
		stopSearchTask()
		UiUtils.uiRun { resetSearch() }
		val task = prepareSearch(text)
		searchTask = task
		if (task == null) {
			return
		}
		UiUtils.uiRunAndWait {
			updateTableHighlight()
			prepareForSearch()
		}
		task.setResultsLimit(mainWindow.getSettings().searchResultsPerPage)
		progressJob?.cancel()
		progressJob = scope.launch {
			task.getProgressFlow().collect { progress -> updateProgress(progress) }
		}
		task.fetchResults()
		LOG.debug("Total search items count estimation: {}", task.getTaskProgress().total())
	}

	private fun prepareSearch(text: String): SearchTask? {
		if (options.isEmpty()) {
			return null
		}
		// 允许空文本用于注释搜索
		if (text.isEmpty() && !options.contains(SearchOptions.COMMENT)) {
			return null
		}
		LOG.debug("Building search for '{}', options: {}", text, options)
		val searchSettings = SearchSettings(text)
		searchSettings.setIgnoreCase(options.contains(SearchOptions.IGNORE_CASE))
		searchSettings.setUseRegex(options.contains(SearchOptions.USE_REGEX))
		searchSettings.setSearchPkgStr(packageField.getText().trim())
		searchSettings.setResFilterStr(resExtField.getText().trim())
		searchSettings.setResSizeLimit(resSizeLimit.getValue() as Int)

		val error = searchSettings.prepare(mainWindow)
		UiUtils.highlightAsErrorField(searchField, !StringUtils.isEmpty(error))
		if (!StringUtils.isEmpty(error)) {
			resultsInfoLabel.setText(error)
			return null
		}

		val newSearchTask = SearchTask(
			mainWindow,
			{ node -> addSearchResult(node) },
			{ status, complete -> searchFinished(status, complete) },
		)
		if (!buildSearch(newSearchTask, text, searchSettings)) {
			UiUtils.highlightAsErrorField(searchField, true)
			return null
		}
		// 保存搜索设置
		mainWindow.getProject().setSearchResourcesFilter(resExtField.getText().trim())
		mainWindow.getProject().setSearchResourcesSizeLimit(searchSettings.getResSizeLimit())
		return newSearchTask
	}

	private fun buildSearch(newSearchTask: SearchTask, text: String, searchSettings: SearchSettings): Boolean {
		var searchClasses: List<JavaClass>
		if (options.contains(SearchOptions.ACTIVE_TAB)) {
			val currentPos = mainWindow.getTabbedPane().currentPosition
			if (currentPos == null) {
				resultsInfoLabel.setText("Can't search in current tab")
				return false
			}
			val currentNode = currentPos.getNode()
			if (currentNode is JClass) {
				val activeCls = checkNotNull(currentNode.getRootClass())
				searchSettings.setActiveCls(activeCls)
				searchClasses = Collections.singletonList(activeCls.getCls())
			} else if (currentNode is JResource) {
				searchSettings.setActiveResource(currentNode)
				searchClasses = Collections.emptyList()
			} else {
				resultsInfoLabel.setText("Can't search in current tab")
				return false
			}
		} else {
			searchClasses = includedClsCache.get(mainWindow.getSettings().excludedPackages) {
				mainWindow.getWrapper().includedClassesWithInners
			}
		}
		val searchPkg: JavaPackage? = searchSettings.getSearchPackage()
		if (searchPkg != null) {
			searchClasses = searchClasses.filter { cls -> searchSettings.isInSearchPkg(cls) }
		}
		if (text.isEmpty() && options.contains(SearchOptions.COMMENT)) {
			// 允许空文本用于注释搜索
			newSearchTask.addProviderJob(CommentSearchProvider(mainWindow, searchSettings, searchClasses))
			return true
		}
		if (searchClasses.isNotEmpty()) {
			// 使用有序执行以加快快速任务
			val merged = MergedSearchProvider()
			if (options.contains(SearchOptions.CLASS)) {
				merged.add(ClassSearchProvider(mainWindow, searchSettings, searchClasses))
			}
			if (options.contains(SearchOptions.METHOD)) {
				merged.add(MethodSearchProvider(mainWindow, searchSettings, searchClasses))
			}
			if (options.contains(SearchOptions.FIELD)) {
				merged.add(FieldSearchProvider(mainWindow, searchSettings, searchClasses))
			}
			if (!merged.isEmpty) {
				merged.prepare()
				newSearchTask.addProviderJob(merged)
			}

			if (options.contains(SearchOptions.CODE)) {
				val clsCount = searchClasses.size
				if (clsCount == 1) {
					newSearchTask.addProviderJob(CodeSearchProvider(mainWindow, searchSettings, searchClasses, null))
				} else if (clsCount > 1) {
					val topClasses = ListUtils.filter(searchClasses) { c -> !c.isInner() }
					val batches = batchesCache.get(topClasses) { clsList -> mainWindow.getWrapper().buildDecompileBatches(clsList) }
					val includedClasses = HashSet(topClasses)
					for (batch in batches) {
						newSearchTask.addProviderJob(CodeSearchProvider(mainWindow, searchSettings, batch, includedClasses))
					}
				}
			}
			if (options.contains(SearchOptions.COMMENT)) {
				newSearchTask.addProviderJob(CommentSearchProvider(mainWindow, searchSettings, searchClasses))
			}
		}
		if (options.contains(SearchOptions.RESOURCE)) {
			newSearchTask.addProviderJob(ResourceSearchProvider(mainWindow, searchSettings, this))
		}
		return true
	}
	override fun openItem(node: JNode) {
		if (mainWindow.getSettings().isUseAutoSearch) {
			// 自动搜索时只保存能打开节点的搜索词
			mainWindow.getProject().addToSearchHistory(searchField.getText())
		}
		super.openItem(node)
	}

	private fun pauseSearch() {
		stopBtn.isEnabled = false
		searchBackgroundExecutor.execute {
			val task = searchTask
			if (task != null) {
				task.cancel()
			}
		}
	}

	private fun stopSearchTask() {
		UiUtils.notUiThreadGuard()
		val task = searchTask
		if (task != null) {
			task.cancel()
			task.waitTask()
			searchTask = null
		}
	}

	private fun loadMoreResults(all: Boolean) {
		searchBackgroundExecutor.execute {
			val task = searchTask
			if (task == null) {
				return@execute
			}
			task.cancel()
			task.waitTask()
			UiUtils.uiRunAndWait { prepareForSearch() }
			if (all) {
				task.setResultsLimit(0)
			}
			task.fetchResults()
		}
	}

	private fun resetSearch() {
		UiUtils.uiThreadGuard()
		resultsModel.clear()
		resultsTable.updateTable()
		synchronized(pendingResults) {
			pendingResults.clear()
		}
		updateProgressLabel("")
		progressPane.setVisible(false)
		warnLabel.setVisible(false)
		loadAllButton.isEnabled = false
		loadMoreButton.isEnabled = false
	}

	private fun prepareForSearch() {
		UiUtils.uiThreadGuard()
		stopBtn.isEnabled = true
		sortBtn.isEnabled = false
		showSearchState()
		progressStartCommon()
	}

	private fun addSearchResult(node: JNode) {
		requireNotNull(node)
		synchronized(pendingResults) {
			UiUtils.notUiThreadGuard()
			pendingResults.add(node)
		}
	}

	private fun updateTable() {
		synchronized(pendingResults) {
			UiUtils.uiThreadGuard()
			Collections.sort(pendingResults)
			resultsModel.addAll(pendingResults)
			pendingResults.clear()
			resultsTable.updateTable()
		}
	}

	private fun updateTableHighlight() {
		val text = searchField.getText()
		updateHighlightContext(text, !options.contains(SearchOptions.IGNORE_CASE), options.contains(SearchOptions.USE_REGEX), false)
		cache.setLastSearch(text)
		cache.setLastSearchPackage(packageField.getText())
		cache.getLastSearchOptions()[searchPreset] = options
		if (!mainWindow.getSettings().isUseAutoSearch) {
			mainWindow.getProject().addToSearchHistory(text)
		}
	}

	private fun updateProgress(progress: ITaskProgress) {
		UiUtils.uiRun {
			progressPane.setProgress(progress)
			updateTable()
		}
	}

	fun updateProgressLabel(text: String) {
		UiUtils.uiRun { progressInfoLabel.setText(text) }
	}

	private fun searchFinished(status: ITaskInfo, complete: Boolean) {
		UiUtils.uiThreadGuard()
		LOG.debug("Search complete: {}, complete: {}", status, complete)
		loadAllButton.isEnabled = !complete
		loadMoreButton.isEnabled = !complete
		stopBtn.isEnabled = false
		progressFinishedCommon()
		updateTable()
		updateProgressLabel(complete)
		sortBtn.isEnabled = resultsModel.getRowCount() != 0
	}

	private fun unloadTempData() {
		mainWindow.getWrapper().unloadClasses()
		System.gc()
	}

	private fun makeOptionsCheckBox(name: String, opt: SearchOptions): JCheckBox {
		val chBox = JCheckBox(name)
		chBox.isSelected = options.contains(opt)
		chBox.addItemListener {
			if (chBox.isSelected) {
				options.add(opt)
			} else {
				options.remove(opt)
			}
			optionsListener.sendUpdate(options)
			searchEmitter.emitSearch()
		}
		return chBox
	}

	private fun makeOptionsToggleButton(name: String, icon: ImageIcon, selectedIcon: ImageIcon, opt: SearchOptions): JToggleButton {
		val toggleButton = JToggleButton()
		toggleButton.setToolTipText(name)
		toggleButton.setIcon(icon)
		toggleButton.setSelectedIcon(selectedIcon)
		toggleButton.isSelected = options.contains(opt)
		toggleButton.addItemListener {
			if (toggleButton.isSelected) {
				options.add(opt)
			} else {
				options.remove(opt)
			}
			optionsListener.sendUpdate(options)
			searchEmitter.emitSearch()
		}
		return toggleButton
	}

	override fun loadFinished() {
		resultsTable.setEnabled(true)
		searchField.setEnabled(true)
		searchEmitter.emitSearch()
	}

	override fun loadStart() {
		resultsTable.setEnabled(false)
		searchField.setEnabled(false)
	}

	private fun registerActiveTabListener() {
		removeActiveTabListener()
		val listener = ChangeListener {
			if (options.contains(SearchOptions.ACTIVE_TAB)) {
				LOG.debug("active tab change event received")
				searchEmitter.emitSearch()
			}
		}
		activeTabListener = listener
		mainWindow.getTabbedPane().addChangeListener(listener)
	}

	private fun removeActiveTabListener() {
		val listener = activeTabListener
		if (listener != null) {
			mainWindow.getTabbedPane().removeChangeListener(listener)
			activeTabListener = null
		}
	}

	private inner class SearchEventEmitter {
		private val events = MutableSharedFlow<String>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

		val flow: Flow<String> get() = events

		fun emitSearch() {
			events.tryEmit(searchField.getText())
		}
	}

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(SearchDialog::class.java)
		private const val serialVersionUID = -5105405456969134105L

		/** 输入防抖窗口（毫秒）。 */
		private const val SEARCH_DEBOUNCE_MS = 100L

		fun search(window: MainWindow, preset: SearchPreset) {
			val searchDialog = SearchDialog(window, preset, emptySet())
			show(searchDialog, window)
		}

		fun searchInActiveTab(window: MainWindow, preset: SearchPreset) {
			val searchDialog = SearchDialog(window, preset, EnumSet.of(SearchOptions.ACTIVE_TAB))
			show(searchDialog, window)
		}

		fun searchText(window: MainWindow, text: String) {
			val searchDialog = SearchDialog(window, SearchPreset.TEXT, emptySet())
			searchDialog.initSearchText = text
			show(searchDialog, window)
		}

		fun searchPackage(window: MainWindow, packageName: String) {
			val searchDialog = SearchDialog(window, SearchPreset.TEXT, emptySet())
			searchDialog.initSearchPackage = packageName
			show(searchDialog, window)
		}

		private fun show(searchDialog: SearchDialog, mw: MainWindow) {
			mw.addLoadListener(object : ILoadListener {
				override fun update(loaded: Boolean): Boolean {
					if (!loaded) {
						searchDialog.dispose()
						return true
					}
					return false
				}
			})
			searchDialog.isVisible = true
		}
	}
}

package kadx.gui.settings.ui

import kadx.api.CommentsLevel
import kadx.api.DecompilationMode
import kadx.api.KadxArgs
import kadx.api.KadxArgs.UseKotlinMethodsForVarNames
import kadx.api.args.GeneratedRenamesMappingFileMode
import kadx.api.args.IntegerFormat
import kadx.api.args.ResourceNameSource
import kadx.api.args.UseSourceNameAsClassNameAlias
import kadx.api.plugins.events.KadxEvents
import kadx.api.plugins.events.types.ReloadSettingsWindow
import kadx.api.plugins.gui.ISettingsGroup
import kadx.core.utils.StringUtils
import kadx.core.utils.exceptions.KadxRuntimeException
import kadx.gui.settings.KadxSettings
import kadx.gui.settings.KadxSettingsData
import kadx.gui.settings.KadxUpdateChannel
import kadx.gui.settings.LineNumbersMode
import kadx.gui.settings.XposedCodegenLanguage
import kadx.gui.settings.data.SaveOptionEnum
import kadx.gui.settings.font.FontAdapter
import kadx.gui.settings.font.FontSettings
import kadx.gui.settings.ui.cache.CacheSettingsGroup
import kadx.gui.settings.ui.font.KadxFontDialog
import kadx.gui.settings.ui.plugins.PluginSettings
import kadx.gui.settings.ui.shortcut.ShortcutsSettingsGroup
import kadx.gui.ui.MainWindow
import kadx.gui.ui.codearea.theme.ThemeIdAndName
import kadx.gui.ui.tab.dnd.TabDndGhostType
import kadx.gui.utils.FontUtils
import kadx.gui.utils.LafManager
import kadx.gui.utils.LangLocale
import kadx.gui.utils.NLS
import kadx.gui.utils.UiUtils
import kadx.gui.utils.ui.ActionHandler
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.awt.BorderLayout
import java.awt.Container
import java.awt.Dialog
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.event.ItemEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.function.Consumer
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.InputMap
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSpinner
import javax.swing.JSplitPane
import javax.swing.KeyStroke
import javax.swing.ScrollPaneConstants
import javax.swing.SpinnerNumberModel
import javax.swing.SwingUtilities
import javax.swing.WindowConstants

/**
 * kadx 设置窗口：左侧树形导航 + 右侧各设置页面。
 *
 * **做什么**：构建反编译、去混淆、重命名、缓存、外观、快捷键、项目、插件、其它等设置组，
 * 并把用户改动即时写回 [KadxSettings]；保存时按需触发项目重载。
 *
 * **线程模型**：保持 Swing 线程模型，使用 `SwingUtilities.invokeLater` 与 [UiUtils.uiRun]。
 */
class KadxSettingsWindow(
	private val mainWindow: MainWindow,
	private val settings: KadxSettings,
) : JDialog() {

	private val startSettings: String
	private val startSettingsHash: String
	private val prevLang: LangLocale
	private val reloadListener: Consumer<ReloadSettingsWindow>

	private var needReloadFlag: Boolean = false
	private lateinit var tree: SettingsTree
	private var groups: MutableList<ISettingsGroup> = ArrayList()
	private lateinit var wrapGroupPanel: JPanel

	init {
		startSettings = settings.settingsJsonString
		startSettingsHash = calcSettingsHash()
		prevLang = settings.langLocale

		initUI()

		title = NLS.str("preferences.title")
		defaultCloseOperation = WindowConstants.DISPOSE_ON_CLOSE
		modalityType = Dialog.ModalityType.APPLICATION_MODAL
		pack()
		UiUtils.setWindowIcons(this)
		setLocationRelativeTo(null)
		if (!mainWindow.getSettings().loadWindowPos(this)) {
			setSize(700, 800)
		}
		reloadListener = Consumer { UiUtils.uiRun { reloadUI() } }
		mainWindow.events().global().addListener(KadxEvents.RELOAD_SETTINGS_WINDOW, reloadListener)
	}

	private fun reloadUI() {
		val selection = tree.selectionRows
		closeGroups(false)
		contentPane.removeAll()
		initUI()
		// 等待其它事件处理完成
		UiUtils.uiRun {
			tree.setSelectionRows(selection)
			SwingUtilities.updateComponentTreeUI(this)
		}
	}

	private fun initUI() {
		wrapGroupPanel = JPanel(BorderLayout(10, 10))

		groups = ArrayList()
		groups.add(makeDecompilationGroup())
		groups.add(makeDeobfuscationGroup())
		groups.add(makeRenameGroup())
		groups.add(CacheSettingsGroup(this))
		groups.add(makeAppearanceGroup())
		groups.add(ShortcutsSettingsGroup(this, settings))
		groups.add(makeProjectGroup())
		groups.add(PluginSettings(mainWindow, settings).build())
		groups.add(makeOtherGroup())

		tree = SettingsTree(this)
		tree.init(groups)
		tree.isFocusable = true
		val leftPane = JScrollPane(tree)
		leftPane.border = BorderFactory.createEmptyBorder(10, 10, 3, 3)

		val rightPane = JScrollPane(wrapGroupPanel)
		rightPane.verticalScrollBar.unitIncrement = 16
		rightPane.horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
		rightPane.border = BorderFactory.createEmptyBorder(10, 3, 3, 10)

		val splitPane = JSplitPane()
		splitPane.resizeWeight = 0.2
		splitPane.leftComponent = leftPane
		splitPane.rightComponent = rightPane

		contentPane.add(splitPane, BorderLayout.CENTER)
		contentPane.add(buildButtonsPane(), BorderLayout.PAGE_END)

		val strokeEsc = KeyStroke.getKeyStroke("ESCAPE")
		val inputMap: InputMap = rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
		inputMap.put(strokeEsc, "ESCAPE")
		rootPane.actionMap.put("ESCAPE", ActionHandler(Runnable { cancel() }))
	}

	private fun buildButtonsPane(): JPanel {
		val saveBtn = JButton(NLS.str("preferences.save"))
		saveBtn.addActionListener { save() }

		val cancelButton = JButton(NLS.str("preferences.cancel"))
		cancelButton.addActionListener { cancel() }

		val resetBtn = JButton(NLS.str("preferences.reset"))
		resetBtn.addActionListener { reset() }

		val copyBtn = JButton(NLS.str("preferences.copy"))
		copyBtn.addActionListener { copySettings() }

		val buttonPane = JPanel()
		buttonPane.layout = BoxLayout(buttonPane, BoxLayout.LINE_AXIS)
		buttonPane.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
		buttonPane.add(resetBtn)
		buttonPane.add(Box.createRigidArea(Dimension(10, 0)))
		buttonPane.add(copyBtn)
		buttonPane.add(Box.createHorizontalGlue())
		buttonPane.add(saveBtn)
		buttonPane.add(Box.createRigidArea(Dimension(10, 0)))
		buttonPane.add(cancelButton)

		rootPane.defaultButton = saveBtn
		return buttonPane
	}

	/**
	 * 按位置激活设置页。
	 *
	 * @param location 设置组的标题，或设置组实现类的类名（以 `.class` 结尾）
	 */
	fun activatePage(location: String) {
		if (location.endsWith(".class")) {
			val clsName = StringUtils.removeSuffix(location, ".class")
			for (group in groups) {
				val groupCls = group.javaClass.simpleName
				if (groupCls == clsName) {
					selectGroup(group)
					return
				}
			}
			throw KadxRuntimeException("No setting group class: $location")
		} else {
			for (group in groups) {
				if (group.getTitle() == location) {
					selectGroup(group)
					return
				}
			}
			throw KadxRuntimeException("No setting group with title: $location")
		}
	}

	fun selectGroup(group: ISettingsGroup) {
		tree.selectGroup(group)
	}

	fun activateGroup(group: ISettingsGroup?) {
		wrapGroupPanel.removeAll()
		if (group != null) {
			wrapGroupPanel.add(group.buildComponent())
		}
		wrapGroupPanel.updateUI()
	}

	private fun enableComponents(container: Container, enable: Boolean) {
		for (component in container.components) {
			if (component is Container) {
				enableComponents(component, enable)
			}
			component.isEnabled = enable
		}
	}

	private fun makeDeobfuscationGroup(): SettingsGroup {
		val deobfOn = JCheckBox()
		deobfOn.isSelected = settings.isDeobfuscationOn
		deobfOn.addItemListener { e ->
			settings.setDeobfuscationOn(e.stateChange == ItemEvent.SELECTED)
			needReload()
		}

		val minLenModel = SpinnerNumberModel(settings.deobfuscationMinLength, 0, Int.MAX_VALUE, 1)
		val minLenSpinner = JSpinner(minLenModel)
		minLenSpinner.addChangeListener {
			settings.setDeobfuscationMinLength(minLenSpinner.value as Int)
			needReload()
		}

		val maxLenModel = SpinnerNumberModel(settings.deobfuscationMaxLength, 0, Int.MAX_VALUE, 1)
		val maxLenSpinner = JSpinner(maxLenModel)
		maxLenSpinner.addChangeListener {
			settings.setDeobfuscationMaxLength(maxLenSpinner.value as Int)
			needReload()
		}

		val resNamesSource = JComboBox(ResourceNameSource.values())
		resNamesSource.selectedItem = settings.resourceNameSource
		resNamesSource.addActionListener {
			settings.setResourceNameSource(resNamesSource.selectedItem as ResourceNameSource)
			needReload()
		}

		val useHeaders = JCheckBox()
		useHeaders.isSelected = settings.isUseHeadersForDetectResourceExtensions
		useHeaders.addItemListener { e ->
			settings.setUseHeadersForDetectResourceExtensions(e.stateChange == ItemEvent.SELECTED)
			needReload()
		}

		val generatedRenamesMappingFileModeCB = JComboBox(GeneratedRenamesMappingFileMode.values())
		generatedRenamesMappingFileModeCB.selectedItem = settings.generatedRenamesMappingFileMode
		generatedRenamesMappingFileModeCB.addActionListener {
			val newValue = generatedRenamesMappingFileModeCB.selectedItem as GeneratedRenamesMappingFileMode
			if (newValue != settings.generatedRenamesMappingFileMode) {
				settings.setGeneratedRenamesMappingFileMode(newValue)
				needReload()
			}
		}

		val editWhitelistedEntities = JButton(NLS.str("preferences.excludedPackages.button"))
		editWhitelistedEntities.addActionListener {
			val prevWhitelistedEntities = settings.deobfuscationWhitelistStr
			val result = JOptionPane.showInputDialog(
				this,
				NLS.str("preferences.deobfuscation_whitelist.editDialog"),
				prevWhitelistedEntities,
			)
			if (result != null) {
				settings.setDeobfuscationWhitelistStr(result)
				if (prevWhitelistedEntities != result) {
					needReload()
				}
			}
		}

		val deobfGroup = SettingsGroup(NLS.str("preferences.deobfuscation"))
		deobfGroup.addRow(NLS.str("preferences.deobfuscation_on"), deobfOn)
		deobfGroup.addRow(NLS.str("preferences.deobfuscation_min_len"), minLenSpinner)
		deobfGroup.addRow(NLS.str("preferences.deobfuscation_max_len"), maxLenSpinner)
		deobfGroup.addRow(NLS.str("preferences.deobfuscation_res_name_source"), resNamesSource)
		deobfGroup.addRow(NLS.str("preferences.deobfuscation_res_use_headers"), useHeaders)
		deobfGroup.addRow(NLS.str("preferences.generated_renames_mapping_file_mode"), generatedRenamesMappingFileModeCB)
		deobfGroup.addRow(
			NLS.str("preferences.deobfuscation_whitelist"),
			NLS.str("preferences.deobfuscation_whitelist.tooltip"),
			editWhitelistedEntities,
		)

		deobfGroup.end()

		val connectedComponents: Collection<JComponent> = listOf(minLenSpinner, maxLenSpinner)
		deobfOn.addItemListener { e -> enableComponentList(connectedComponents, e.stateChange == ItemEvent.SELECTED) }
		enableComponentList(connectedComponents, settings.isDeobfuscationOn)
		return deobfGroup
	}

	private fun makeRenameGroup(): SettingsGroup {
		val renameCaseSensitive = JCheckBox()
		renameCaseSensitive.isSelected = settings.isRenameCaseSensitive
		renameCaseSensitive.addItemListener { e ->
			settings.updateRenameFlag(KadxArgs.RenameEnum.CASE, e.stateChange == ItemEvent.SELECTED)
			needReload()
		}

		val renameValid = JCheckBox()
		renameValid.isSelected = settings.isRenameValid
		renameValid.addItemListener { e ->
			settings.updateRenameFlag(KadxArgs.RenameEnum.VALID, e.stateChange == ItemEvent.SELECTED)
			needReload()
		}

		val renamePrintable = JCheckBox()
		renamePrintable.isSelected = settings.isRenamePrintable
		renamePrintable.addItemListener { e ->
			settings.updateRenameFlag(KadxArgs.RenameEnum.PRINTABLE, e.stateChange == ItemEvent.SELECTED)
			needReload()
		}

		val useSourceNameAsClassNameAlias = JComboBox(UseSourceNameAsClassNameAlias.values())
		useSourceNameAsClassNameAlias.selectedItem = settings.useSourceNameAsClassNameAlias
		useSourceNameAsClassNameAlias.addActionListener {
			settings.setUseSourceNameAsClassNameAlias(
				useSourceNameAsClassNameAlias.selectedItem as UseSourceNameAsClassNameAlias,
			)
			needReload()
		}

		val repeatLimit = JSpinner(SpinnerNumberModel(settings.sourceNameRepeatLimit, 1, Int.MAX_VALUE, 1))
		repeatLimit.addChangeListener {
			settings.setSourceNameRepeatLimit(repeatLimit.value as Int)
			needReload()
		}

		val group = SettingsGroup(NLS.str("preferences.rename"))
		group.addRow(NLS.str("preferences.rename_case"), renameCaseSensitive)
		group.addRow(NLS.str("preferences.rename_valid"), renameValid)
		group.addRow(NLS.str("preferences.rename_printable"), renamePrintable)
		group.addRow(NLS.str("preferences.rename_use_source_name_as_class_name_alias"), useSourceNameAsClassNameAlias)
		group.addRow(NLS.str("preferences.rename_source_name_repeat_limit"), repeatLimit)
		return group
	}

	private fun enableComponentList(connectedComponents: Collection<JComponent>, enabled: Boolean) {
		connectedComponents.forEach { it.isEnabled = enabled }
	}

	private fun makeProjectGroup(): SettingsGroup {
		val dropdown = JComboBox(SaveOptionEnum.values())
		dropdown.selectedItem = settings.saveOption
		dropdown.addActionListener {
			settings.setSaveOption(dropdown.selectedItem as SaveOptionEnum)
			needReload()
		}

		val group = SettingsGroup(NLS.str("preferences.project"))
		group.addRow(NLS.str("preferences.saveOption"), dropdown)

		return group
	}

	private fun makeAppearanceGroup(): SettingsGroup {
		val languageCbx = JComboBox(NLS.langLocales)
		for (locale in NLS.langLocales) {
			if (locale == settings.langLocale) {
				languageCbx.selectedItem = locale
				break
			}
		}
		languageCbx.addActionListener { settings.setLangLocale(languageCbx.selectedItem as LangLocale) }

		val editorThemeManager = mainWindow.getEditorThemeManager()
		val themesCbx = JComboBox(editorThemeManager.themeIdNameArray)
		themesCbx.selectedItem = editorThemeManager.currentThemeIdName
		themesCbx.addActionListener {
			val selected = themesCbx.selectedItem as ThemeIdAndName?
			if (selected != null) {
				settings.setEditorTheme(selected.getId())
				mainWindow.loadSettings()
			}
		}

		val lafCbx = JComboBox(LafManager.themes)
		lafCbx.selectedItem = settings.lafTheme
		lafCbx.addActionListener {
			settings.setLafTheme(lafCbx.selectedItem as String)
			mainWindow.loadSettings()
		}

		val uiZoomSpinner = JSpinner(SpinnerNumberModel(settings.uiZoom.toDouble(), 0.1, 10.0, 0.25))
		uiZoomSpinner.addChangeListener {
			val zoomValue = (uiZoomSpinner.value as Double).toFloat()
			settings.setUiZoom(zoomValue)
			mainWindow.loadSettings()
		}

		val applyUiZoomToFontsChB = JCheckBox()
		applyUiZoomToFontsChB.isSelected = settings.isApplyUiZoomToFonts
		applyUiZoomToFontsChB.addItemListener { e ->
			settings.setApplyUiZoomToFonts(e.stateChange == ItemEvent.SELECTED)
			mainWindow.loadSettings()
		}

		val group = SettingsGroup(NLS.str("preferences.appearance"))
		group.addRow(NLS.str("preferences.language"), languageCbx)
		group.addRow(NLS.str("preferences.ui_zoom"), uiZoomSpinner)
		group.addRow(NLS.str("preferences.apply_ui_zoom_to_fonts"), applyUiZoomToFontsChB)

		val fontSettings: FontSettings = settings.getFontSettings()
		addFontEditor(group, NLS.str("preferences.ui_font"), fontSettings.getUiFontAdapter(), false)
		addFontEditor(group, NLS.str("preferences.code_font"), fontSettings.getCodeFontAdapter(), false)
		addFontEditor(group, NLS.str("preferences.smali_font"), fontSettings.getSmaliFontAdapter(), true)

		group.addRow(NLS.str("preferences.laf_theme"), lafCbx)
		group.addRow(NLS.str("preferences.theme"), themesCbx)

		val tabDndGhostTypeCbx = JComboBox(TabDndGhostType.values())
		tabDndGhostTypeCbx.selectedItem = settings.tabDndGhostType
		tabDndGhostTypeCbx.addActionListener {
			settings.setTabDndGhostType(tabDndGhostTypeCbx.selectedItem as TabDndGhostType)
			mainWindow.loadSettings()
		}
		group.addRow(NLS.str("preferences.tab_dnd_appearance"), tabDndGhostTypeCbx)

		return group
	}

	private fun addFontEditor(group: SettingsGroup, title: String, fontAdapter: FontAdapter, monospace: Boolean) {
		val fontLabel = JLabel(getFontLabelStr(fontAdapter.getFont()))
		val fontBtn = JButton(NLS.str("preferences.select_font"))
		fontBtn.addMouseListener(object : MouseAdapter() {
			override fun mouseClicked(e: MouseEvent) {
				val font = KadxFontDialog(this@KadxSettingsWindow, settings, title)
					.select(fontAdapter.getFont(), monospace)
				if (font != null) {
					fontLabel.text = getFontLabelStr(font)
					fontAdapter.setFont(font)
					mainWindow.loadSettings()
				}
			}
		})
		val fontPanel = JPanel()
		fontPanel.layout = FlowLayout(FlowLayout.LEFT)
		fontPanel.add(fontLabel)
		fontPanel.add(fontBtn)
		group.addRow(title, fontPanel)
	}

	private fun getFontLabelStr(font: Font): String = font.family + ' ' + FontUtils.convertFontStyleToString(font.style) + ' ' + font.size

	private fun makeDecompilationGroup(): SettingsGroup {
		val useDx = JCheckBox()
		useDx.isSelected = settings.isUseDx
		useDx.addItemListener { e ->
			settings.setUseDx(e.stateChange == ItemEvent.SELECTED)
			needReload()
		}

		val decompilationModeComboBox = JComboBox(DecompilationMode.values())
		decompilationModeComboBox.selectedItem = settings.decompilationMode
		decompilationModeComboBox.addActionListener {
			settings.setDecompilationMode(decompilationModeComboBox.selectedItem as DecompilationMode)
			needReload()
		}

		val showInconsistentCode = JCheckBox()
		showInconsistentCode.isSelected = settings.isShowInconsistentCode
		showInconsistentCode.addItemListener { e ->
			settings.setShowInconsistentCode(e.stateChange == ItemEvent.SELECTED)
			needReload()
		}

		val resourceDecode = JCheckBox()
		resourceDecode.isSelected = settings.isSkipResources
		resourceDecode.addItemListener { e ->
			settings.setSkipResources(e.stateChange == ItemEvent.SELECTED)
			needReload()
		}

		// fix for #1331
		val threadsCountValue = settings.threadsCount
		val threadsCountMax = Math.max(2, Math.max(threadsCountValue, Runtime.getRuntime().availableProcessors() * 2))
		val spinnerModel = SpinnerNumberModel(threadsCountValue, 1, threadsCountMax, 1)
		val threadsCount = JSpinner(spinnerModel)
		threadsCount.addChangeListener {
			settings.setThreadsCount(threadsCount.value as Int)
			needReload()
		}

		val editExcludedPackages = JButton(NLS.str("preferences.excludedPackages.button"))
		editExcludedPackages.addActionListener {
			val oldExcludedPackages = settings.excludedPackages
			val result = JOptionPane.showInputDialog(
				this,
				NLS.str("preferences.excludedPackages.editDialog"),
				settings.excludedPackages,
			)
			if (result != null) {
				settings.setExcludedPackages(result)
				if (oldExcludedPackages != result) {
					needReload()
				}
			}
		}

		val autoStartJobs = JCheckBox()
		autoStartJobs.isSelected = settings.isAutoStartJobs
		autoStartJobs.addItemListener { e -> settings.setAutoStartJobs(e.stateChange == ItemEvent.SELECTED) }

		val escapeUnicode = JCheckBox()
		escapeUnicode.isSelected = settings.isEscapeUnicode
		escapeUnicode.addItemListener { e ->
			settings.setEscapeUnicode(e.stateChange == ItemEvent.SELECTED)
			needReload()
		}

		val replaceConsts = JCheckBox()
		replaceConsts.isSelected = settings.isReplaceConsts
		replaceConsts.addItemListener { e ->
			settings.setReplaceConsts(e.stateChange == ItemEvent.SELECTED)
			needReload()
		}

		val respectBytecodeAccessModifiers = JCheckBox()
		respectBytecodeAccessModifiers.isSelected = settings.isRespectBytecodeAccessModifiers
		respectBytecodeAccessModifiers.addItemListener { e ->
			settings.setRespectBytecodeAccessModifiers(e.stateChange == ItemEvent.SELECTED)
			needReload()
		}

		val useImports = JCheckBox()
		useImports.isSelected = settings.isUseImports
		useImports.addItemListener { e ->
			settings.setUseImports(e.stateChange == ItemEvent.SELECTED)
			needReload()
		}

		val useDebugInfo = JCheckBox()
		useDebugInfo.isSelected = settings.isDebugInfo
		useDebugInfo.addItemListener { e ->
			settings.setDebugInfo(e.stateChange == ItemEvent.SELECTED)
			needReload()
		}

		val inlineAnonymous = JCheckBox()
		inlineAnonymous.isSelected = settings.isInlineAnonymousClasses
		inlineAnonymous.addItemListener { e ->
			settings.setInlineAnonymousClasses(e.stateChange == ItemEvent.SELECTED)
			needReload()
		}

		val inlineMethods = JCheckBox()
		inlineMethods.isSelected = settings.isInlineMethods
		inlineMethods.addItemListener { e ->
			settings.setInlineMethods(e.stateChange == ItemEvent.SELECTED)
			needReload()
		}

		val inlineKotlinLambdas = JCheckBox()
		inlineKotlinLambdas.isSelected = settings.isAllowInlineKotlinLambda
		inlineKotlinLambdas.addItemListener { e ->
			settings.setAllowInlineKotlinLambda(e.stateChange == ItemEvent.SELECTED)
			needReload()
		}

		val moveInnerClasses = JCheckBox()
		moveInnerClasses.isSelected = settings.isMoveInnerClasses
		moveInnerClasses.addItemListener { e ->
			settings.setMoveInnerClasses(e.stateChange == ItemEvent.SELECTED)
			needReload()
		}

		val extractFinally = JCheckBox()
		extractFinally.isSelected = settings.isExtractFinally
		extractFinally.addItemListener { e ->
			settings.setExtractFinally(e.stateChange == ItemEvent.SELECTED)
			needReload()
		}

		val restoreSwitchOverString = JCheckBox()
		restoreSwitchOverString.isSelected = settings.isRestoreSwitchOverString
		restoreSwitchOverString.addItemListener { e ->
			settings.setRestoreSwitchOverString(e.stateChange == ItemEvent.SELECTED)
			needReload()
		}

		val fsCaseSensitive = JCheckBox()
		fsCaseSensitive.isSelected = settings.isFsCaseSensitive
		fsCaseSensitive.addItemListener { e ->
			settings.setFsCaseSensitive(e.stateChange == ItemEvent.SELECTED)
			needReload()
		}

		val kotlinRenameVars = JComboBox(UseKotlinMethodsForVarNames.values())
		kotlinRenameVars.selectedItem = settings.useKotlinMethodsForVarNames
		kotlinRenameVars.addActionListener {
			settings.setUseKotlinMethodsForVarNames(kotlinRenameVars.selectedItem as UseKotlinMethodsForVarNames)
			needReload()
		}

		val commentsLevel = JComboBox(CommentsLevel.values())
		commentsLevel.selectedItem = settings.commentsLevel
		commentsLevel.addActionListener {
			settings.setCommentsLevel(commentsLevel.selectedItem as CommentsLevel)
			needReload()
		}

		val integerFormat = JComboBox(IntegerFormat.values())
		integerFormat.selectedItem = settings.integerFormat
		integerFormat.addActionListener {
			settings.setIntegerFormat(integerFormat.selectedItem as IntegerFormat)
			needReload()
		}

		val typeUpdatesLimitCount = JSpinner(
			SpinnerNumberModel(settings.typeUpdatesLimitCount, 1, Short.MAX_VALUE.toInt(), 1),
		)
		typeUpdatesLimitCount.addChangeListener {
			val newValue = typeUpdatesLimitCount.value as Int
			if (newValue < 1) {
				UiUtils.uiRun { typeUpdatesLimitCount.value = 1 }
			} else {
				settings.setTypeUpdatesLimitCount(newValue)
				needReload()
			}
		}

		val other = SettingsGroup(NLS.str("preferences.decompile"))
		other.addRow(NLS.str("preferences.threads"), threadsCount)
		other.addRow(
			NLS.str("preferences.excludedPackages"),
			NLS.str("preferences.excludedPackages.tooltip"),
			editExcludedPackages,
		)
		other.addRow(NLS.str("preferences.start_jobs"), autoStartJobs)
		other.addRow(NLS.str("preferences.decompilationMode"), decompilationModeComboBox)
		other.addRow(NLS.str("preferences.showInconsistentCode"), showInconsistentCode)
		other.addRow(NLS.str("preferences.escapeUnicode"), escapeUnicode)
		other.addRow(NLS.str("preferences.replaceConsts"), replaceConsts)
		other.addRow(NLS.str("preferences.respectBytecodeAccessModifiers"), respectBytecodeAccessModifiers)
		other.addRow(NLS.str("preferences.useImports"), useImports)
		other.addRow(NLS.str("preferences.useDebugInfo"), useDebugInfo)
		other.addRow(NLS.str("preferences.inlineAnonymous"), inlineAnonymous)
		other.addRow(NLS.str("preferences.inlineMethods"), inlineMethods)
		other.addRow(NLS.str("preferences.inlineKotlinLambdas"), inlineKotlinLambdas)
		other.addRow(NLS.str("preferences.moveInnerClasses"), moveInnerClasses)
		other.addRow(NLS.str("preferences.extractFinally"), extractFinally)
		other.addRow(NLS.str("preferences.restoreSwitchOverString"), restoreSwitchOverString)
		other.addRow(NLS.str("preferences.fsCaseSensitive"), fsCaseSensitive)
		other.addRow(NLS.str("preferences.useDx"), useDx)
		other.addRow(NLS.str("preferences.skipResourcesDecode"), resourceDecode)
		other.addRow(NLS.str("preferences.useKotlinMethodsForVarNames"), kotlinRenameVars)
		other.addRow(NLS.str("preferences.commentsLevel"), commentsLevel)
		other.addRow(NLS.str("preferences.integerFormat"), integerFormat)
		other.addRow(NLS.str("preferences.typeUpdatesCountLimit"), typeUpdatesLimitCount)
		return other
	}

	private fun makeOtherGroup(): SettingsGroup {
		val lineNumbersMode = JComboBox(LineNumbersMode.values())
		lineNumbersMode.selectedItem = settings.lineNumbersMode
		lineNumbersMode.addActionListener {
			settings.setLineNumbersMode(lineNumbersMode.selectedItem as LineNumbersMode)
			mainWindow.loadSettings()
		}

		val jumpOnDoubleClick = JCheckBox()
		jumpOnDoubleClick.isSelected = settings.isJumpOnDoubleClick
		jumpOnDoubleClick.addItemListener { e -> settings.setJumpOnDoubleClick(e.stateChange == ItemEvent.SELECTED) }

		val resultsPerPage = JSpinner(SpinnerNumberModel(settings.searchResultsPerPage, 0, Int.MAX_VALUE, 1))
		resultsPerPage.addChangeListener { settings.setSearchResultsPerPage(resultsPerPage.value as Int) }

		val useAltFileDialog = JCheckBox()
		useAltFileDialog.isSelected = settings.isUseAlternativeFileDialog
		useAltFileDialog.addItemListener { e -> settings.setUseAlternativeFileDialog(e.stateChange == ItemEvent.SELECTED) }

		val update = JCheckBox()
		update.isSelected = settings.isCheckForUpdates
		update.addItemListener { e -> settings.setCheckForUpdates(e.stateChange == ItemEvent.SELECTED) }

		val disableTooltipOnHover = JCheckBox()
		disableTooltipOnHover.isSelected = settings.isDisableTooltipOnHover
		disableTooltipOnHover.addItemListener { e -> settings.setDisableTooltipOnHover(e.stateChange == ItemEvent.SELECTED) }

		val cfg = JCheckBox()
		cfg.isSelected = settings.isCfgOutput
		cfg.addItemListener { e ->
			settings.setCfgOutput(e.stateChange == ItemEvent.SELECTED)
			needReload()
		}

		val rawCfg = JCheckBox()
		rawCfg.isSelected = settings.isRawCfgOutput
		rawCfg.addItemListener { e ->
			settings.setRawCfgOutput(e.stateChange == ItemEvent.SELECTED)
			needReload()
		}

		val xposedCodegenLanguage = JComboBox(XposedCodegenLanguage.values())
		xposedCodegenLanguage.selectedItem = settings.xposedCodegenLanguage
		xposedCodegenLanguage.addActionListener {
			settings.setXposedCodegenLanguage(xposedCodegenLanguage.selectedItem as XposedCodegenLanguage)
			mainWindow.loadSettings()
		}

		val updateChannel = JComboBox(KadxUpdateChannel.values())
		updateChannel.selectedItem = settings.kadxUpdateChannel
		updateChannel.addActionListener {
			settings.setKadxUpdateChannel(updateChannel.selectedItem as KadxUpdateChannel)
			mainWindow.loadSettings()
		}

		val group = SettingsGroup(NLS.str("preferences.other"))
		group.addRow(NLS.str("preferences.lineNumbersMode"), lineNumbersMode)
		group.addRow(NLS.str("preferences.jumpOnDoubleClick"), jumpOnDoubleClick)
		group.addRow(NLS.str("preferences.disable_tooltip_on_hover"), disableTooltipOnHover)
		group.addRow(NLS.str("preferences.search_results_per_page"), resultsPerPage)
		group.addRow(NLS.str("preferences.useAlternativeFileDialog"), useAltFileDialog)
		group.addRow(NLS.str("preferences.cfg"), cfg)
		group.addRow(NLS.str("preferences.raw_cfg"), rawCfg)
		group.addRow(NLS.str("preferences.xposed_codegen_language"), xposedCodegenLanguage)
		group.addRow(NLS.str("preferences.check_for_updates"), update)
		group.addRow(NLS.str("preferences.update_channel"), updateChannel)
		return group
	}

	private fun closeGroups(save: Boolean) {
		for (group in groups) {
			group.close(save)
		}
	}

	private fun save() {
		closeGroups(true)
		settings.sync()
		enableComponents(this, false)
		SwingUtilities.invokeLater {
			if (shouldReload()) {
				mainWindow.getShortcutsController().loadSettings()
				mainWindow.reopen()
			}
			if (settings.langLocale != prevLang) {
				JOptionPane.showMessageDialog(
					this,
					NLS.str("msg.language_changed", settings.langLocale),
					NLS.str("msg.language_changed_title", settings.langLocale),
					JOptionPane.INFORMATION_MESSAGE,
				)
			}
			dispose()
		}
	}

	private fun cancel() {
		closeGroups(false)
		settings.loadSettingsFromJsonString(startSettings)
		mainWindow.loadSettings()
		dispose()
	}

	private fun reset() {
		val res = JOptionPane.showConfirmDialog(
			this,
			NLS.str("preferences.reset_message"),
			NLS.str("preferences.reset_title"),
			JOptionPane.YES_NO_OPTION,
		)
		if (res == JOptionPane.YES_OPTION) {
			settings.loadSettingsData(KadxSettingsData())
			mainWindow.loadSettings()
			needReload()
			contentPane.removeAll()
			initUI()
			pack()
			repaint()
		}
	}

	private fun copySettings() {
		val settingsText = settings.exportSettingsString()
		val clipboard = Toolkit.getDefaultToolkit().systemClipboard
		val selection = StringSelection(settingsText)
		clipboard.setContents(selection, selection)
		JOptionPane.showMessageDialog(this, NLS.str("preferences.copy_message"))
	}

	fun needReload() {
		needReloadFlag = true
	}

	private fun shouldReload(): Boolean = needReloadFlag || startSettingsHash != calcSettingsHash()

	private fun calcSettingsHash(): String {
		val decompiler = mainWindow.getWrapper().currentDecompiler
		return settings.toKadxArgs().makeCodeArgsHash(decompiler)
	}

	fun getMainWindow(): MainWindow = mainWindow

	override fun dispose() {
		mainWindow.events().global().removeListener(KadxEvents.RELOAD_SETTINGS_WINDOW, reloadListener)
		settings.saveWindowPos(this)
		super.dispose()
	}

	companion object {
		private const val serialVersionUID: Long = -1804570470377354148L

		private val LOG: Logger = LoggerFactory.getLogger(KadxSettingsWindow::class.java)
	}
}

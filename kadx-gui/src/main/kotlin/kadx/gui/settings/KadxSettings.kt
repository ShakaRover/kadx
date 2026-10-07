package kadx.gui.settings

import com.google.gson.ExclusionStrategy
import com.google.gson.FieldAttributes
import com.google.gson.Gson
import kadx.api.CommentsLevel
import kadx.api.DecompilationMode
import kadx.api.KadxArgs
import kadx.api.args.GeneratedRenamesMappingFileMode
import kadx.api.args.IntegerFormat
import kadx.api.args.ResourceNameSource
import kadx.api.args.UseSourceNameAsClassNameAlias
import kadx.api.args.UserRenamesMappingsMode
import kadx.cli.config.KadxConfigAdapter
import kadx.cli.config.KadxConfigExclude
import kadx.core.utils.GsonUtils
import kadx.gui.cache.code.CodeCacheMode
import kadx.gui.cache.usage.UsageCacheMode
import kadx.gui.settings.data.SaveOptionEnum
import kadx.gui.settings.data.ShortcutsWrapper
import kadx.gui.settings.font.FontSettings
import kadx.gui.ui.MainWindow
import kadx.gui.ui.tab.dnd.TabDndGhostType
import kadx.gui.utils.LangLocale
import kadx.gui.utils.PathTypeAdapter
import kadx.gui.utils.RectangleTypeAdapter
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.awt.Font
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.awt.Window
import java.nio.file.Path
import java.util.Collections
import javax.swing.JFrame

/**
 * kadx-gui 设置的读写门面。
 *
 * **做什么**：内部持有 [KadxSettingsData]（Gson 持久化对象），向外提供大量
 * `getXxx()` / `isXxx()` / `setXxx()` 访问器；部分 setter 会额外触发落盘（[sync]）或字体更新。
 *
 * **为什么保留显式 getter/setter**：`MainWindow`、`settings.ui`、`utils` 等大量 Java 调用方
 * 以及已迁移的 Kotlin 调用方都按原方法名访问，保持 JVM 方法名不变可实现零改动。
 */
class KadxSettings(private val configAdapter: KadxConfigAdapter<KadxSettingsData>) {
	private val dataWriteSync: Any = Any()
	private val shortcutsWrapper: ShortcutsWrapper = ShortcutsWrapper()
	private val fontSettings: FontSettings = FontSettings()

	private lateinit var settingsData: KadxSettingsData
	val settingsJsonString: String get() = configAdapter.objectToJsonString(settingsData)

	fun loadSettingsFromJsonString(jsonStr: String) {
		loadSettingsData(configAdapter.jsonStringToObject(jsonStr))
	}

	fun loadSettingsData(settingsData: KadxSettingsData) {
		this.settingsData = settingsData
		upgradeSettings(settingsData.settingsVersion)
		fixOnLoad()
		// 更新自定义字段
		shortcutsWrapper.updateShortcuts(settingsData.shortcuts)
		fontSettings.bindData(settingsData)
		// Linux HiDPI：字体跟随桌面缩放（Xft.dpi / GDK_SCALE）
		fontSettings.applyDesktopScale(kadx.gui.settings.font.FontSettings.detectDesktopScale())
	}

	private fun upgradeSettings(fromVersion: Int) {
		if (settingsData.settingsVersion == KadxSettingsData.CURRENT_SETTINGS_VERSION) {
			return
		}
		LOG.debug("upgrade settings from version: {} to {}", fromVersion, KadxSettingsData.CURRENT_SETTINGS_VERSION)
		var version = fromVersion
		if (version <= 22) {
			version++
		}
		if (version != KadxSettingsData.CURRENT_SETTINGS_VERSION) {
			LOG.warn(
				"Incorrect settings upgrade. Expected version: {}, got: {}",
				KadxSettingsData.CURRENT_SETTINGS_VERSION,
				version,
			)
		}
		settingsData.settingsVersion = KadxSettingsData.CURRENT_SETTINGS_VERSION
		sync()
	}

	private fun fixOnLoad() {
		if (settingsData.threadsCount <= 0) {
			settingsData.threadsCount = KadxArgs.DEFAULT_THREADS_COUNT
		}
		if (settingsData.deobfuscationMinLength < 0) {
			settingsData.deobfuscationMinLength = 0
		}
		if (settingsData.deobfuscationMaxLength < 0) {
			settingsData.deobfuscationMaxLength = 0
		}
		// Gson 遇到未知枚举值会把非空字段置 null（反射绕过 Kotlin 空检查）。
		// 不修正的话 registerCodeCache 会拿不到有效模式，args.codeCache 会静默保留
		// KadxArgs 默认的**无界** InMemoryCodeCache。这里统一回退到 DISK。
		val codeCacheMode: CodeCacheMode? = settingsData.codeCacheMode
		if (codeCacheMode == null) {
			LOG.warn("Unknown code cache mode in settings, falling back to DISK")
			settingsData.codeCacheMode = CodeCacheMode.DISK
		}
	}

	fun sync() {
		synchronized(dataWriteSync) {
			configAdapter.save(settingsData)
		}
	}

	fun exportSettingsString(): String {
		val gson: Gson = GsonUtils.defaultGsonBuilder()
			.setExclusionStrategies(object : ExclusionStrategy {
				override fun shouldSkipField(f: FieldAttributes): Boolean = f.getAnnotation(KadxConfigExclude::class.java) != null ||
					f.getAnnotation(KadxConfigExcludeExport::class.java) != null

				override fun shouldSkipClass(clazz: Class<*>): Boolean = false
			})
			.create()
		return gson.toJson(settingsData)
	}

	fun toKadxArgs(): KadxArgs = settingsData.toKadxArgs()

	val files: List<String> get() = settingsData.files

	val cmdSelectClass: String? get() = settingsData.cmdSelectClass

	val lastOpenFilePath: Path get() = settingsData.lastOpenFilePath

	fun setLastOpenFilePath(lastOpenFilePath: Path) {
		settingsData.lastOpenFilePath = lastOpenFilePath
	}

	val lastSaveProjectPath: Path get() = settingsData.lastSaveProjectPath

	fun setLastSaveProjectPath(lastSaveProjectPath: Path) {
		settingsData.lastSaveProjectPath = lastSaveProjectPath
	}

	val lastSaveFilePath: Path get() = settingsData.lastSaveFilePath

	fun setLastSaveFilePath(lastSaveFilePath: Path) {
		settingsData.lastSaveFilePath = lastSaveFilePath
	}

	val isFlattenPackage: Boolean get() = settingsData.flattenPackage

	fun setFlattenPackage(flattenPackage: Boolean) {
		settingsData.flattenPackage = flattenPackage
	}

	val isCheckForUpdates: Boolean get() = settingsData.checkForUpdates

	fun setCheckForUpdates(checkForUpdates: Boolean) {
		settingsData.checkForUpdates = checkForUpdates
		sync()
	}

	val isDisableTooltipOnHover: Boolean get() = settingsData.disableTooltipOnHover

	fun setDisableTooltipOnHover(disableTooltipOnHover: Boolean) {
		settingsData.disableTooltipOnHover = disableTooltipOnHover
	}

	val recentProjects: List<Path> get() = Collections.unmodifiableList(settingsData.recentProjects)

	fun addRecentProject(projectPath: Path?) {
		if (projectPath == null) {
			return
		}
		val recentProjects = settingsData.recentProjects
		val normPath = projectPath.toAbsolutePath().normalize()
		recentProjects.remove(normPath)
		recentProjects.add(0, normPath)
		val count = recentProjects.size
		if (count > RECENT_PROJECTS_COUNT) {
			recentProjects.subList(RECENT_PROJECTS_COUNT, count).clear()
		}
	}

	fun removeRecentProject(projectPath: Path) {
		settingsData.recentProjects.remove(projectPath)
	}

	@Suppress("ConstantValue")
	fun saveWindowPos(window: Window?) {
		if (window == null) {
			return
		}
		synchronized(dataWriteSync) {
			val bounds = window.bounds
			if (bounds != null) {
				val pos = WindowLocation(makeWindowId(window), bounds)
				settingsData.windowPos[pos.getWindowId()] = pos
			}
		}
	}

	fun loadWindowPos(window: Window): Boolean {
		val windowId = makeWindowId(window)
		val pos = settingsData.windowPos[windowId] ?: return false
		val bounds = pos.getBounds() ?: return false
		if (!isAccessibleInAnyScreen(windowId, bounds)) {
			return false
		}
		window.bounds = bounds
		if (window is MainWindow) {
			(window as JFrame).setExtendedState(mainWindowExtendedState)
		}
		return true
	}
	val mainWindowExtendedState: Int get() = settingsData.mainWindowExtendedState

	fun setMainWindowExtendedState(mainWindowExtendedState: Int) {
		settingsData.mainWindowExtendedState = mainWindowExtendedState
	}

	val isShowHeapUsageBar: Boolean get() = settingsData.showHeapUsageBar

	fun setShowHeapUsageBar(showHeapUsageBar: Boolean) {
		settingsData.showHeapUsageBar = showHeapUsageBar
	}

	val isAlwaysSelectOpened: Boolean get() = settingsData.alwaysSelectOpened

	fun setAlwaysSelectOpened(alwaysSelectOpened: Boolean) {
		settingsData.alwaysSelectOpened = alwaysSelectOpened
	}

	val isEnablePreviewTab: Boolean get() = settingsData.enablePreviewTab

	fun setEnablePreviewTab(enablePreviewTab: Boolean) {
		settingsData.enablePreviewTab = enablePreviewTab
	}

	val isUseAlternativeFileDialog: Boolean get() = settingsData.useAlternativeFileDialog

	fun setUseAlternativeFileDialog(useAlternativeFileDialog: Boolean) {
		settingsData.useAlternativeFileDialog = useAlternativeFileDialog
	}

	val excludedPackages: String get() = settingsData.excludedPackages

	fun setExcludedPackages(excludedPackages: String) {
		settingsData.excludedPackages = excludedPackages
	}

	val langLocale: LangLocale get() = settingsData.langLocale

	fun setLangLocale(langLocale: LangLocale) {
		settingsData.langLocale = langLocale
	}

	val isAutoStartJobs: Boolean get() = settingsData.autoStartJobs

	fun setAutoStartJobs(autoStartJobs: Boolean) {
		settingsData.autoStartJobs = autoStartJobs
	}

	val shortcuts: ShortcutsWrapper get() = shortcutsWrapper

	val treeWidth: Int get() = settingsData.treeWidth

	fun setTreeWidth(treeWidth: Int) {
		settingsData.treeWidth = treeWidth
	}

	val uiZoom: Float get() = settingsData.uiZoom

	fun setUiZoom(uiZoom: Float) {
		settingsData.uiZoom = uiZoom
		fontSettings.applyUiZoom(uiZoom, isApplyUiZoomToFonts)
	}

	val isApplyUiZoomToFonts: Boolean get() = settingsData.applyUiZoomToFonts

	fun setApplyUiZoomToFonts(applyUiZoomToFonts: Boolean) {
		settingsData.applyUiZoomToFonts = applyUiZoomToFonts
		fontSettings.applyUiZoom(uiZoom, applyUiZoomToFonts)
	}

	fun getFontSettings(): FontSettings = fontSettings

	val uiFont: Font get() = fontSettings.getUiFontAdapter().getEffectiveFont()

	fun setUiFont(font: Font) {
		fontSettings.getUiFontAdapter().setFont(font)
	}

	val codeFont: Font get() = fontSettings.getCodeFontAdapter().getEffectiveFont()

	fun setCodeFont(font: Font) {
		fontSettings.getCodeFontAdapter().setFont(font)
	}

	val smaliFont: Font get() = fontSettings.getSmaliFontAdapter().getEffectiveFont()

	fun setSmaliFont(font: Font) {
		fontSettings.getSmaliFontAdapter().setFont(font)
	}

	val editorTheme: String get() = settingsData.editorTheme

	fun setEditorTheme(editorTheme: String) {
		settingsData.editorTheme = editorTheme
	}

	val lafTheme: String get() = settingsData.lafTheme

	fun setLafTheme(lafTheme: String) {
		settingsData.lafTheme = lafTheme
	}

	val isCodeAreaLineWrap: Boolean get() = settingsData.codeAreaLineWrap

	fun setCodeAreaLineWrap(lineWrap: Boolean) {
		settingsData.codeAreaLineWrap = lineWrap
	}

	val searchResultsPerPage: Int get() = settingsData.searchResultsPerPage

	fun setSearchResultsPerPage(searchResultsPerPage: Int) {
		settingsData.searchResultsPerPage = searchResultsPerPage
	}

	val isUseAutoSearch: Boolean get() = settingsData.useAutoSearch

	fun saveUseAutoSearch(useAutoSearch: Boolean) {
		settingsData.useAutoSearch = useAutoSearch
		sync()
	}

	fun saveKeepCommonDialogOpen(keepCommonDialogOpen: Boolean) {
		settingsData.keepCommonDialogOpen = keepCommonDialogOpen
		sync()
	}

	val isKeepCommonDialogOpen: Boolean get() = settingsData.keepCommonDialogOpen
	val mainWindowVerticalSplitterLoc: Int get() = settingsData.mainWindowVerticalSplitterLoc

	fun setMainWindowVerticalSplitterLoc(location: Int) {
		settingsData.mainWindowVerticalSplitterLoc = location
	}

	val debuggerStackFrameSplitterLoc: Int get() = settingsData.debuggerStackFrameSplitterLoc

	fun setDebuggerStackFrameSplitterLoc(location: Int) {
		settingsData.debuggerStackFrameSplitterLoc = location
	}

	val debuggerVarTreeSplitterLoc: Int get() = settingsData.debuggerVarTreeSplitterLoc

	fun setDebuggerVarTreeSplitterLoc(location: Int) {
		settingsData.debuggerVarTreeSplitterLoc = location
	}

	val adbDialogHost: String get() = settingsData.adbDialogHost

	fun setAdbDialogHost(adbDialogHost: String) {
		settingsData.adbDialogHost = adbDialogHost
	}

	val adbDialogPath: String get() = settingsData.adbDialogPath

	fun setAdbDialogPath(adbDialogPath: String) {
		settingsData.adbDialogPath = adbDialogPath
	}

	val adbDialogPort: String get() = settingsData.adbDialogPort

	fun setAdbDialogPort(adbDialogPort: String) {
		settingsData.adbDialogPort = adbDialogPort
	}

	val commentsLevel: CommentsLevel get() = settingsData.commentsLevel

	fun setCommentsLevel(level: CommentsLevel) {
		settingsData.commentsLevel = level
	}

	val typeUpdatesLimitCount: Int get() = settingsData.typeUpdatesLimitCount

	fun setTypeUpdatesLimitCount(typeUpdatesLimitCount: Int) {
		settingsData.typeUpdatesLimitCount = typeUpdatesLimitCount
	}

	val lineNumbersMode: LineNumbersMode get() = settingsData.lineNumbersMode

	fun setLineNumbersMode(lineNumbersMode: LineNumbersMode) {
		settingsData.lineNumbersMode = lineNumbersMode
	}

	val codeCacheMode: CodeCacheMode get() = settingsData.codeCacheMode

	fun setCodeCacheMode(codeCacheMode: CodeCacheMode) {
		settingsData.codeCacheMode = codeCacheMode
	}

	val usageCacheMode: UsageCacheMode get() = settingsData.usageCacheMode

	fun setUsageCacheMode(usageCacheMode: UsageCacheMode) {
		settingsData.usageCacheMode = usageCacheMode
	}

	val cacheDir: String? get() = settingsData.cacheDir

	fun setCacheDir(cacheDir: String?) {
		settingsData.cacheDir = cacheDir
	}

	val isJumpOnDoubleClick: Boolean get() = settingsData.jumpOnDoubleClick

	fun setJumpOnDoubleClick(jumpOnDoubleClick: Boolean) {
		settingsData.jumpOnDoubleClick = jumpOnDoubleClick
	}

	val isDockLogViewer: Boolean get() = settingsData.dockLogViewer

	fun saveDockLogViewer(dockLogViewer: Boolean) {
		settingsData.dockLogViewer = dockLogViewer
		sync()
	}

	val isDockQuickTabs: Boolean get() = settingsData.dockQuickTabs

	fun saveDockQuickTabs(dockQuickTabs: Boolean) {
		settingsData.dockQuickTabs = dockQuickTabs
		sync()
	}
	val xposedCodegenLanguage: XposedCodegenLanguage get() = settingsData.xposedCodegenLanguage

	fun setXposedCodegenLanguage(language: XposedCodegenLanguage) {
		settingsData.xposedCodegenLanguage = language
	}

	val kadxUpdateChannel: KadxUpdateChannel get() = settingsData.kadxUpdateChannel

	fun setKadxUpdateChannel(channel: KadxUpdateChannel) {
		settingsData.kadxUpdateChannel = channel
	}

	val tabDndGhostType: TabDndGhostType get() = settingsData.tabDndGhostType

	fun setTabDndGhostType(tabDndGhostType: TabDndGhostType) {
		settingsData.tabDndGhostType = tabDndGhostType
	}

	val isRestoreSwitchOverString: Boolean get() = settingsData.restoreSwitchOverString

	fun setRestoreSwitchOverString(restoreSwitchOverString: Boolean) {
		settingsData.restoreSwitchOverString = restoreSwitchOverString
	}

	val isRenamePrintable: Boolean get() = settingsData.isRenamePrintable

	val userRenamesMappingsMode: UserRenamesMappingsMode get() = settingsData.userRenamesMappingsMode

	fun setUserRenamesMappingsMode(userRenamesMappingsMode: UserRenamesMappingsMode) {
		settingsData.userRenamesMappingsMode = userRenamesMappingsMode
	}

	val isInlineAnonymousClasses: Boolean get() = settingsData.inlineAnonymousClasses

	fun setInlineAnonymousClasses(inlineAnonymousClasses: Boolean) {
		settingsData.inlineAnonymousClasses = inlineAnonymousClasses
	}

	val isRespectBytecodeAccessModifiers: Boolean get() = settingsData.respectBytecodeAccessModifiers

	fun setRespectBytecodeAccessModifiers(respectBytecodeAccessModifiers: Boolean) {
		settingsData.respectBytecodeAccessModifiers = respectBytecodeAccessModifiers
	}

	val isRenameCaseSensitive: Boolean get() = settingsData.isRenameCaseSensitive

	val decompilationMode: DecompilationMode get() = settingsData.decompilationMode

	fun setDecompilationMode(decompilationMode: DecompilationMode) {
		settingsData.decompilationMode = decompilationMode
	}

	val isInlineMethods: Boolean get() = settingsData.inlineMethods

	fun setInlineMethods(inlineMethods: Boolean) {
		settingsData.inlineMethods = inlineMethods
	}

	val isFsCaseSensitive: Boolean get() = settingsData.fsCaseSensitive

	fun setFsCaseSensitive(fsCaseSensitive: Boolean) {
		settingsData.fsCaseSensitive = fsCaseSensitive
	}

	val isExtractFinally: Boolean get() = settingsData.extractFinally

	fun setExtractFinally(extractFinally: Boolean) {
		settingsData.extractFinally = extractFinally
	}

	val sourceNameRepeatLimit: Int get() = settingsData.sourceNameRepeatLimit

	fun setSourceNameRepeatLimit(sourceNameRepeatLimit: Int) {
		settingsData.sourceNameRepeatLimit = sourceNameRepeatLimit
	}

	val isRenameValid: Boolean get() = settingsData.isRenameValid

	val isSkipXmlPrettyPrint: Boolean get() = settingsData.skipXmlPrettyPrint

	fun setSkipXmlPrettyPrint(skipXmlPrettyPrint: Boolean) {
		settingsData.skipXmlPrettyPrint = skipXmlPrettyPrint
	}

	val useSourceNameAsClassNameAlias: UseSourceNameAsClassNameAlias get() = settingsData.getUseSourceNameAsClassNameAlias()

	fun setUseSourceNameAsClassNameAlias(useSourceNameAsClassNameAlias: UseSourceNameAsClassNameAlias) {
		settingsData.setUseSourceNameAsClassNameAlias(useSourceNameAsClassNameAlias)
	}

	val isShowInconsistentCode: Boolean get() = settingsData.showInconsistentCode

	fun setShowInconsistentCode(showInconsistentCode: Boolean) {
		settingsData.showInconsistentCode = showInconsistentCode
	}

	val isCfgOutput: Boolean get() = settingsData.cfgOutput

	fun setCfgOutput(cfgOutput: Boolean) {
		settingsData.cfgOutput = cfgOutput
	}

	val isEscapeUnicode: Boolean get() = settingsData.escapeUnicode

	fun setEscapeUnicode(escapeUnicode: Boolean) {
		settingsData.escapeUnicode = escapeUnicode
	}

	val useKotlinMethodsForVarNames: KadxArgs.UseKotlinMethodsForVarNames get() = settingsData.useKotlinMethodsForVarNames

	fun setUseKotlinMethodsForVarNames(useKotlinMethodsForVarNames: KadxArgs.UseKotlinMethodsForVarNames) {
		settingsData.useKotlinMethodsForVarNames = useKotlinMethodsForVarNames
	}
	val deobfuscationWhitelistStr: String get() = settingsData.deobfuscationWhitelistStr

	fun setDeobfuscationWhitelistStr(deobfuscationWhitelistStr: String) {
		settingsData.deobfuscationWhitelistStr = deobfuscationWhitelistStr
	}

	val generatedRenamesMappingFile: String? get() = settingsData.generatedRenamesMappingFile

	val isRawCfgOutput: Boolean get() = settingsData.rawCfgOutput

	fun setRawCfgOutput(rawCfgOutput: Boolean) {
		settingsData.rawCfgOutput = rawCfgOutput
	}

	val isMoveInnerClasses: Boolean get() = settingsData.moveInnerClasses

	fun setMoveInnerClasses(moveInnerClasses: Boolean) {
		settingsData.moveInnerClasses = moveInnerClasses
	}

	val isUseDx: Boolean get() = settingsData.useDx

	fun setUseDx(useDx: Boolean) {
		settingsData.useDx = useDx
	}

	val isAddDebugLines: Boolean get() = settingsData.addDebugLines

	val isUseHeadersForDetectResourceExtensions: Boolean get() = settingsData.useHeadersForDetectResourceExtensions

	fun setUseHeadersForDetectResourceExtensions(useHeadersForDetectResourceExtensions: Boolean) {
		settingsData.useHeadersForDetectResourceExtensions = useHeadersForDetectResourceExtensions
	}

	val pluginOptions: Map<String, String> get() = settingsData.pluginOptions

	val isDeobfuscationOn: Boolean get() = settingsData.deobfuscationOn

	fun setDeobfuscationOn(deobfuscationOn: Boolean) {
		settingsData.deobfuscationOn = deobfuscationOn
	}

	val isReplaceConsts: Boolean get() = settingsData.replaceConsts

	fun setReplaceConsts(replaceConsts: Boolean) {
		settingsData.replaceConsts = replaceConsts
	}

	val isAllowInlineKotlinLambda: Boolean get() = settingsData.allowInlineKotlinLambda

	fun setAllowInlineKotlinLambda(allowInlineKotlinLambda: Boolean) {
		settingsData.allowInlineKotlinLambda = allowInlineKotlinLambda
	}

	@Suppress("DEPRECATION")
	fun setDeobfuscationUseSourceNameAsAlias(deobfuscationUseSourceNameAsAlias: Boolean) {
		settingsData.deobfuscationUseSourceNameAsAlias = deobfuscationUseSourceNameAsAlias
	}

	fun setRenameFlags(renameFlags: MutableSet<KadxArgs.RenameEnum>) {
		settingsData.renameFlags = renameFlags
	}

	fun updateRenameFlag(flag: KadxArgs.RenameEnum, enabled: Boolean) {
		if (enabled) {
			settingsData.renameFlags.add(flag)
		} else {
			settingsData.renameFlags.remove(flag)
		}
	}

	fun setUserRenamesMappingsPath(userRenamesMappingsPath: Path) {
		settingsData.userRenamesMappingsPath = userRenamesMappingsPath
	}

	val isSkipSources: Boolean get() = settingsData.skipSources

	val isDebugInfo: Boolean get() = settingsData.debugInfo

	fun setDebugInfo(debugInfo: Boolean) {
		settingsData.debugInfo = debugInfo
	}

	val isSkipResources: Boolean get() = settingsData.skipResources

	fun setSkipResources(skipResources: Boolean) {
		settingsData.skipResources = skipResources
	}

	val resourceNameSource: ResourceNameSource get() = settingsData.resourceNameSource

	fun setResourceNameSource(resourceNameSource: ResourceNameSource) {
		settingsData.resourceNameSource = resourceNameSource
	}

	val integerFormat: IntegerFormat get() = settingsData.integerFormat

	fun setIntegerFormat(format: IntegerFormat) {
		settingsData.integerFormat = format
	}

	val isFallbackMode: Boolean get() = settingsData.fallbackMode

	val isUseImports: Boolean get() = settingsData.useImports

	fun setUseImports(useImports: Boolean) {
		settingsData.useImports = useImports
	}

	val deobfuscationMinLength: Int get() = settingsData.deobfuscationMinLength

	fun setDeobfuscationMinLength(deobfuscationMinLength: Int) {
		settingsData.deobfuscationMinLength = deobfuscationMinLength
	}

	val generatedRenamesMappingFileMode: GeneratedRenamesMappingFileMode get() = settingsData.generatedRenamesMappingFileMode

	fun setGeneratedRenamesMappingFileMode(generatedRenamesMappingFileMode: GeneratedRenamesMappingFileMode) {
		settingsData.generatedRenamesMappingFileMode = generatedRenamesMappingFileMode
	}

	val deobfuscationMaxLength: Int get() = settingsData.deobfuscationMaxLength

	fun setDeobfuscationMaxLength(deobfuscationMaxLength: Int) {
		settingsData.deobfuscationMaxLength = deobfuscationMaxLength
	}

	val threadsCount: Int get() = settingsData.threadsCount

	fun setThreadsCount(threadsCount: Int) {
		settingsData.threadsCount = threadsCount
	}

	val saveOption: SaveOptionEnum get() = settingsData.saveOption

	fun setSaveOption(saveOption: SaveOptionEnum) {
		settingsData.saveOption = saveOption
	}
	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(KadxSettings::class.java)

		private const val RECENT_PROJECTS_COUNT: Int = 30

		fun buildConfigAdapter(): KadxConfigAdapter<KadxSettingsData> = KadxConfigAdapter(KadxSettingsData::class.java, "gui") { gsonBuilder ->
			gsonBuilder.registerTypeHierarchyAdapter(Path::class.java, PathTypeAdapter.singleton())
			gsonBuilder.registerTypeHierarchyAdapter(Rectangle::class.java, RectangleTypeAdapter.singleton())
		}

		private fun makeWindowId(window: Window): String = window.javaClass.simpleName

		private fun isAccessibleInAnyScreen(windowId: String, windowBounds: Rectangle): Boolean {
			for (gd in GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices) {
				val screenBounds: Rectangle = gd.defaultConfiguration.bounds
				if (screenBounds.intersects(windowBounds)) {
					return true
				}
			}
			LOG.debug("Window saved position was ignored: {}, bounds: {}", windowId, windowBounds)
			return false
		}
	}
}

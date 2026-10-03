package jadx.gui.settings

import com.google.gson.ExclusionStrategy
import com.google.gson.FieldAttributes
import com.google.gson.Gson
import jadx.api.CommentsLevel
import jadx.api.DecompilationMode
import jadx.api.JadxArgs
import jadx.api.args.GeneratedRenamesMappingFileMode
import jadx.api.args.IntegerFormat
import jadx.api.args.ResourceNameSource
import jadx.api.args.UseSourceNameAsClassNameAlias
import jadx.api.args.UserRenamesMappingsMode
import jadx.cli.config.JadxConfigAdapter
import jadx.cli.config.JadxConfigExclude
import jadx.core.utils.GsonUtils
import jadx.gui.cache.code.CodeCacheMode
import jadx.gui.cache.usage.UsageCacheMode
import jadx.gui.settings.data.SaveOptionEnum
import jadx.gui.settings.data.ShortcutsWrapper
import jadx.gui.settings.font.FontSettings
import jadx.gui.ui.MainWindow
import jadx.gui.ui.tab.dnd.TabDndGhostType
import jadx.gui.utils.LangLocale
import jadx.gui.utils.PathTypeAdapter
import jadx.gui.utils.RectangleTypeAdapter
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
 * jadx-gui 设置的读写门面。
 *
 * **做什么**：内部持有 [JadxSettingsData]（Gson 持久化对象），向外提供大量
 * `getXxx()` / `isXxx()` / `setXxx()` 访问器；部分 setter 会额外触发落盘（[sync]）或字体更新。
 *
 * **为什么保留显式 getter/setter**：`MainWindow`、`settings.ui`、`utils` 等大量 Java 调用方
 * 以及已迁移的 Kotlin 调用方都按原方法名访问，保持 JVM 方法名不变可实现零改动。
 */
class JadxSettings(private val configAdapter: JadxConfigAdapter<JadxSettingsData>) {
	private val dataWriteSync: Any = Any()
	private val shortcutsWrapper: ShortcutsWrapper = ShortcutsWrapper()
	private val fontSettings: FontSettings = FontSettings()

	private lateinit var settingsData: JadxSettingsData
	fun getSettingsJsonString(): String = configAdapter.objectToJsonString(settingsData)

	fun loadSettingsFromJsonString(jsonStr: String) {
		loadSettingsData(configAdapter.jsonStringToObject(jsonStr))
	}

	fun loadSettingsData(settingsData: JadxSettingsData) {
		this.settingsData = settingsData
		upgradeSettings(settingsData.settingsVersion)
		fixOnLoad()
		// 更新自定义字段
		shortcutsWrapper.updateShortcuts(settingsData.shortcuts)
		fontSettings.bindData(settingsData)
	}

	private fun upgradeSettings(fromVersion: Int) {
		if (settingsData.settingsVersion == JadxSettingsData.CURRENT_SETTINGS_VERSION) {
			return
		}
		LOG.debug("upgrade settings from version: {} to {}", fromVersion, JadxSettingsData.CURRENT_SETTINGS_VERSION)
		var version = fromVersion
		if (version <= 22) {
			version++
		}
		if (version != JadxSettingsData.CURRENT_SETTINGS_VERSION) {
			LOG.warn(
				"Incorrect settings upgrade. Expected version: {}, got: {}",
				JadxSettingsData.CURRENT_SETTINGS_VERSION,
				version,
			)
		}
		settingsData.settingsVersion = JadxSettingsData.CURRENT_SETTINGS_VERSION
		sync()
	}

	private fun fixOnLoad() {
		if (settingsData.threadsCount <= 0) {
			settingsData.threadsCount = JadxArgs.DEFAULT_THREADS_COUNT
		}
		if (settingsData.deobfuscationMinLength < 0) {
			settingsData.deobfuscationMinLength = 0
		}
		if (settingsData.deobfuscationMaxLength < 0) {
			settingsData.deobfuscationMaxLength = 0
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
				override fun shouldSkipField(f: FieldAttributes): Boolean = f.getAnnotation(JadxConfigExclude::class.java) != null ||
					f.getAnnotation(JadxConfigExcludeExport::class.java) != null

				override fun shouldSkipClass(clazz: Class<*>): Boolean = false
			})
			.create()
		return gson.toJson(settingsData)
	}

	fun toJadxArgs(): JadxArgs = settingsData.toJadxArgs()

	fun getFiles(): List<String> = settingsData.files

	fun getCmdSelectClass(): String? = settingsData.cmdSelectClass

	fun getLastOpenFilePath(): Path = settingsData.lastOpenFilePath

	fun setLastOpenFilePath(lastOpenFilePath: Path) {
		settingsData.lastOpenFilePath = lastOpenFilePath
	}

	fun getLastSaveProjectPath(): Path = settingsData.lastSaveProjectPath

	fun setLastSaveProjectPath(lastSaveProjectPath: Path) {
		settingsData.lastSaveProjectPath = lastSaveProjectPath
	}

	fun getLastSaveFilePath(): Path = settingsData.lastSaveFilePath

	fun setLastSaveFilePath(lastSaveFilePath: Path) {
		settingsData.lastSaveFilePath = lastSaveFilePath
	}

	fun isFlattenPackage(): Boolean = settingsData.flattenPackage

	fun setFlattenPackage(flattenPackage: Boolean) {
		settingsData.flattenPackage = flattenPackage
	}

	fun isCheckForUpdates(): Boolean = settingsData.checkForUpdates

	fun setCheckForUpdates(checkForUpdates: Boolean) {
		settingsData.checkForUpdates = checkForUpdates
		sync()
	}

	fun isDisableTooltipOnHover(): Boolean = settingsData.disableTooltipOnHover

	fun setDisableTooltipOnHover(disableTooltipOnHover: Boolean) {
		settingsData.disableTooltipOnHover = disableTooltipOnHover
	}

	fun getRecentProjects(): List<Path> = Collections.unmodifiableList(settingsData.recentProjects)

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
			(window as JFrame).setExtendedState(getMainWindowExtendedState())
		}
		return true
	}
	fun getMainWindowExtendedState(): Int = settingsData.mainWindowExtendedState

	fun setMainWindowExtendedState(mainWindowExtendedState: Int) {
		settingsData.mainWindowExtendedState = mainWindowExtendedState
	}

	fun isShowHeapUsageBar(): Boolean = settingsData.showHeapUsageBar

	fun setShowHeapUsageBar(showHeapUsageBar: Boolean) {
		settingsData.showHeapUsageBar = showHeapUsageBar
	}

	fun isAlwaysSelectOpened(): Boolean = settingsData.alwaysSelectOpened

	fun setAlwaysSelectOpened(alwaysSelectOpened: Boolean) {
		settingsData.alwaysSelectOpened = alwaysSelectOpened
	}

	fun isEnablePreviewTab(): Boolean = settingsData.enablePreviewTab

	fun setEnablePreviewTab(enablePreviewTab: Boolean) {
		settingsData.enablePreviewTab = enablePreviewTab
	}

	fun isUseAlternativeFileDialog(): Boolean = settingsData.useAlternativeFileDialog

	fun setUseAlternativeFileDialog(useAlternativeFileDialog: Boolean) {
		settingsData.useAlternativeFileDialog = useAlternativeFileDialog
	}

	fun getExcludedPackages(): String = settingsData.excludedPackages

	fun setExcludedPackages(excludedPackages: String) {
		settingsData.excludedPackages = excludedPackages
	}

	fun getLangLocale(): LangLocale = settingsData.langLocale

	fun setLangLocale(langLocale: LangLocale) {
		settingsData.langLocale = langLocale
	}

	fun isAutoStartJobs(): Boolean = settingsData.autoStartJobs

	fun setAutoStartJobs(autoStartJobs: Boolean) {
		settingsData.autoStartJobs = autoStartJobs
	}

	fun getShortcuts(): ShortcutsWrapper = shortcutsWrapper

	fun getTreeWidth(): Int = settingsData.treeWidth

	fun setTreeWidth(treeWidth: Int) {
		settingsData.treeWidth = treeWidth
	}

	fun getUiZoom(): Float = settingsData.uiZoom

	fun setUiZoom(uiZoom: Float) {
		settingsData.uiZoom = uiZoom
		fontSettings.applyUiZoom(uiZoom, isApplyUiZoomToFonts())
	}

	fun isApplyUiZoomToFonts(): Boolean = settingsData.applyUiZoomToFonts

	fun setApplyUiZoomToFonts(applyUiZoomToFonts: Boolean) {
		settingsData.applyUiZoomToFonts = applyUiZoomToFonts
		fontSettings.applyUiZoom(getUiZoom(), applyUiZoomToFonts)
	}

	fun getFontSettings(): FontSettings = fontSettings

	fun getUiFont(): Font = fontSettings.getUiFontAdapter().getEffectiveFont()

	fun setUiFont(font: Font) {
		fontSettings.getUiFontAdapter().setFont(font)
	}

	fun getCodeFont(): Font = fontSettings.getCodeFontAdapter().getEffectiveFont()

	fun setCodeFont(font: Font) {
		fontSettings.getCodeFontAdapter().setFont(font)
	}

	fun getSmaliFont(): Font = fontSettings.getSmaliFontAdapter().getEffectiveFont()

	fun setSmaliFont(font: Font) {
		fontSettings.getSmaliFontAdapter().setFont(font)
	}

	fun getEditorTheme(): String = settingsData.editorTheme

	fun setEditorTheme(editorTheme: String) {
		settingsData.editorTheme = editorTheme
	}

	fun getLafTheme(): String = settingsData.lafTheme

	fun setLafTheme(lafTheme: String) {
		settingsData.lafTheme = lafTheme
	}

	fun isCodeAreaLineWrap(): Boolean = settingsData.codeAreaLineWrap

	fun setCodeAreaLineWrap(lineWrap: Boolean) {
		settingsData.codeAreaLineWrap = lineWrap
	}

	fun getSearchResultsPerPage(): Int = settingsData.searchResultsPerPage

	fun setSearchResultsPerPage(searchResultsPerPage: Int) {
		settingsData.searchResultsPerPage = searchResultsPerPage
	}

	fun isUseAutoSearch(): Boolean = settingsData.useAutoSearch

	fun saveUseAutoSearch(useAutoSearch: Boolean) {
		settingsData.useAutoSearch = useAutoSearch
		sync()
	}

	fun saveKeepCommonDialogOpen(keepCommonDialogOpen: Boolean) {
		settingsData.keepCommonDialogOpen = keepCommonDialogOpen
		sync()
	}

	fun isKeepCommonDialogOpen(): Boolean = settingsData.keepCommonDialogOpen
	fun getMainWindowVerticalSplitterLoc(): Int = settingsData.mainWindowVerticalSplitterLoc

	fun setMainWindowVerticalSplitterLoc(location: Int) {
		settingsData.mainWindowVerticalSplitterLoc = location
	}

	fun getDebuggerStackFrameSplitterLoc(): Int = settingsData.debuggerStackFrameSplitterLoc

	fun setDebuggerStackFrameSplitterLoc(location: Int) {
		settingsData.debuggerStackFrameSplitterLoc = location
	}

	fun getDebuggerVarTreeSplitterLoc(): Int = settingsData.debuggerVarTreeSplitterLoc

	fun setDebuggerVarTreeSplitterLoc(location: Int) {
		settingsData.debuggerVarTreeSplitterLoc = location
	}

	fun getAdbDialogHost(): String = settingsData.adbDialogHost

	fun setAdbDialogHost(adbDialogHost: String) {
		settingsData.adbDialogHost = adbDialogHost
	}

	fun getAdbDialogPath(): String = settingsData.adbDialogPath

	fun setAdbDialogPath(adbDialogPath: String) {
		settingsData.adbDialogPath = adbDialogPath
	}

	fun getAdbDialogPort(): String = settingsData.adbDialogPort

	fun setAdbDialogPort(adbDialogPort: String) {
		settingsData.adbDialogPort = adbDialogPort
	}

	fun getCommentsLevel(): CommentsLevel = settingsData.commentsLevel

	fun setCommentsLevel(level: CommentsLevel) {
		settingsData.commentsLevel = level
	}

	fun getTypeUpdatesLimitCount(): Int = settingsData.typeUpdatesLimitCount

	fun setTypeUpdatesLimitCount(typeUpdatesLimitCount: Int) {
		settingsData.typeUpdatesLimitCount = typeUpdatesLimitCount
	}

	fun getLineNumbersMode(): LineNumbersMode = settingsData.lineNumbersMode

	fun setLineNumbersMode(lineNumbersMode: LineNumbersMode) {
		settingsData.lineNumbersMode = lineNumbersMode
	}

	fun getCodeCacheMode(): CodeCacheMode = settingsData.codeCacheMode

	fun setCodeCacheMode(codeCacheMode: CodeCacheMode) {
		settingsData.codeCacheMode = codeCacheMode
	}

	fun getUsageCacheMode(): UsageCacheMode = settingsData.usageCacheMode

	fun setUsageCacheMode(usageCacheMode: UsageCacheMode) {
		settingsData.usageCacheMode = usageCacheMode
	}

	fun getCacheDir(): String? = settingsData.cacheDir

	fun setCacheDir(cacheDir: String?) {
		settingsData.cacheDir = cacheDir
	}

	fun isJumpOnDoubleClick(): Boolean = settingsData.jumpOnDoubleClick

	fun setJumpOnDoubleClick(jumpOnDoubleClick: Boolean) {
		settingsData.jumpOnDoubleClick = jumpOnDoubleClick
	}

	fun isDockLogViewer(): Boolean = settingsData.dockLogViewer

	fun saveDockLogViewer(dockLogViewer: Boolean) {
		settingsData.dockLogViewer = dockLogViewer
		sync()
	}

	fun isDockQuickTabs(): Boolean = settingsData.dockQuickTabs

	fun saveDockQuickTabs(dockQuickTabs: Boolean) {
		settingsData.dockQuickTabs = dockQuickTabs
		sync()
	}
	fun getXposedCodegenLanguage(): XposedCodegenLanguage = settingsData.xposedCodegenLanguage

	fun setXposedCodegenLanguage(language: XposedCodegenLanguage) {
		settingsData.xposedCodegenLanguage = language
	}

	fun getJadxUpdateChannel(): JadxUpdateChannel = settingsData.jadxUpdateChannel

	fun setJadxUpdateChannel(channel: JadxUpdateChannel) {
		settingsData.jadxUpdateChannel = channel
	}

	fun getTabDndGhostType(): TabDndGhostType = settingsData.tabDndGhostType

	fun setTabDndGhostType(tabDndGhostType: TabDndGhostType) {
		settingsData.tabDndGhostType = tabDndGhostType
	}

	fun isRestoreSwitchOverString(): Boolean = settingsData.restoreSwitchOverString

	fun setRestoreSwitchOverString(restoreSwitchOverString: Boolean) {
		settingsData.restoreSwitchOverString = restoreSwitchOverString
	}

	fun isRenamePrintable(): Boolean = settingsData.isRenamePrintable

	fun getUserRenamesMappingsMode(): UserRenamesMappingsMode = settingsData.userRenamesMappingsMode

	fun setUserRenamesMappingsMode(userRenamesMappingsMode: UserRenamesMappingsMode) {
		settingsData.userRenamesMappingsMode = userRenamesMappingsMode
	}

	fun isInlineAnonymousClasses(): Boolean = settingsData.inlineAnonymousClasses

	fun setInlineAnonymousClasses(inlineAnonymousClasses: Boolean) {
		settingsData.inlineAnonymousClasses = inlineAnonymousClasses
	}

	fun isRespectBytecodeAccessModifiers(): Boolean = settingsData.respectBytecodeAccessModifiers

	fun setRespectBytecodeAccessModifiers(respectBytecodeAccessModifiers: Boolean) {
		settingsData.respectBytecodeAccessModifiers = respectBytecodeAccessModifiers
	}

	fun isRenameCaseSensitive(): Boolean = settingsData.isRenameCaseSensitive

	fun getDecompilationMode(): DecompilationMode = settingsData.decompilationMode

	fun setDecompilationMode(decompilationMode: DecompilationMode) {
		settingsData.decompilationMode = decompilationMode
	}

	fun isInlineMethods(): Boolean = settingsData.inlineMethods

	fun setInlineMethods(inlineMethods: Boolean) {
		settingsData.inlineMethods = inlineMethods
	}

	fun isFsCaseSensitive(): Boolean = settingsData.fsCaseSensitive

	fun setFsCaseSensitive(fsCaseSensitive: Boolean) {
		settingsData.fsCaseSensitive = fsCaseSensitive
	}

	fun isExtractFinally(): Boolean = settingsData.extractFinally

	fun setExtractFinally(extractFinally: Boolean) {
		settingsData.extractFinally = extractFinally
	}

	fun getSourceNameRepeatLimit(): Int = settingsData.sourceNameRepeatLimit

	fun setSourceNameRepeatLimit(sourceNameRepeatLimit: Int) {
		settingsData.sourceNameRepeatLimit = sourceNameRepeatLimit
	}

	fun isRenameValid(): Boolean = settingsData.isRenameValid

	fun isSkipXmlPrettyPrint(): Boolean = settingsData.skipXmlPrettyPrint

	fun setSkipXmlPrettyPrint(skipXmlPrettyPrint: Boolean) {
		settingsData.skipXmlPrettyPrint = skipXmlPrettyPrint
	}

	fun getUseSourceNameAsClassNameAlias(): UseSourceNameAsClassNameAlias = settingsData.getUseSourceNameAsClassNameAlias()

	fun setUseSourceNameAsClassNameAlias(useSourceNameAsClassNameAlias: UseSourceNameAsClassNameAlias) {
		settingsData.setUseSourceNameAsClassNameAlias(useSourceNameAsClassNameAlias)
	}

	fun isShowInconsistentCode(): Boolean = settingsData.showInconsistentCode

	fun setShowInconsistentCode(showInconsistentCode: Boolean) {
		settingsData.showInconsistentCode = showInconsistentCode
	}

	fun isCfgOutput(): Boolean = settingsData.cfgOutput

	fun setCfgOutput(cfgOutput: Boolean) {
		settingsData.cfgOutput = cfgOutput
	}

	fun isEscapeUnicode(): Boolean = settingsData.escapeUnicode

	fun setEscapeUnicode(escapeUnicode: Boolean) {
		settingsData.escapeUnicode = escapeUnicode
	}

	fun getUseKotlinMethodsForVarNames(): JadxArgs.UseKotlinMethodsForVarNames = settingsData.useKotlinMethodsForVarNames

	fun setUseKotlinMethodsForVarNames(useKotlinMethodsForVarNames: JadxArgs.UseKotlinMethodsForVarNames) {
		settingsData.useKotlinMethodsForVarNames = useKotlinMethodsForVarNames
	}
	fun getDeobfuscationWhitelistStr(): String = settingsData.deobfuscationWhitelistStr

	fun setDeobfuscationWhitelistStr(deobfuscationWhitelistStr: String) {
		settingsData.deobfuscationWhitelistStr = deobfuscationWhitelistStr
	}

	fun getGeneratedRenamesMappingFile(): String? = settingsData.generatedRenamesMappingFile

	fun isRawCfgOutput(): Boolean = settingsData.rawCfgOutput

	fun setRawCfgOutput(rawCfgOutput: Boolean) {
		settingsData.rawCfgOutput = rawCfgOutput
	}

	fun isMoveInnerClasses(): Boolean = settingsData.moveInnerClasses

	fun setMoveInnerClasses(moveInnerClasses: Boolean) {
		settingsData.moveInnerClasses = moveInnerClasses
	}

	fun isUseDx(): Boolean = settingsData.useDx

	fun setUseDx(useDx: Boolean) {
		settingsData.useDx = useDx
	}

	fun isAddDebugLines(): Boolean = settingsData.addDebugLines

	fun isUseHeadersForDetectResourceExtensions(): Boolean = settingsData.useHeadersForDetectResourceExtensions

	fun setUseHeadersForDetectResourceExtensions(useHeadersForDetectResourceExtensions: Boolean) {
		settingsData.useHeadersForDetectResourceExtensions = useHeadersForDetectResourceExtensions
	}

	fun getPluginOptions(): Map<String, String> = settingsData.pluginOptions

	fun isDeobfuscationOn(): Boolean = settingsData.deobfuscationOn

	fun setDeobfuscationOn(deobfuscationOn: Boolean) {
		settingsData.deobfuscationOn = deobfuscationOn
	}

	fun isReplaceConsts(): Boolean = settingsData.replaceConsts

	fun setReplaceConsts(replaceConsts: Boolean) {
		settingsData.replaceConsts = replaceConsts
	}

	fun isAllowInlineKotlinLambda(): Boolean = settingsData.allowInlineKotlinLambda

	fun setAllowInlineKotlinLambda(allowInlineKotlinLambda: Boolean) {
		settingsData.allowInlineKotlinLambda = allowInlineKotlinLambda
	}

	@Suppress("DEPRECATION")
	fun setDeobfuscationUseSourceNameAsAlias(deobfuscationUseSourceNameAsAlias: Boolean) {
		settingsData.deobfuscationUseSourceNameAsAlias = deobfuscationUseSourceNameAsAlias
	}

	fun setRenameFlags(renameFlags: MutableSet<JadxArgs.RenameEnum>) {
		settingsData.renameFlags = renameFlags
	}

	fun updateRenameFlag(flag: JadxArgs.RenameEnum, enabled: Boolean) {
		if (enabled) {
			settingsData.renameFlags.add(flag)
		} else {
			settingsData.renameFlags.remove(flag)
		}
	}

	fun setUserRenamesMappingsPath(userRenamesMappingsPath: Path) {
		settingsData.userRenamesMappingsPath = userRenamesMappingsPath
	}

	fun isSkipSources(): Boolean = settingsData.skipSources

	fun isDebugInfo(): Boolean = settingsData.debugInfo

	fun setDebugInfo(debugInfo: Boolean) {
		settingsData.debugInfo = debugInfo
	}

	fun isSkipResources(): Boolean = settingsData.skipResources

	fun setSkipResources(skipResources: Boolean) {
		settingsData.skipResources = skipResources
	}

	fun getResourceNameSource(): ResourceNameSource = settingsData.resourceNameSource

	fun setResourceNameSource(resourceNameSource: ResourceNameSource) {
		settingsData.resourceNameSource = resourceNameSource
	}

	fun getIntegerFormat(): IntegerFormat = settingsData.integerFormat

	fun setIntegerFormat(format: IntegerFormat) {
		settingsData.integerFormat = format
	}

	fun isFallbackMode(): Boolean = settingsData.fallbackMode

	fun isUseImports(): Boolean = settingsData.useImports

	fun setUseImports(useImports: Boolean) {
		settingsData.useImports = useImports
	}

	fun getDeobfuscationMinLength(): Int = settingsData.deobfuscationMinLength

	fun setDeobfuscationMinLength(deobfuscationMinLength: Int) {
		settingsData.deobfuscationMinLength = deobfuscationMinLength
	}

	fun getGeneratedRenamesMappingFileMode(): GeneratedRenamesMappingFileMode = settingsData.generatedRenamesMappingFileMode

	fun setGeneratedRenamesMappingFileMode(generatedRenamesMappingFileMode: GeneratedRenamesMappingFileMode) {
		settingsData.generatedRenamesMappingFileMode = generatedRenamesMappingFileMode
	}

	fun getDeobfuscationMaxLength(): Int = settingsData.deobfuscationMaxLength

	fun setDeobfuscationMaxLength(deobfuscationMaxLength: Int) {
		settingsData.deobfuscationMaxLength = deobfuscationMaxLength
	}

	fun getThreadsCount(): Int = settingsData.threadsCount

	fun setThreadsCount(threadsCount: Int) {
		settingsData.threadsCount = threadsCount
	}

	fun getSaveOption(): SaveOptionEnum = settingsData.saveOption

	fun setSaveOption(saveOption: SaveOptionEnum) {
		settingsData.saveOption = saveOption
	}
	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(JadxSettings::class.java)

		private const val RECENT_PROJECTS_COUNT: Int = 30

		@JvmStatic
		fun buildConfigAdapter(): JadxConfigAdapter<JadxSettingsData> = JadxConfigAdapter(JadxSettingsData::class.java, "gui") { gsonBuilder ->
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

package kadx.gui.settings

import com.google.gson.annotations.SerializedName
import kadx.cli.LogHelper
import kadx.gui.cache.code.CodeCacheMode
import kadx.gui.cache.usage.UsageCacheMode
import kadx.gui.settings.data.SaveOptionEnum
import kadx.gui.ui.action.ActionModel
import kadx.gui.ui.tab.dnd.TabDndGhostType
import kadx.gui.utils.LafManager
import kadx.gui.utils.LangLocale
import kadx.gui.utils.NLS
import kadx.gui.utils.shortcut.Shortcut
import java.nio.file.Path
import java.nio.file.Paths
import javax.swing.JFrame

/**
 * kadx-gui 的全部设置数据（继承 kadx-cli 的全部选项）。
 *
 * **做什么**：作为 Gson 配置对象被序列化/反序列化到 `gui.json`（见 `KadxConfigAdapter`），
 * 由 [KadxSettings] 包装后供界面读写。
 *
 * **为什么用 Kotlin 属性**：Gson 与 jcommander 都通过**反射字段**读写，Kotlin 属性的背后字段名
 * 与原 Java 字段名完全一致，因此持久化 JSON 的键名与默认值保持不变。
 * 带 [KadxConfigExcludeExport] 的字段在“导出/复制配置”时会被跳过。
 * 注意：`codeFontStr` 需要兼容旧键名 `fontStr`，故保留 [SerializedName]。
 */
class KadxSettingsData : KadxGUIArgs() {

	@field:KadxConfigExcludeExport
	var lastSaveProjectPath: Path = USER_HOME

	@field:KadxConfigExcludeExport
	var lastOpenFilePath: Path = USER_HOME

	@field:KadxConfigExcludeExport
	var lastSaveFilePath: Path = USER_HOME

	@field:KadxConfigExcludeExport
	var recentProjects: MutableList<Path> = ArrayList()

	@field:KadxConfigExcludeExport
	var windowPos: MutableMap<String, WindowLocation> = HashMap()

	@field:KadxConfigExcludeExport
	var mainWindowExtendedState: Int = JFrame.NORMAL

	var flattenPackage: Boolean = false
	var checkForUpdates: Boolean = true
	var kadxUpdateChannel: KadxUpdateChannel = KadxUpdateChannel.STABLE

	var uiZoom: Float = 1.0f

	// Java 侧的 `FontSettings.bindData` 按 `isApplyUiZoomToFonts()` 访问，保留原 getter 名
	@get:JvmName("isApplyUiZoomToFonts")
	var applyUiZoomToFonts: Boolean = true

	var uiFontStr: String = ""

	@field:SerializedName(value = "codeFontStr", alternate = ["fontStr"])
	var codeFontStr: String = ""

	var smaliFontStr: String = ""

	var editorTheme: String = ""

	var lafTheme: String = LafManager.INITIAL_THEME_NAME
	var langLocale: LangLocale = NLS.defaultLocale()
	var autoStartJobs: Boolean = false
	var excludedPackages: String = ""
	var saveOption: SaveOptionEnum = SaveOptionEnum.ASK

	var shortcuts: MutableMap<ActionModel, Shortcut> = HashMap()

	var showHeapUsageBar: Boolean = false
	var alwaysSelectOpened: Boolean = false
	var enablePreviewTab: Boolean = false
	var useAlternativeFileDialog: Boolean = false
	var codeAreaLineWrap: Boolean = false
	var searchResultsPerPage: Int = 50
	var useAutoSearch: Boolean = true
	var keepCommonDialogOpen: Boolean = false
	var lineNumbersMode: LineNumbersMode = LineNumbersMode.AUTO

	var mainWindowVerticalSplitterLoc: Int = 300
	var debuggerStackFrameSplitterLoc: Int = 300
	var debuggerVarTreeSplitterLoc: Int = 700

	var adbDialogPath: String = ""
	var adbDialogHost: String = "localhost"
	var adbDialogPort: String = "5037"

	var codeCacheMode: CodeCacheMode = CodeCacheMode.DISK
	var usageCacheMode: UsageCacheMode = UsageCacheMode.DISK

	/**
	 * 缓存目录选项：
	 * `null` - 使用默认（系统）目录
	 * `"."` - 使用项目目录
	 * 其他 - 自定义路径
	 */
	var cacheDir: String? = null

	var jumpOnDoubleClick: Boolean = true
	var disableTooltipOnHover: Boolean = false

	var xposedCodegenLanguage: XposedCodegenLanguage = XposedCodegenLanguage.JAVA

	var treeWidth: Int = 130
	var dockLogViewer: Boolean = true
	var dockQuickTabs: Boolean = false
	var tabDndGhostType: TabDndGhostType = TabDndGhostType.OUTLINE

	var settingsVersion: Int = CURRENT_SETTINGS_VERSION

	init {
		// GUI 的默认日志级别为 INFO（CLI 默认是 PROGRESS）
		logLevel = LogHelper.LogLevelEnum.INFO
	}

	companion object {
		const val CURRENT_SETTINGS_VERSION: Int = 23

		private val USER_HOME: Path = Paths.get(System.getProperty("user.home"))
	}
}

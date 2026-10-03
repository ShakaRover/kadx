package jadx.gui.ui.codearea.theme

import jadx.core.utils.StringUtils
import jadx.gui.settings.JadxSettings
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea
import org.slf4j.LoggerFactory

/**
 * 编辑器主题管理器：负责注册、切换、应用、卸载代码区主题。
 *
 * **做什么**：
 * - 构造时注册所有内置主题（动态主题 + 7 个 RSTA 自带主题）；
 * - 如果设置里没有保存过主题 ID，就把第一个主题设为默认；
 * - [setTheme] 按 ID 切换主题（切换前先卸载旧主题）；
 * - [apply] 把当前主题应用到具体的代码编辑器。
 *
 * **线程模型**：保持原 Swing 模型，[setTheme] 仍用 `@Synchronized` 串行化，
 * 避免多线程同时切换主题导致状态错乱（K2 禁止 `synchronized fun` 写法）。
 */
class EditorThemeManager(settings: JadxSettings) {

	private val themes: MutableList<IEditorTheme> = ArrayList()
	private val themesMap: MutableMap<String, IEditorTheme> = HashMap()

	private var currentTheme: IEditorTheme = FallbackEditorTheme()

	init {
		registerThemes()
		if (StringUtils.isEmpty(settings.editorTheme)) {
			// 没有配置主题时，把注册的第一个主题设为默认
			val defaultTheme = themes[0]
			settings.setEditorTheme(defaultTheme.getId())
		}
	}

	private fun registerThemes() {
		registerTheme(DynamicCodeAreaTheme())
		registerTheme(RSTABundledTheme("default"))
		registerTheme(RSTABundledTheme("eclipse"))
		registerTheme(RSTABundledTheme("idea"))
		registerTheme(RSTABundledTheme("vs"))
		registerTheme(RSTABundledTheme("dark"))
		registerTheme(RSTABundledTheme("monokai"))
		registerTheme(RSTABundledTheme("druid"))
	}

	/** 注册一个主题；同 ID 的旧主题会被替换并移到列表末尾。 */
	fun registerTheme(editorTheme: IEditorTheme) {
		val prev = themesMap.put(editorTheme.getId(), editorTheme)
		if (prev != null) {
			themes.remove(prev)
		}
		themes.add(editorTheme)
	}

	/** 按 ID 切换主题（已存在同名主题时直接返回）。 */
	@Synchronized
	fun setTheme(id: String) {
		if (currentTheme.getId() == id) {
			// 已经是当前主题
			return
		}
		// 解析新主题
		val newTheme = themesMap[id]
		if (newTheme == null) {
			LOG.warn("Failed to resolve editor theme: {}", id)
			return
		}
		// 先卸载旧主题
		unload()

		// 再加载新主题
		try {
			newTheme.load()
		} catch (t: Throwable) {
			LOG.warn("Failed to load editor theme: {}", id, t)
		}
		currentTheme = newTheme
	}

	/** 把当前主题应用到代码编辑器。 */
	fun apply(textArea: RSyntaxTextArea) {
		currentTheme.apply(textArea)
	}

	/** 返回所有主题的「ID + 显示名」数组，供下拉框使用。 */
	val themeIdNameArray: Array<ThemeIdAndName> get() = themes.map { toThemeIdAndName(it) }.toTypedArray()

	/** 返回当前主题的「ID + 显示名」。 */
	val currentThemeIdName: ThemeIdAndName get() = toThemeIdAndName(currentTheme)

	/** 卸载当前主题（忽略卸载过程中的异常，避免影响界面关闭）。 */
	fun unload() {
		try {
			currentTheme.unload()
		} catch (t: Throwable) {
			LOG.warn("Failed to unload editor theme: {}", currentTheme.getId(), t)
		}
	}

	companion object {
		private val LOG = LoggerFactory.getLogger(EditorThemeManager::class.java)

		private fun toThemeIdAndName(t: IEditorTheme): ThemeIdAndName = ThemeIdAndName(t.getId(), t.getName())
	}
}

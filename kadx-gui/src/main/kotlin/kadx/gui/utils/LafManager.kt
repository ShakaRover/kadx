package kadx.gui.utils

import com.formdev.flatlaf.FlatDarculaLaf
import com.formdev.flatlaf.FlatDarkLaf
import com.formdev.flatlaf.FlatIntelliJLaf
import com.formdev.flatlaf.FlatLightLaf
import com.formdev.flatlaf.intellijthemes.FlatAllIJThemes
import com.formdev.flatlaf.themes.FlatMacDarkLaf
import com.formdev.flatlaf.themes.FlatMacLightLaf
import kadx.gui.settings.KadxSettings
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.LinkedHashMap
import javax.swing.UIManager

/**
 * Look&Feel（界面主题）管理器。
 *
 * **做什么**：维护「主题名 -> LookAndFeel 类名」映射，负责初始化与切换主题。
 *
 * **为什么用 `object`**：原 Java 全部是静态方法与静态字段，`object` + `@JvmStatic`/`@JvmField`
 * 保持 Java 侧访问方式不变。
 */
object LafManager {

	private val LOG: Logger = LoggerFactory.getLogger(LafManager::class.java)

	/** 默认主题名（FlatLaf Light）。 */
	val INITIAL_THEME_NAME: String = FlatLightLaf.NAME

	/** 主题名 -> LookAndFeel 类名；使用 LinkedHashMap 保证下拉框顺序稳定。 */
	private val THEMES_MAP: MutableMap<String, String> = initThemesMap()

	/**
	 * 按设置初始化主题；若设置里的主题已失效则回退到默认主题并同步设置。
	 */
	fun init(settings: KadxSettings) {
		var preferredThemeClass = getThemeClass(settings)

		// 设置中记录的主题已不存在时重置为默认主题
		if (preferredThemeClass == null) {
			settings.setLafTheme(INITIAL_THEME_NAME)
			preferredThemeClass = getThemeClass(settings)
		}

		if (setupLaf(preferredThemeClass)) {
			return
		}
		setupLaf(INITIAL_THEME_NAME)
		settings.setLafTheme(INITIAL_THEME_NAME)
		settings.sync()
	}

	/** 按当前设置切换主题，返回是否成功。 */
	fun updateLaf(settings: KadxSettings): Boolean = setupLaf(getThemeClass(settings))

	/** 返回所有可选主题名。 */
	val themes: Array<String> get() = THEMES_MAP.keys.toTypedArray()

	private fun getThemeClass(settings: KadxSettings): String? = THEMES_MAP[settings.lafTheme]

	private fun setupLaf(themeClass: String?): Boolean {
		if (!themeClass.isNullOrEmpty()) {
			return applyLaf(themeClass)
		}
		return false
	}

	private fun initThemesMap(): MutableMap<String, String> {
		val map = LinkedHashMap<String, String>()

		// FlatLaf 自带主题
		map[FlatLightLaf.NAME] = FlatLightLaf::class.java.name
		map[FlatDarkLaf.NAME] = FlatDarkLaf::class.java.name
		map[FlatMacLightLaf.NAME] = FlatMacLightLaf::class.java.name
		map[FlatMacDarkLaf.NAME] = FlatMacDarkLaf::class.java.name
		map[FlatIntelliJLaf.NAME] = FlatIntelliJLaf::class.java.name
		map[FlatDarculaLaf.NAME] = FlatDarculaLaf::class.java.name

		// flatlaf-intellij-themes 提供的主题
		for (themeInfo in FlatAllIJThemes.INFOS) {
			map[themeInfo.getName()] = themeInfo.getClassName()
		}
		return map
	}

	private fun applyLaf(theme: String): Boolean = try {
		UIManager.setLookAndFeel(theme)
		true
	} catch (e: Exception) {
		LOG.error("Failed to set laf to {}", theme, e)
		false
	}
}

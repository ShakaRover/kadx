package jadx.gui.settings

import jadx.cli.config.IJadxConfig
import jadx.cli.config.JadxConfigAdapter
import jadx.gui.utils.LangLocale
import jadx.gui.utils.NLS
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * 在 GUI 正式初始化前，从配置中“只读取语言”的工具类。
 *
 * **做什么**：界面文字需要在窗口创建前就确定语言，因此这里用一个只含 `langLocale`
 * 字段的精简配置对象提前加载语言；启动后再用 [checkConfig] 校验完整配置里的语言是否一致。
 */
class GuiConfigLocale private constructor() {

	/**
	 * 精简版 [JadxSettingsData]：只保留 `langLocale` 字段。
	 * 字段名即 Gson 的 JSON 键，不能改动。
	 */
	class LocaleConfig : IJadxConfig {
		var langLocale: LangLocale? = null
	}

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(GuiConfigLocale::class.java)

		fun load() {
			LOG.debug("Loading locale config")
			val configAdapter = JadxConfigAdapter(LocaleConfig::class.java, "gui")
			configAdapter.useConfigRef("") // 使用默认配置
			val localeConfig = configAdapter.load()
			val locale = localeConfig?.langLocale
			if (locale != null) {
				NLS.setLocale(locale)
			} else {
				LOG.warn("Can't load locale from config, using default")
				NLS.setLocale(NLS.defaultLocale())
			}
			LOG.debug("Loaded locale config: {}", NLS.currentLocale())
		}

		fun checkConfig(settingsData: JadxSettingsData) {
			val loadedLocale = settingsData.langLocale
			if (NLS.currentLocale() != loadedLocale) {
				LOG.warn("Locale from non-default config loaded only partially!")
				NLS.setLocale(loadedLocale)
			}
		}
	}
}

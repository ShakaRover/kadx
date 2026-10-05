package kadx.gui.utils

import kadx.core.utils.exceptions.KadxRuntimeException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.HashMap
import java.util.Locale
import java.util.MissingResourceException
import java.util.PropertyResourceBundle
import java.util.ResourceBundle
import java.util.Vector

/**
 * 国际化（i18n）文案加载与查询。
 *
 * **做什么**：启动时加载 `i18n/Messages_xx_XX.properties` 全部语言包，
 * 按当前语言返回对应文案；缺失的 key 回退到英文（默认语言）。
 *
 * **为什么用 `object`**：原 Java 全部是静态字段/静态方法，
 * `object` + `@JvmStatic` 保持 Java 侧 `NLS::str` 调用方式不变。
 */
object NLS {

	private val LOG: Logger = LoggerFactory.getLogger(NLS::class.java)

	/** 支持的语言列表（第一个为默认语言 en_US）。 */
	private val LANG_LOCALES = Vector<LangLocale>()

	/** 语言 -> 资源包。 */
	private val LANG_LOCALES_MAP = HashMap<LangLocale, ResourceBundle>()

	/** 默认（英文）资源包，用于缺失 key 的回退。 */
	private val FALLBACK_MESSAGES_MAP: ResourceBundle

	/** 系统默认语言，用于 [defaultLocale]。 */
	private val LOCAL_LOCALE: LangLocale

	// 下面两个字段缓存「当前语言」与「对应资源包」，避免每次 str() 都查两次 Map。
	private lateinit var localizedMessagesMap: ResourceBundle
	private lateinit var currentLocale: LangLocale

	init {
		LOCAL_LOCALE = LangLocale(Locale.getDefault())

		LANG_LOCALES.add(LangLocale("en", "US")) // 默认语言
		LANG_LOCALES.add(LangLocale("zh", "CN"))
		LANG_LOCALES.add(LangLocale("zh", "TW"))
		LANG_LOCALES.add(LangLocale("es", "ES"))
		LANG_LOCALES.add(LangLocale("de", "DE"))
		LANG_LOCALES.add(LangLocale("ko", "KR"))
		LANG_LOCALES.add(LangLocale("pt", "BR"))
		LANG_LOCALES.add(LangLocale("ru", "RU"))
		LANG_LOCALES.add(LangLocale("id", "ID"))

		LANG_LOCALES.forEach { load(it) }

		val defLang = LANG_LOCALES[0]
		FALLBACK_MESSAGES_MAP = checkNotNull(LANG_LOCALES_MAP[defLang])
	}

	private fun load(lang: LangLocale) {
		val locale = lang.get()
		val resName = "i18n/Messages_${locale.toLanguageTag().replace('-', '_')}.properties"
		val bundleUrl = NLS::class.java.classLoader.getResource(resName)
			?: throw KadxRuntimeException("Locale resource not found: $resName")
		val bundle: ResourceBundle = try {
			InputStreamReader(bundleUrl.openStream(), StandardCharsets.UTF_8).use { reader ->
				PropertyResourceBundle(reader)
			}
		} catch (e: Exception) {
			throw KadxRuntimeException("Failed to load $resName", e)
		}
		LANG_LOCALES_MAP[lang] = bundle
	}

	/** 按 key 取当前语言的文案；缺失时回退英文，再缺失则返回 key 本身。 */
	fun str(key: String): String = try {
		localizedMessagesMap.getString(key)
	} catch (e: MissingResourceException) {
		getFallbackString(key)
	}

	/** 按 key 取文案并做 `String.format` 参数替换。 */
	fun str(key: String, vararg parameters: Any?): String = String.format(str(key), *parameters)

	/** 取指定语言的文案（用于语言选择列表展示）。 */
	fun str(key: String, locale: LangLocale): String {
		val bundle = LANG_LOCALES_MAP[locale]
		if (bundle != null) {
			try {
				return bundle.getString(key)
			} catch (ignored: MissingResourceException) {
				// 使用回退文案
			}
		}
		return getFallbackString(key)
	}

	private fun getFallbackString(key: String): String = try {
		FALLBACK_MESSAGES_MAP.getString(key)
	} catch (ex: Exception) {
		LOG.error("Missing fallback value for key: {}", key, ex)
		key
	}

	/** 切换当前语言。 */
	fun setLocale(locale: LangLocale) {
		currentLocale = if (LANG_LOCALES_MAP.containsKey(locale)) {
			locale
		} else {
			LANG_LOCALES[0]
		}
		localizedMessagesMap = checkNotNull(LANG_LOCALES_MAP[currentLocale])
	}

	val langLocales: Vector<LangLocale> get() = LANG_LOCALES

	fun currentLocale(): LangLocale = currentLocale

	/** 系统默认语言；不受支持时回退到英文。 */
	fun defaultLocale(): LangLocale {
		if (LANG_LOCALES_MAP.containsKey(LOCAL_LOCALE)) {
			return LOCAL_LOCALE
		}
		// 不支持的系统语言回退英文
		return LANG_LOCALES[0]
	}
}

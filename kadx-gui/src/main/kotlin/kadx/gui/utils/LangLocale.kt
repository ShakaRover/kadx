package kadx.gui.utils

import java.util.Locale

/**
 * 界面语言（Locale）的包装类。
 *
 * **做什么**：作为设置项中保存/比较语言的对象，并提供 [toString] 展示用的语言名。
 *
 * **为什么保留无参构造器**：Gson 反序列化需要无参构造器，字段名 `locale` 也必须保持不变
 * （Gson 默认按字段名序列化，见 `KadxSettingsData.langLocale`）。
 *
 * 注意：原 Java 的 `getLocale()/setLocale()` 是标准 JavaBean 访问器，这里用 Kotlin
 * 公开属性 `locale` 让编译器自动生成同名访问器，JVM 签名与原来完全一致。
 */
class LangLocale {

	/** 实际的语言；无参构造（Gson 路径）时可能为 null，之后由 setter 填充。 */
	var locale: Locale? = null

	/** Gson 反序列化专用构造器，请勿删除。 */
	constructor()

	constructor(locale: Locale) {
		this.locale = locale
	}

	constructor(l: String, c: String) {
		this.locale = Locale(l, c)
	}

	/** 返回语言；与原 Java 一样，未初始化时视为编程错误。 */
	fun get(): Locale = checkNotNull(locale)

	override fun toString(): String = NLS.str("language.name", this)

	override fun equals(other: Any?): Boolean = other is LangLocale && locale == other.get()

	override fun hashCode(): Int = checkNotNull(locale).hashCode()
}

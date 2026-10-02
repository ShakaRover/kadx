package jadx.api.plugins.options.impl

import jadx.api.plugins.options.JadxPluginOptions
import java.util.Locale
import java.util.function.Function

/**
 * 旧版选项解析基类。
 *
 * **说明**：推荐改用 [BasePluginOptionsBuilder]（更清晰的链式 API），
 * 本类仅为兼容保留，标记为废弃。
 *
 * **为什么 `options` 用 `@JvmField`**：原 Java 是 `protected` 字段，
 * Java 子类直接读写它；Kotlin 属性默认会生成 getter/setter，其中
 * `setOptions(Map)` 会与本类的 `setOptions` 方法签名冲突，故用 `@JvmField`
 * 只生成字段、不生成访问器。
 */
@Deprecated("Prefer BasePluginOptionsBuilder as a better way to init and parse options")
abstract class BaseOptionsParser : JadxPluginOptions {

	@JvmField
	protected var options: Map<String, String>? = null

	override fun setOptions(options: Map<String, String>) {
		this.options = options
		parseOptions()
	}

	abstract fun parseOptions()

	/** 读取布尔选项，接受 yes/no/true/false（大小写不敏感），其它值抛异常。 */
	fun getBooleanOption(key: String, defValue: Boolean): Boolean {
		val value = checkNotNull(options)[key] ?: return defValue
		val valueLower = value.lowercase(Locale.ROOT)
		if (valueLower == "yes" || valueLower == "true") {
			return true
		}
		if (valueLower == "no" || valueLower == "false") {
			return false
		}
		throw IllegalArgumentException("Unknown value '$value' for option '$key', expect: 'yes' or 'no'")
	}

	/** 读取并解析任意类型选项，缺失时返回默认值。 */
	fun <T> getOption(key: String, parse: Function<String, T>, defValue: T): T {
		val value = checkNotNull(options)[key]
		if (value == null) {
			return defValue
		}
		return parse.apply(value)
	}
}

package jadx.api.plugins.options.impl

import jadx.api.plugins.options.JadxPluginOptions
import jadx.api.plugins.options.OptionDescription
import jadx.api.plugins.options.OptionFlag
import jadx.api.plugins.options.OptionType
import org.jetbrains.annotations.Nullable
import java.util.ArrayList
import java.util.Arrays
import java.util.Collections
import java.util.EnumSet
import java.util.Locale
import java.util.function.Consumer
import java.util.function.Function

/**
 * [JadxPluginOptions] 的推荐基类。
 *
 * **怎么用**：子类覆写 [registerOptions]，在其中调用 `option` / `boolOption` /
 * `strOption` / `intOption` / `enumOption` 声明选项，并用链式方法补充描述、
 * 默认值、解析/格式化/存储函数。
 *
 * **为什么构造器里调用 [registerOptions]**：与原 Java 行为一致——实例化时立即
 * 注册并校验所有选项（缺 description/parser/formatter/setter 会抛异常）。
 */
@Suppress("unused")
abstract class BasePluginOptionsBuilder : JadxPluginOptions {

	private val options: MutableList<OptionData<*>> = ArrayList()

	init {
		// 先由子类注册选项，再逐个校验必填项
		registerOptions()
		for (option in options) {
			option.validate()
		}
	}

	abstract fun registerOptions()

	fun <T> option(name: String): OptionBuilder<T> = addOption(OptionData<T>(name))

	fun <T> option(name: String, optionType: Class<T>): OptionBuilder<T> = addOption(OptionData<T>(name))

	fun boolOption(name: String): OptionBuilder<Boolean> = addOption(
		OptionData<Boolean>(name)
			.type(OptionType.BOOLEAN)
			.values(Arrays.asList(true, false))
			.formatter { b -> if (b) "yes" else "no" }
			.parser { v -> parseBoolOption(name, v) },
	)

	fun strOption(name: String): OptionBuilder<String> = addOption(
		OptionData<String>(name)
			.type(OptionType.STRING)
			// 使用 Function.identity() 而非 `{ v -> v }`：Kotlin lambda 会对非空参数插入 null 检查，
			// 而原 Java 的 `v -> v` 允许 null 原样返回（默认值为 null 时需要保持一致）
			.formatter(Function.identity())
			.parser(Function.identity()),
	)

	fun intOption(name: String): OptionBuilder<Int> = addOption(
		OptionData<Int>(name)
			.type(OptionType.NUMBER)
			.formatter { v -> v.toString() }
			.parser { v -> v.toInt() },
	)

	fun <E : Enum<*>> enumOption(name: String, values: Array<E>, valueOf: Function<String, E>): OptionBuilder<E> = addOption(
		OptionData<E>(name)
			.type(OptionType.STRING)
			.values(Arrays.asList(*values))
			.formatter { v -> v.name.lowercase(Locale.ROOT) }
			.parser { v -> valueOf.apply(v.uppercase(Locale.ROOT)) },
	)

	override fun setOptions(map: Map<String, String>) {
		for (option in options) {
			parseOption(option, map[option.name()])
		}
	}

	override fun getOptionsDescriptions(): List<OptionDescription> = Collections.unmodifiableList(options)

	private fun <T> addOption(optionData: OptionBuilder<T>): OptionBuilder<T> {
		this.options.add(optionData as OptionData<*>)
		return optionData
	}

	/**
	 * 单个选项的元数据 + 构建器实现。
	 *
	 * **为什么字段与方法同名**：`values()` / `defaultValue()` / `description()` 等是接口
	 * 方法名，内部字段改用 `valuesList` / `defaultVal` / `desc` 规避冲突；
	 * 私有字段不会生成 getter，因此不会与显式 `override fun` 发生 JVM 签名冲突。
	 */
	protected class OptionData<T>(private val name: String) :
		OptionDescription,
		OptionBuilder<T> {

		private var desc: String? = null
		private var valuesList: List<T> = Collections.emptyList()
		private var type: OptionType = OptionType.STRING
		private var flags: MutableSet<OptionFlag> = EnumSet.noneOf(OptionFlag::class.java)
		private var parser: Function<String, T>? = null
		private var formatter: Function<T, String>? = null
		private var setter: Consumer<T>? = null

		// 供外层 parseOption 读取原始默认值（internal 以避免暴露给 Java 公共 API）
		internal var defaultVal: T? = null

		override fun name(): String = name

		override fun description(): String = checkNotNull(desc)

		override fun values(): List<String> = valuesList.map { checkNotNull(formatter).apply(it) }

		@Nullable
		@Suppress("UNCHECKED_CAST")
		override fun defaultValue(): String? = checkNotNull(formatter).apply(defaultVal as T)

		override fun getType(): OptionType = type

		override fun getFlags(): Set<OptionFlag> = flags

		override fun description(desc: String): OptionBuilder<T> {
			this.desc = desc
			return this
		}

		override fun defaultValue(defValue: T): OptionBuilder<T> {
			this.defaultVal = defValue
			return this
		}

		override fun parser(parser: Function<String, T>): OptionBuilder<T> {
			this.parser = parser
			return this
		}

		override fun formatter(formatter: Function<T, String>): OptionBuilder<T> {
			this.formatter = formatter
			return this
		}

		override fun setter(setter: Consumer<T>): OptionBuilder<T> {
			this.setter = setter
			return this
		}

		override fun type(optionType: OptionType): OptionBuilder<T> {
			this.type = optionType
			return this
		}

		override fun flags(vararg flags: OptionFlag): OptionBuilder<T> {
			this.flags = EnumSet.copyOf(flags.asList())
			return this
		}

		override fun values(values: List<T>): OptionBuilder<T> {
			this.valuesList = values
			return this
		}

		fun getParser(): Function<String, T>? = parser

		fun getFormatter(): Function<T, String>? = formatter

		fun getSetter(): Consumer<T>? = setter

		fun validate() {
			val description = desc
			if (description == null || description.isEmpty()) {
				throw IllegalArgumentException("Description should be set for option: $name")
			}
			if (parser == null) {
				throw IllegalArgumentException("Parser should be set for option: $name")
			}
			if (formatter == null) {
				throw IllegalArgumentException("Formatter should be set for option: $name")
			}
			if (setter == null) {
				throw IllegalArgumentException("Setter should be set for option: $name")
			}
		}
	}
	companion object {
		@Suppress("UNCHECKED_CAST")
		private fun <T> parseOption(option: OptionData<T>, value: String?) {
			val parsedValue: T
			if (value == null) {
				parsedValue = option.defaultVal as T
			} else {
				parsedValue = try {
					checkNotNull(option.getParser()).apply(value)
				} catch (e: Exception) {
					throw RuntimeException("Parse failed for option: ${option.name()}, value: $value", e)
				}
			}
			try {
				checkNotNull(option.getSetter()).accept(parsedValue)
			} catch (e: Exception) {
				throw RuntimeException("Setter invoke failed for option: ${option.name()}, value: $parsedValue", e)
			}
		}

		private fun parseBoolOption(name: String, value: String): Boolean {
			val valueLower = value.trim().lowercase(Locale.ROOT)
			if (valueLower == "yes" || valueLower == "true") {
				return true
			}
			if (valueLower == "no" || valueLower == "false") {
				return false
			}
			throw IllegalArgumentException("Unknown value '$value' for option '$name', expect: 'yes' or 'no'")
		}
	}
}

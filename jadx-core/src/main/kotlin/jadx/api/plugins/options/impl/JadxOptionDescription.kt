package jadx.api.plugins.options.impl

import jadx.api.plugins.options.OptionDescription
import jadx.api.plugins.options.OptionFlag
import jadx.api.plugins.options.OptionType
import org.jetbrains.annotations.Nullable
import java.util.Arrays
import java.util.Collections
import java.util.EnumSet

/**
 * [OptionDescription] 的简单实现。
 *
 * **做什么**：直接持有名称、描述、默认值、可选值与类型，并允许通过
 * [withFlag] / [withFlags] 追加标志位。
 */
class JadxOptionDescription(
	private val name: String,
	private val desc: String,
	private val defaultVal: String?,
	private val valuesList: List<String>,
	private val type: OptionType,
) : OptionDescription {

	private val flags: MutableSet<OptionFlag> = EnumSet.noneOf(OptionFlag::class.java)

	constructor(name: String, desc: String, defaultValue: String?, values: List<String>) :
		this(name, desc, defaultValue, values, OptionType.STRING)

	override fun name(): String = name

	override fun description(): String = desc

	@Nullable
	override fun defaultValue(): String? = defaultVal

	override fun values(): List<String> = valuesList

	override fun getType(): OptionType = type

	override fun getFlags(): Set<OptionFlag> = flags

	/** 追加单个标志位（链式）。 */
	fun withFlag(flag: OptionFlag): JadxOptionDescription {
		this.flags.add(flag)
		return this
	}

	/** 追加多个标志位（链式）。 */
	fun withFlags(vararg flags: OptionFlag): JadxOptionDescription {
		Collections.addAll(this.flags, *flags)
		return this
	}

	override fun toString(): String = "OptionDescription{$desc, values=$valuesList}"

	companion object {
		/** 构建一个布尔选项描述（取值固定为 yes/no）。 */
		@JvmStatic
		fun booleanOption(name: String, desc: String, defaultValue: Boolean): JadxOptionDescription = JadxOptionDescription(
			name,
			desc,
			if (defaultValue) "yes" else "no",
			Arrays.asList("yes", "no"),
			OptionType.BOOLEAN,
		)
	}
}

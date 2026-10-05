package kadx.plugins.mappings

import kadx.api.plugins.options.OptionFlag
import kadx.api.plugins.options.impl.BasePluginOptionsBuilder
import kadx.core.utils.ListUtils
import net.fabricmc.mappingio.format.MappingFormat
import java.util.Locale

/**
 * rename-mappings 插件选项。
 *
 * **背景**：[format] 为 null 表示 AUTO（自动探测格式）；[invert] 在加载时交换源/目标命名空间。
 * INVERT_OPT / FORMAT_OPT 常量被 kadx-gui 的 Java 代码直接引用，故用 const val 保持静态字段访问。
 */
public class RenameMappingsOptions : BasePluginOptionsBuilder() {

	private var invert = false

	/** null 值表示 'auto' 选项 */
	private var formatValue: MappingFormat? = null

	override fun registerOptions() {
		// 显式指定可空类型参数：format 允许 null（表示 AUTO）
		option<MappingFormat?>(FORMAT_OPT)
			.description("mapping format")
			.parser(::parseMappingFormat)
			.formatter { v -> if (v == null) "AUTO" else v.name }
			.values(ListUtils.concat<MappingFormat>(null, MappingFormat.values()))
			.defaultValue(null)
			.flags(OptionFlag.PER_PROJECT, OptionFlag.DISABLE_IN_GUI)
			.setter { v -> formatValue = v }

		boolOption(INVERT_OPT)
			.description("invert mapping on load")
			.defaultValue(false)
			.flags(OptionFlag.PER_PROJECT)
			.setter { v -> invert = v }
	}

	public val format: MappingFormat? get() = formatValue

	// 原 Java 是原始 boolean 的 isXxx() getter，Kotlin 属性会生成 getXxx()，故显式声明保持方法名
	public val isInvert: Boolean get() = invert

	public val optionsHashString: String get() = "$formatValue:$invert"

	public companion object {
		const val INVERT_OPT: String = RenameMappingsPlugin.PLUGIN_ID + ".invert"
		const val FORMAT_OPT: String = RenameMappingsPlugin.PLUGIN_ID + ".format"

		private fun parseMappingFormat(name: String): MappingFormat? {
			val upName = name.uppercase(Locale.ROOT)
			if (upName == "AUTO") {
				return null
			}
			return MappingFormat.valueOf(upName)
		}
	}
}

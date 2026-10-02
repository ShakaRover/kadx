package jadx.api.plugins.options

import org.jetbrains.annotations.Nullable
import java.util.Collections

/**
 * 单个插件选项的描述信息：名称、说明、可选值、默认值与类型/标志。
 *
 * **注意方法名不是 getter 风格**：`name()` / `description()` / `values()` /
 * `defaultValue()` 是原 Java 接口的方法名，必须原样保留；只有 `getType()` /
 * `getFlags()` 是 getter，且带有默认实现（Java 实现方可不覆写）。
 */
interface OptionDescription {

	fun name(): String

	fun description(): String

	/**
	 * 可选的取值列表。
	 * 若不是有限取值集合，则返回空列表。
	 */
	fun values(): List<String>

	/**
	 * 默认值。
	 * 若该选项必填则返回 null。
	 */
	@Nullable
	fun defaultValue(): String?

	fun getType(): OptionType = OptionType.STRING

	fun getFlags(): Set<OptionFlag> = Collections.emptySet()
}

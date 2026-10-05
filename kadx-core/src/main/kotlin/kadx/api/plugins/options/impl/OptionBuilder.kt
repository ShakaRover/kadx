package kadx.api.plugins.options.impl

import kadx.api.plugins.options.OptionFlag
import kadx.api.plugins.options.OptionType
import java.util.function.Consumer
import java.util.function.Function

/**
 * 选项构建器接口：链式配置一个选项的描述、解析/格式化/存储函数与类型。
 *
 * **为什么用 Java 函数式接口**：Java 插件以 lambda/方法引用传入
 * `Function` / `Consumer`，保持 `java.util.function.*` 参数类型可让 Java 调用方零改动。
 */
interface OptionBuilder<T> {

	/**
	 * 选项描述（必填）。
	 */
	fun description(desc: String): OptionBuilder<T>

	fun defaultValue(defValue: T): OptionBuilder<T>

	/**
	 * 把输入字符串解析为选项值的函数（必填）。
	 */
	fun parser(parser: Function<String, T>): OptionBuilder<T>

	/**
	 * 把选项值格式化为字符串（用于生成帮助文本）的函数（必填）。
	 */
	fun formatter(formatter: Function<T, String>): OptionBuilder<T>

	/**
	 * 保存/应用解析后选项值的函数（必填）。
	 */
	fun setter(setter: Consumer<T>): OptionBuilder<T>

	/**
	 * 可选的取值集合。
	 */
	fun values(values: List<T>): OptionBuilder<T>

	fun type(optionType: OptionType): OptionBuilder<T>

	fun flags(vararg flags: OptionFlag): OptionBuilder<T>
}

package jadx.plugins.input.javaconvert

import jadx.api.plugins.options.impl.BasePluginOptionsBuilder

/**
 * java-convert 插件选项。
 *
 * **背景**：[mode] 选择转换后端（DX / D8 / BOTH），[d8Desugar] 控制 D8 是否启用 desugaring。
 * 默认值在 JadxArgs 解析阶段通过 setter 应用，所以 init() 之后 [mode] 实际非空；
 * 但字段声明上仍保持可空（与原 Java 的 null 初始值一致）。
 */
public class JavaConvertOptions : BasePluginOptionsBuilder() {

	public enum class Mode {
		DX,
		D8,
		BOTH,
	}

	private var mode: Mode? = null
	private var d8Desugar = false

	override fun registerOptions() {
		enumOption(JavaConvertPlugin.PLUGIN_ID + ".mode", Mode.values(), Mode::valueOf)
			.description("convert mode")
			.defaultValue(Mode.BOTH)
			.setter { v -> mode = v }

		boolOption(JavaConvertPlugin.PLUGIN_ID + ".d8-desugar")
			.description("use desugar in d8")
			.defaultValue(false)
			.setter { v -> d8Desugar = v }
	}

	public fun getMode(): Mode? = mode

	// 原 Java 是原始 boolean 的 isXxx() getter，Kotlin 属性会生成 getXxx()，故显式声明保持方法名
	public fun isD8Desugar(): Boolean = d8Desugar
}

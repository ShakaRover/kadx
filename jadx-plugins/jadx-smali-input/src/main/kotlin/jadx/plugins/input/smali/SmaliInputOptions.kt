package jadx.plugins.input.smali

import jadx.api.plugins.options.impl.BasePluginOptionsBuilder

/**
 * Smali 输入插件选项。
 *
 * **背景**：[apiLevel] 通过 [registerOptions] 注册为命令行可配置项（默认 27）；
 * [threads] 不是用户选项，由插件 init() 时从全局线程数设置同步过来。
 */
public class SmaliInputOptions : BasePluginOptionsBuilder() {

	private var apiLevelValue = 0

	/** 并行编译 smali 文件的线程数（init 时从全局配置填充） */
	public var threads: Int = 0

	override fun registerOptions() {
		intOption(SmaliInputPlugin.PLUGIN_ID + ".api-level")
			.description("Android API level")
			.defaultValue(27)
			.setter { v -> apiLevelValue = v }
	}

	public val apiLevel: Int get() = apiLevelValue
}

package kadx.plugins.input.dex

import kadx.api.plugins.options.impl.BasePluginOptionsBuilder

/**
 * DEX 输入插件选项。
 *
 * **背景**：[verifyChecksum] 通过 [registerOptions] 注册为命令行可配置项（默认 true），
 * 控制加载 DEX 前是否先做 Adler32 校验和验证（见同模块 utils 包的 DexCheckSum）。
 */
public class DexInputOptions : BasePluginOptionsBuilder() {

	private var verifyChecksum = false

	override fun registerOptions() {
		boolOption(DexInputPlugin.PLUGIN_ID + ".verify-checksum")
			.description("verify dex file checksum before load")
			.defaultValue(true)
			.setter { v -> verifyChecksum = v }
	}

	public val isVerifyChecksum: Boolean get() = verifyChecksum
}

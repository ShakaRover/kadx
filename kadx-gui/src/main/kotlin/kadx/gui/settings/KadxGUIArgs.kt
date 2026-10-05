package kadx.gui.settings

import com.beust.jcommander.Parameter
import kadx.cli.KadxCLIArgs
import kadx.cli.config.KadxConfigExclude

/**
 * kadx-gui 在 CLI 参数基础上追加的 GUI 专用参数。
 *
 * **做什么**：继承 [KadxCLIArgs] 的全部命令行/配置字段，并增加 `--select-class`，
 * 用于启动后直接定位到指定类。`KadxSettingsData` 会继承本类以复用这些参数。
 *
 * **为什么用 Kotlin 属性**：jcommander 通过反射字段读写参数，Gson 通过字段读写配置；
 * Kotlin 属性的背后字段名与原 Java 字段名一致，注解默认落在字段上，行为不变。
 */
open class KadxGUIArgs : KadxCLIArgs() {

	@KadxConfigExclude
	@Parameter(
		names = ["-sc", "--select-class"],
		description = "GUI: Open the selected class and show the decompiled code",
	)
	var cmdSelectClass: String? = null
}

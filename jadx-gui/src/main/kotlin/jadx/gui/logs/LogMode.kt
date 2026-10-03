package jadx.gui.logs

import jadx.gui.utils.NLS
import org.apache.commons.lang3.StringUtils

/**
 * 日志查看器的过滤模式。
 *
 * - [ALL]：显示全部日志；
 * - [ALL_SCRIPTS]：只显示所有脚本产生的日志（logger 名以 `JadxScript:` 开头）；
 * - [CURRENT_SCRIPT]：只显示当前脚本的日志（logger 名等于过滤串）。
 *
 * **本地化**：[NLS_STRINGS] 按枚举 ordinal 顺序与 `log_viewer.modes` 资源串一一对应。
 */
enum class LogMode {
	ALL,
	ALL_SCRIPTS,
	CURRENT_SCRIPT,
	;

	/** 返回当前模式的本地化显示名。 */
	fun getLocalizedName(): String = NLS_STRINGS[ordinal]

	override fun toString(): String = getLocalizedName()

	companion object {
		/** `log_viewer.modes` 按 `|` 拆出的模式名，顺序必须与枚举常量一致。 */
		private val NLS_STRINGS: Array<String> = StringUtils.split(NLS.str("log_viewer.modes"), '|')
	}
}

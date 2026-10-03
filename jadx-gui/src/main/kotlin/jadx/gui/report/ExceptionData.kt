package jadx.gui.report

import java.io.IOException

/**
 * 异常上报对话框所需的数据载体。
 *
 * **做什么**：把原始异常、应上报到的 GitHub 项目、以及可能存在的 [IOException] 打包，
 * 供 [ExceptionDialog] / [IOExceptionMessageBox] 决定展示哪种提示。
 *
 * **为什么不是 `data class`**：原 Java 类没有值语义，仅是只读容器。
 * 这里用「私有字段 + 显式 getter」保留原 JVM 方法名（注意 `getIOExc` 的拼写）。
 */
class ExceptionData internal constructor(
	private val exc: Throwable,
	private val githubProjectValue: String,
	private val ioException: IOException?,
) {

	fun getException(): Throwable = exc

	fun getIOExc(): IOException? = ioException

	fun getGithubProject(): String = githubProjectValue
}

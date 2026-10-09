package kadx.gui.report

import kadx.gui.ui.MainWindow
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException

/**
 * 未捕获异常处理器：注册为线程默认的 [Thread.UncaughtExceptionHandler]。
 *
 * **做什么**：捕获未处理异常后先记录日志；若是 I/O 异常则走 [IOExceptionMessageBox]，
 * 否则弹 [ExceptionDialog]。异常一律归到 kadx 主项目 —— kadx 已不再支持外部插件，
 * 不存在“异常来自某个插件”的情况，因此不再需要按插件类加载器名反查项目。
 *
 * **为什么保持 [Thread.UncaughtExceptionHandler]**：JVM 只认这个接口，签名必须精确不变。
 */
class KadxExceptionHandler private constructor(private val mainWindow: MainWindow) : Thread.UncaughtExceptionHandler {

	override fun uncaughtException(thread: Thread, ex: Throwable) {
		LOG.error("Exception was thrown", ex)
		val excData = buildExceptionData(ex)
		if (excData.iOExc != null) {
			IOExceptionMessageBox.show(mainWindow, excData)
		} else {
			ExceptionDialog.show(mainWindow, excData)
		}
	}

	/** 沿 cause 链收集 I/O 异常。 */
	private fun buildExceptionData(ex: Throwable): ExceptionData {
		var ioExc: IOException? = null
		var curExc: Throwable? = ex
		while (curExc != null) {
			val current = curExc
			if (current is IOException) {
				ioExc = current
			}
			curExc = current.cause
		}
		return ExceptionData(ex, MAIN_PROJECT_STRING, ioExc)
	}

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(KadxExceptionHandler::class.java)

		const val MAIN_PROJECT_STRING: String = "ShakaRover/kadx"

		/** 把本处理器安装为 JVM 默认的未捕获异常处理器。 */
		fun register(mainWindow: MainWindow) {
			Thread.setDefaultUncaughtExceptionHandler(KadxExceptionHandler(mainWindow))
		}
	}
}

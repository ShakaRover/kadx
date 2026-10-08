package kadx.gui.report

import kadx.core.utils.Utils
import kadx.gui.ui.MainWindow
import kadx.plugins.tools.KadxExternalPluginsLoader
import kadx.plugins.tools.KadxPluginsTools
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException

/**
 * 未捕获异常处理器：注册为线程默认的 [Thread.UncaughtExceptionHandler]。
 *
 * **做什么**：捕获未处理异常后，先记录日志，再尽力从堆栈里识别出「异常来自哪个插件」，
 * 以便把问题上报到正确的 GitHub 项目；若是 I/O 异常则走 [IOExceptionMessageBox]，
 * 否则弹 [ExceptionDialog]。
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

	/** 沿 cause 链收集 I/O 异常，并查找首个插件来源。 */
	private fun buildExceptionData(ex: Throwable): ExceptionData {
		var projectName: String? = null
		var ioExc: IOException? = null
		var curExc: Throwable? = ex
		while (curExc != null) {
			val current = curExc
			if (current is IOException) {
				ioExc = current
			}
			if (projectName == null) {
				projectName = searchPluginInStackTrace(current)
			}
			curExc = current.cause
		}
		return ExceptionData(ex, Utils.getOrElse(projectName, MAIN_PROJECT_STRING), ioExc)
	}

	/** 在堆栈中查找带插件类加载器名的帧，据此解析出插件所属项目。 */
	private fun searchPluginInStackTrace(curExc: Throwable): String? {
		for (stackTraceElement in curExc.stackTrace) {
			val classLoaderName = stackTraceElement.classLoaderName
			val prefix = KadxExternalPluginsLoader.KADX_PLUGIN_CLASSLOADER_PREFIX
			if (classLoaderName != null && classLoaderName.startsWith(prefix)) {
				val jarName = classLoaderName.substring(prefix.length)
				val pluginProject = resolvePluginByJarName(jarName)
				LOG.debug("Report exception in plugin: {}", pluginProject)
				return pluginProject
			}
		}
		return null
	}

	/** 用 jar 文件名在已安装插件列表中反查项目名。 */
	private fun resolvePluginByJarName(jarName: String): String {
		for (kadxPluginMetadata in KadxPluginsTools.instance.getInstalled()) {
			if (kadxPluginMetadata.path == jarName) {
				val githubProject = getGithubProject(kadxPluginMetadata.locationId)
				return githubProject ?: ""
			}
		}
		return ""
	}

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(KadxExceptionHandler::class.java)

		const val MAIN_PROJECT_STRING: String = "ShakaRover/kadx"

		/** 把本处理器安装为 JVM 默认的未捕获异常处理器。 */
		fun register(mainWindow: MainWindow) {
			Thread.setDefaultUncaughtExceptionHandler(KadxExceptionHandler(mainWindow))
		}

		/** 从 `github:owner:repo` 形式的 locationId 解析出 `owner/repo`。 */
		private fun getGithubProject(locationId: String?): String? {
			if (locationId != null && locationId.startsWith("github:")) {
				return locationId.substring("github:".length).replace(':', '/')
			}
			return null
		}
	}
}

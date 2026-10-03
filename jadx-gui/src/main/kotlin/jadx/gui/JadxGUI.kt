package jadx.gui

import jadx.cli.JadxCLIArgs
import jadx.cli.config.JadxConfigAdapter
import jadx.commons.app.JadxSystemInfo
import jadx.core.Jadx
import jadx.core.utils.JadxBuildInfo
import jadx.core.utils.files.FileUtils
import jadx.gui.logs.LogCollector
import jadx.gui.settings.GuiConfigLocale
import jadx.gui.settings.JadxSettings
import jadx.gui.settings.JadxSettingsData
import jadx.gui.ui.MainWindow
import jadx.gui.utils.LafManager
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.awt.Desktop
import javax.swing.SwingUtilities

/**
 * jadx-gui 的应用入口。
 *
 * **做什么**：解析命令行参数并加载 GUI 配置，注册日志收集器与系统信息输出，
 * 然后在 Swing 事件派发线程（EDT）上初始化外观、创建主窗口并注册“打开文件”系统事件处理器。
 *
 * **线程模型（阶段 5.1 保持不变）**：初始化工作分两段：
 * 1. 配置加载、日志注册在主线程执行；
 * 2. 所有 Swing 组件创建都通过 [SwingUtilities.invokeLater] 投递到 EDT，
 *    与原 Java 完全一致，未引入协程。
 *
 * **为什么是 `class` + `companion object` + `@JvmStatic fun main`**：
 * 打包配置（`mainClass.set("jadx.gui.JadxGUI")`）要求 JVM 入口仍是 `jadx.gui.JadxGUI` 类。
 * Kotlin 顶层 `fun main` 会生成 `JadxGUIKt` 类，因此必须把 `main` 放在伴生对象里并用
 * `@JvmStatic` 暴露为外层类的静态方法。
 */
class JadxGUI {

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(JadxGUI::class.java)

		/**
		 * 程序入口：加载配置并启动主窗口。
		 *
		 * @param args 命令行参数（与 CLI 共用解析逻辑）
		 */
		@JvmStatic
		fun main(args: Array<String>) {
			try {
				GuiConfigLocale.load()
				val configAdapter: JadxConfigAdapter<JadxSettingsData> = JadxSettings.buildConfigAdapter()
				val settingsData = JadxCLIArgs.processArgs(args, JadxSettingsData(), configAdapter)
				if (settingsData == null) {
					return
				}
				val settings = JadxSettings(configAdapter)
				settings.loadSettingsData(settingsData)
				GuiConfigLocale.checkConfig(settingsData)

				LogCollector.register()
				printSystemInfo()
				SwingUtilities.invokeLater {
					LafManager.init(settings)
					settings.getFontSettings().updateDefaultFont()
					val mw = MainWindow(settings)
					registerOpenFileHandler(mw)
					mw.init()
				}
			} catch (e: Exception) {
				LOG.error("Error: {}", e.message, e)
				System.exit(1)
			}
		}

		/**
		 * 注册系统级“打开文件”处理器（如桌面双击关联文件）。
		 * 平台不支持或注册失败时仅记录日志，不影响主流程。
		 */
		private fun registerOpenFileHandler(mw: MainWindow) {
			try {
				if (Desktop.isDesktopSupported()) {
					val desktop = Desktop.getDesktop()
					if (desktop.isSupported(Desktop.Action.APP_OPEN_FILE)) {
						desktop.setOpenFileHandler { e -> mw.open(FileUtils.toPaths(e.getFiles())) }
					}
				}
			} catch (e: Throwable) {
				LOG.error("Failed to register open file handler", e)
			}
		}

		/** 在 debug 级别日志中打印 jadx / JVM / 操作系统版本信息。 */
		private fun printSystemInfo() {
			if (LOG.isDebugEnabled()) {
				LOG.debug(
					"Starting jadx-gui: version: {}, bundle: {}. JVM: {} {}. OS: {}, version: {}, arch: {}",
					Jadx.getVersion(),
					JadxBuildInfo.getJadxBundleType(),
					JadxSystemInfo.JAVA_VM,
					JadxSystemInfo.JAVA_VER,
					JadxSystemInfo.OS_NAME,
					JadxSystemInfo.OS_VERSION,
					JadxSystemInfo.OS_ARCH,
				)
			}
		}
	}
}

package jadx.gui.plugins.quark

import ch.qos.logback.classic.Level
import jadx.commons.app.JadxSystemInfo
import jadx.core.utils.exceptions.JadxRuntimeException
import jadx.gui.jobs.BackgroundExecutor
import jadx.gui.logs.LogOptions
import jadx.gui.treemodel.JRoot
import jadx.gui.ui.MainWindow
import jadx.gui.utils.UiUtils
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.function.Consumer
import javax.swing.JOptionPane

/**
 * Quark 分析任务的调度与执行。
 *
 * **做什么**：检查/安装 Quark（必要时创建 Python venv），更新规则，
 * 调用 `quark` 命令生成 JSON 报告，并把报告节点挂到树上。
 *
 * **线程模型**：保持原 Swing 模型，安装与分析都通过 `BackgroundExecutor` 串行执行。
 */
class QuarkManager(private val mainWindow: MainWindow, private val apkPath: Path) {

	private var useVEnv = false
	private var installComplete = false
	private var reportFile: Path? = null

	/** 启动完整流程：检查安装 -> 后台分析 -> 加载报告。 */
	fun start() {
		if (!checkFileSize(LARGE_APK_SIZE)) {
			val result = JOptionPane.showConfirmDialog(
				mainWindow,
				"The selected file size is too large (over 30M) that may take a long time to analyze, do you want to continue",
				"Quark: Warning",
				JOptionPane.YES_NO_OPTION,
			)
			if (result == JOptionPane.NO_OPTION) {
				return
			}
		}
		val executor: BackgroundExecutor = mainWindow.getBackgroundExecutor()
		executor.execute(
			"Quark install",
			Runnable { checkInstall() },
			Consumer { executor.execute("Quark analysis", Runnable { startAnalysis() }, Consumer { loadReport() }) },
		)
	}

	private fun checkInstall() {
		try {
			if (checkCommand("quark")) {
				useVEnv = false
				installComplete = true
				return
			}
			useVEnv = true
			if (checkVEnvCommand("quark")) {
				installComplete = true
				installQuark() // 升级 quark
				return
			}
			val result = JOptionPane.showConfirmDialog(
				mainWindow,
				"Quark is not installed, do you want to install it from PyPI?",
				"Warning",
				JOptionPane.YES_NO_OPTION,
			)
			if (result == JOptionPane.NO_OPTION) {
				installComplete = false
				return
			}
			createVirtualEnv()
			installQuark()
			installComplete = true
		} catch (e: Exception) {
			UiUtils.errorMessage(mainWindow, checkNotNull(e.message))
			LOG.error("Failed to install quark", e)
			installComplete = false
		}
	}
	private fun startAnalysis() {
		if (!installComplete) {
			return
		}
		try {
			updateQuarkRules()
			val report = Files.createTempFile("QuarkReport-", ".json").toAbsolutePath()
			reportFile = report
			val cmd = ArrayList<String>()
			cmd.add(getCommand("quark"))
			cmd.add("-a")
			cmd.add(apkPath.toString())
			cmd.add("-o")
			cmd.add(report.toString())
			runCommand(cmd)
		} catch (e: Exception) {
			UiUtils.errorMessage(mainWindow, "Failed to execute Quark")
			LOG.error("Failed to execute Quark", e)
		}
	}

	private fun loadReport() {
		try {
			val quarkNode = QuarkReportNode(checkNotNull(reportFile))
			val root: JRoot = mainWindow.getTreeRoot()
			root.replaceCustomNode(quarkNode)
			root.update()
			mainWindow.reloadTree()
			mainWindow.getTabsController().selectTab(quarkNode)
		} catch (e: Exception) {
			UiUtils.errorMessage(mainWindow, "Failed to load Quark report.")
			LOG.error("Failed to load Quark report.", e)
		}
	}
	private fun createVirtualEnv() {
		if (Files.exists(getVenvPath("activate"))) {
			return
		}
		val directory = QUARK_DIR_PATH.toFile()
		if (!directory.isDirectory) {
			if (!directory.mkdirs()) {
				throw JadxRuntimeException("Failed create directory: $directory")
			}
		}
		val cmd = ArrayList<String>()
		cmd.add("python")
		cmd.add("-m")
		cmd.add("venv")
		cmd.add(VENV_PATH.toString())
		try {
			runCommand(cmd)
		} catch (e: Exception) {
			throw JadxRuntimeException("Failed to create virtual environment", e)
		}
	}

	private fun installQuark() {
		val cmd = ArrayList<String>()
		cmd.add(getCommand("pip3"))
		cmd.add("install")
		cmd.add("setuptools")
		cmd.add("quark-engine")
		cmd.add("--upgrade")
		try {
			runCommand(cmd)
		} catch (e: Exception) {
			throw JadxRuntimeException("Failed to install quark-engine", e)
		}
	}

	private fun updateQuarkRules() {
		val cmd = ArrayList<String>()
		cmd.add(getCommand("freshquark"))
		try {
			runCommand(cmd)
		} catch (e: Exception) {
			throw JadxRuntimeException("Failed to update quark rules", e)
		}
	}

	/** 根据文件大小判断是否需要提醒用户。 */
	fun checkFileSize(sizeThreshold: Int): Boolean {
		try {
			val fileSize = Files.size(apkPath).toInt() / 1024 / 1024
			if (fileSize > sizeThreshold) {
				return false
			}
		} catch (e: Exception) {
			LOG.error("Failed to calculate file: {}", e.message, e)
			return false
		}
		return true
	}

	private fun getCommand(cmd: String): String = if (useVEnv) getVenvPath(cmd).toAbsolutePath().toString() else cmd

	private fun checkVEnvCommand(cmd: String): Boolean {
		val venvPath = getVenvPath(cmd)
		return checkCommand(venvPath.toAbsolutePath().toString())
	}

	private fun getVenvPath(cmd: String): Path = if (JadxSystemInfo.IS_WINDOWS) {
		VENV_PATH.resolve("Scripts").resolve("$cmd.exe")
	} else {
		VENV_PATH.resolve("bin").resolve(cmd)
	}
	private fun runCommand(cmd: List<String>) {
		mainWindow.showLogViewer(LogOptions.forLevel(Level.INFO))
		LOG.info("Running command: {}", cmd.joinToString(" "))
		val builder = ProcessBuilder(cmd)
		builder.redirectErrorStream(true)
		val process = builder.start()
		try {
			BufferedReader(InputStreamReader(process.getInputStream())).use { buf ->
				buf.lines().forEach { msg -> LOG.info("# {}", msg) }
			}
		} finally {
			process.waitFor()
		}
		if (process.exitValue() != 0) {
			throw RuntimeException(
				"Execution failed (exit code " + process.exitValue() + ") - command " +
					cmd.joinToString(" ") + "\nPlease see command log output what was going wrong.",
			)
		}
	}

	private fun checkCommand(vararg cmd: String): Boolean = try {
		val process = Runtime.getRuntime().exec(cmd)
		process.waitFor()
		true
	} catch (e: Exception) {
		false
	}

	companion object {
		private val QUARK_DIR_PATH: Path = Paths.get(System.getProperty("user.home"), ".quark-engine")
		private val VENV_PATH: Path = QUARK_DIR_PATH.resolve("quark_venv")
		private const val LARGE_APK_SIZE = 30

		private val LOG = LoggerFactory.getLogger(QuarkManager::class.java)
	}
}

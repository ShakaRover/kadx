package jadx.gui.jobs

import jadx.api.ICodeCache
import jadx.api.utils.tasks.ITaskExecutor
import jadx.gui.JadxWrapper
import jadx.gui.cache.code.CodeCacheMode
import jadx.gui.cache.code.FixedCodeCache
import jadx.gui.ui.MainWindow
import jadx.gui.utils.NLS
import java.io.File
import javax.swing.JOptionPane

/**
 * 导出任务：把反编译结果（源码 / 资源）保存到磁盘。
 *
 * **做什么**：调用 [jadx.api.JadxDecompiler.getSaveTaskExecutor] 取得保存任务，
 * 并把工程的根目录指向 [saveDir]。
 *
 * **代码缓存处理**：导出过程中若缓存模式不是磁盘缓存，则临时用
 * [FixedCodeCache] 包装原缓存，避免把新反编译的代码写入内存缓存；
 * [onFinish] 时再恢复原缓存。
 *
 * **为什么不用 `data class`**：任务对象需要按引用比较。
 */
class ExportTask(
	private val mainWindow: MainWindow,
	private val wrapper: JadxWrapper,
	private val saveDir: File,
) : CancelableBackgroundTask() {

	private var timeLimit: Int = 0
	private var uiCodeCache: ICodeCache? = null

	override val title: String get() = NLS.str("msg.saving_sources")

	override fun scheduleTasks(): ITaskExecutor {
		wrapCodeCache()
		wrapper.args.setRootDir(saveDir)
		val saveTasks = wrapper.getDecompiler().getSaveTaskExecutor()
		this.timeLimit = DecompileTask.calcDecompileTimeLimit(saveTasks.getTasksCount())
		return saveTasks
	}

	private fun wrapCodeCache() {
		uiCodeCache = wrapper.args.codeCache
		if (mainWindow.getSettings().codeCacheMode != CodeCacheMode.DISK) {
			// 不把新反编译的代码写入缓存，避免内存占用增加
			// TODO: 或许可以实现一个内存受限的缓存？
			wrapper.args.codeCache = FixedCodeCache(checkNotNull(uiCodeCache))
		}
	}

	override fun onFinish(taskInfo: ITaskInfo) {
		// 恢复初始代码缓存
		wrapper.args.codeCache = checkNotNull(uiCodeCache)
		if (taskInfo.jobsSkipped == 0L) {
			return
		}
		val reason = getIncompleteReason(taskInfo.status)
		if (reason != null) {
			JOptionPane.showMessageDialog(
				mainWindow,
				NLS.str("message.saveIncomplete", reason, taskInfo.jobsSkipped),
				NLS.str("message.errorTitle"),
				JOptionPane.ERROR_MESSAGE,
			)
		}
	}

	/** 把任务状态映射为“未完成原因”文案；正常状态返回 `null`。 */
	private fun getIncompleteReason(status: TaskStatus): String? = when (status) {
		TaskStatus.CANCEL_BY_USER -> NLS.str("message.userCancelTask")

		TaskStatus.CANCEL_BY_TIMEOUT -> NLS.str("message.taskTimeout", timeLimit())

		TaskStatus.CANCEL_BY_MEMORY -> {
			mainWindow.showHeapUsageBar()
			NLS.str("message.memoryLow")
		}

		TaskStatus.ERROR -> NLS.str("message.taskError")

		else -> null
	}

	override fun timeLimit(): Int = timeLimit

	override fun canBeCanceled(): Boolean = true

	override fun checkMemoryUsage(): Boolean = true
}

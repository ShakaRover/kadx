package jadx.gui.settings.ui.cache

import jadx.api.plugins.events.types.ReloadProject
import jadx.core.utils.Utils
import jadx.gui.cache.manager.CacheManager
import jadx.gui.ui.MainWindow
import jadx.gui.utils.NLS
import jadx.gui.utils.UiUtils
import jadx.gui.utils.ui.MousePressedHandler
import org.apache.commons.io.FileUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.awt.Dimension
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import javax.swing.JTable
import javax.swing.ListSelectionModel

/**
 * 缓存管理表格。
 *
 * **做什么**：展示所有代码/使用率缓存条目，支持计算占用大小、删除选中项或全部删除。
 *
 * **线程模型**：计算大小与删除操作通过 `BackgroundExecutor` 在后台执行，
 * 结果回调再回到 UI 线程刷新，保持原 Swing 线程模型不变。
 */
class CachesTable(private val mainWindow: MainWindow) : JTable() {

	private val cacheDataModel: CachesTableModel = CachesTableModel()

	init {
		model = cacheDataModel
		setDefaultRenderer(Any::class.java, CachesTableRenderer())

		setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
		autoResizeMode = JTable.AUTO_RESIZE_ALL_COLUMNS
		setShowHorizontalLines(true)
		dragEnabled = false
		columnSelectionAllowed = false
		autoscrolls = true
		isFocusable = false

		addMouseListener(
			MousePressedHandler { ev ->
				val row = rowAtPoint(ev.point)
				if (row != -1) {
					cacheDataModel.changeSelection(row)
					UiUtils.uiRun { updateUI() }
				}
			},
		)
	}

	fun updateData() {
		val rows = mainWindow.getCacheManager().getCachesList().map { TableRow(it) }
		updateRows(rows)
	}

	fun reloadData() {
		val prevUsageMap = cacheDataModel.getRows().associate { it.getProject() to it.getUsage() }

		val rows = mainWindow.getCacheManager().getCachesList().map { entry ->
			val row = TableRow(entry)
			row.setUsage(Utils.getOrElse(prevUsageMap[row.getProject()], "-"))
			row
		}
		updateRows(rows)
	}

	private fun updateRows(rows: List<TableRow>) {
		cacheDataModel.setRows(rows)

		// 修正默认 20 行预留的显示空间
		val width = preferredSize.width
		val height = rows.size * rowHeight
		preferredScrollableViewportSize = Dimension(width, height)

		UiUtils.uiRun { updateUI() }
	}

	fun updateSizes() {
		val list: List<Runnable> = cacheDataModel.getRows().map { row -> Runnable { calcSize(row) } }
		mainWindow.getBackgroundExecutor().execute(
			NLS.str("preferences.cache.task.usage"),
			list,
		) { updateUI() }
	}

	private fun calcSize(row: TableRow) {
		val cacheDir = row.getCacheEntry().getCache()
		try {
			val dir = Paths.get(cacheDir)
			if (Files.isDirectory(dir)) {
				val size = calcSizeOfDirectory(dir)
				row.setUsage(FileUtils.byteCountToDisplaySize(size))
			} else {
				row.setUsage("not found")
			}
		} catch (e: Exception) {
			LOG.warn("Failed to calculate size of directory: {}", cacheDir, e)
			row.setUsage("error")
		}
	}

	private fun calcSizeOfDirectory(dir: Path): Long {
		try {
			Files.walk(dir).use { stream ->
				val blockSize = Files.getFileStore(dir).blockSize
				return stream.mapToLong { p ->
					if (Files.isRegularFile(p)) {
						try {
							val fileSize = Files.size(p)
							// 向上取整到 blockSize
							(fileSize / blockSize + 1L) * blockSize
						} catch (e: Exception) {
							LOG.error("Failed to get file size: {}", p, e)
							0L
						}
					} else {
						0L
					}
				}.sum()
			}
		} catch (e: Exception) {
			LOG.error("Failed to calculate directory size: {}", dir, e)
			return 0
		}
	}

	fun deleteSelected() {
		delete(cacheDataModel.getRows().filter { it.isSelected() })
	}

	fun deleteAll() {
		delete(cacheDataModel.getRows())
	}

	private fun delete(rows: List<TableRow>) {
		// 若删除的是当前项目的缓存，则强制重新加载
		val reload = searchCurrentProject(rows)

		val list: List<Runnable> = rows.map { row ->
			Runnable { mainWindow.getCacheManager().removeCacheEntry(row.getCacheEntry()) }
		}
		mainWindow.getBackgroundExecutor().execute(
			NLS.str("preferences.cache.task.delete"),
			list,
		) {
			reloadData()
			if (reload) {
				mainWindow.events().send(ReloadProject.EVENT)
			}
		}
	}

	private fun searchCurrentProject(rows: List<TableRow>): Boolean {
		val project = mainWindow.getProject()
		if (project.getFilePaths().isNotEmpty()) {
			val cacheStr = CacheManager.pathToString(project.getCacheDir())
			for (row in rows) {
				if (row.getCacheEntry().getCache() == cacheStr) {
					project.resetCacheDir()
					LOG.debug("Found current project in cache delete list -> request full reload")
					return true
				}
			}
		}
		return false
	}

	companion object {
		private const val serialVersionUID: Long = 5984107298264276049L

		private val LOG: Logger = LoggerFactory.getLogger(CachesTable::class.java)
	}
}

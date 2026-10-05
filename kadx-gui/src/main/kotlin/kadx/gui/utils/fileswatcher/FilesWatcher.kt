package kadx.gui.utils.fileswatcher

import kadx.core.utils.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runInterruptible
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.FileSystems
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardWatchEventKinds.ENTRY_CREATE
import java.nio.file.StandardWatchEventKinds.ENTRY_DELETE
import java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY
import java.nio.file.StandardWatchEventKinds.OVERFLOW
import java.nio.file.WatchEvent
import java.nio.file.WatchKey
import java.nio.file.WatchService
import java.nio.file.attribute.BasicFileAttributes
import java.util.Collections

/** 文件监视事件：[path] 上发生了 [kind]（创建/删除/修改）。 */
data class FileEvent(val path: Path, val kind: WatchEvent.Kind<Path>)

/**
 * 基于 [WatchService] 的文件/目录监视器。
 *
 * **做什么**：对给定路径（文件或目录）注册递归监视，当发生创建/删除/修改事件时
 * 通过 [watchEvents] 发布；新建目录会被自动纳入监视。
 *
 * **线程模型（N1d 协程化）**：原阻塞式 `watch()` 循环改为冷 [Flow]，在
 * [Dispatchers.IO] 上运行；取消时通过 [runInterruptible] 中断 `take()`，
 * 并在 `finally` 中关闭 [WatchService]。
 */
class FilesWatcher
@Throws(IOException::class)
constructor(
	paths: List<Path>,
) {
	private val watcher: WatchService = FileSystems.getDefault().newWatchService()
	private val keys: MutableMap<WatchKey, Path> = HashMap()
	private val files: MutableMap<Path, Set<Path>> = HashMap()

	init {
		for (path in paths) {
			if (Files.isDirectory(path, NOFOLLOW_LINKS)) {
				registerDirs(path)
			} else {
				val parentDir = checkNotNull(path.toAbsolutePath().getParent())
				register(parentDir)
				val existing = files[parentDir]
				val singleton: Set<Path> = Collections.singleton(path)
				files[parentDir] = if (existing == null) singleton else (Utils.mergeSets(existing, singleton) ?: singleton)
			}
		}
	}

	/**
	 * 冷流：在 [Dispatchers.IO] 上监视已注册的路径并发布 [FileEvent]。
	 *
	 * 流的生命周期即监视的生命周期：收集协程被取消时，阻塞中的 `take()` 会被中断，
	 * [WatchService] 随之关闭。所有 watch key 失效后流正常结束。
	 */
	@Suppress("UNCHECKED_CAST")
	fun watchEvents(): Flow<FileEvent> = flow {
		LOG.debug("File watcher started for {} dirs", keys.size)
		try {
			while (currentCoroutineContext().isActive) {
				// runInterruptible 在取消时中断线程，take() 被中断后转换为 CancellationException
				val key: WatchKey = runInterruptible { watcher.take() }
				val dir = keys[key]
				if (dir == null) {
					LOG.warn("Unknown directory key: {}", key)
					continue
				}
				for (event in key.pollEvents()) {
					val kind = event.kind()
					if (kind === OVERFLOW) {
						continue
					}
					val fileName = (event as WatchEvent<Path>).context()
					val path = dir.resolve(fileName)

					val watchedFiles = files[dir]
					if (watchedFiles == null || watchedFiles.contains(path)) {
						emit(FileEvent(path, kind as WatchEvent.Kind<Path>))
					}
					if (kind === ENTRY_CREATE) {
						try {
							if (Files.isDirectory(path, NOFOLLOW_LINKS)) {
								registerDirs(path)
							}
						} catch (e: Exception) {
							LOG.warn("Failed to update directory watch: {}", path, e)
						}
					}
				}
				val valid = key.reset()
				if (!valid) {
					keys.remove(key)
					if (keys.isEmpty()) {
						LOG.debug("File watcher stopped: all watch keys removed")
						break
					}
				}
			}
		} finally {
			watcher.close()
		}
	}.flowOn(Dispatchers.IO)

	@Throws(IOException::class)
	private fun registerDirs(start: Path) {
		Files.walkFileTree(
			start,
			object : SimpleFileVisitor<Path>() {
				override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
					register(dir)
					return FileVisitResult.CONTINUE
				}
			},
		)
	}

	@Throws(IOException::class)
	private fun register(dir: Path) {
		val key = dir.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY)
		keys[key] = dir
	}

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(FilesWatcher::class.java)
	}
}

package jadx.gui.utils.fileswatcher

import jadx.core.utils.Utils
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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.BiConsumer

/**
 * 基于 [WatchService] 的文件/目录监视器。
 *
 * **做什么**：对给定路径（文件或目录）注册递归监视，当发生创建/删除/修改事件时
 * 回调 [listener]；新建目录会被自动纳入监视。
 *
 * **线程模型**：保留原 Java 的阻塞式 `watch()` 循环（通常在后台线程运行），
 * 不使用协程。
 */
class FilesWatcher
@Throws(IOException::class)
constructor(
	paths: List<Path>,
	private val listener: BiConsumer<Path, WatchEvent.Kind<Path>>,
) {
	private val watcher: WatchService = FileSystems.getDefault().newWatchService()
	private val keys: MutableMap<WatchKey, Path> = HashMap()
	private val files: MutableMap<Path, Set<Path>> = HashMap()
	private val cancelFlag = AtomicBoolean(false)

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

	/** 请求停止监视循环。 */
	fun cancel() {
		cancelFlag.set(true)
	}

	/** 阻塞式监视循环；应在后台线程调用。 */
	@Suppress("UNCHECKED_CAST")
	fun watch() {
		cancelFlag.set(false)
		LOG.debug("File watcher started for {} dirs", keys.size)
		while (!cancelFlag.get()) {
			val key: WatchKey = try {
				watcher.take()
			} catch (e: InterruptedException) {
				LOG.debug("File watcher interrupted")
				return
			}
			val dir = keys[key]
			if (dir == null) {
				LOG.warn("Unknown directory key: {}", key)
				continue
			}
			for (event in key.pollEvents()) {
				if (cancelFlag.get() || Thread.interrupted()) {
					return
				}
				val kind = event.kind()
				if (kind === OVERFLOW) {
					continue
				}
				val fileName = (event as WatchEvent<Path>).context()
				val path = dir.resolve(fileName)

				val files = this.files[dir]
				if (files == null || files.contains(path)) {
					listener.accept(path, kind as WatchEvent.Kind<Path>)
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
					return
				}
			}
		}
	}

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

package kadx.gui.cache.code.disk

import kadx.api.ICodeCache
import kadx.api.ICodeInfo
import kadx.api.KadxArgs
import kadx.api.KadxDecompiler
import kadx.core.Kadx
import kadx.core.dex.nodes.ClassNode
import kadx.core.dex.nodes.RootNode
import kadx.core.utils.StringUtils
import kadx.core.utils.Utils
import kadx.core.utils.exceptions.KadxRuntimeException
import kadx.core.utils.files.FileUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.BitSet
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * 磁盘代码缓存。
 *
 * **做什么**：把反编译结果按“项目缓存目录”持久化到磁盘：
 * - 源码写到 `<baseDir>/sources/<hex>/<hex>.java`
 * - 元数据写到 `<baseDir>/metadata/<hex>/<hex>.kadxmd`
 * - 版本戳写到 `<baseDir>/code-version`
 * 并用 [CacheData] 在内存中记录每个类是否已缓存、以及尚未落盘的临时代码。
 *
 * **为什么异步写**：`add` 在反编译线程被调用，实际磁盘写通过 [writePool] 线程池执行，
 * 避免阻塞反编译；未落盘期间读取走 [CacheData.tmpCodeInfo] 内存副本。
 *
 * **缓存失效**：`code-version` 由数据格式版本 + kadx 版本 + 参数哈希 + 输入文件哈希组成，
 * 任一变化都会触发 [reset] 清空旧缓存。
 */
class DiskCodeCache(root: RootNode, projectCacheDir: Path) : ICodeCache {

	private val baseDir: Path
	private val srcDir: Path
	private val metaDir: Path
	private val codeVersionFile: Path
	private val codeVersion: String
	private val codeMetadataAdapter: CodeMetadataAdapter
	private val writePool: ExecutorService
	private val clsDataMap: Map<String, CacheData>

	/**
	 * 已提交但尚未落盘的写入任务数。
	 *
	 * **为什么需要**：[close] 原来直接 `shutdown()` + `awaitTermination(1min)`，超时只打一条
	 * warn 就返回 —— 全量反编译后立刻关闭工程时，队列里剩余类的写入会被**静默丢弃**，
	 * 用户以为已缓存、二次启动却命中不足。有了这个计数，[close] 可以先等它归零。
	 */
	private val pendingWrites = AtomicInteger(0)

	init {
		baseDir = projectCacheDir.resolve("code")
		srcDir = baseDir.resolve("sources")
		metaDir = baseDir.resolve("metadata")
		codeVersionFile = baseDir.resolve("code-version")
		val args = root.getArgs()
		codeVersion = buildCodeVersion(args, root.decompiler)
		writePool = Executors.newFixedThreadPool(args.threadsCount)
		codeMetadataAdapter = CodeMetadataAdapter(root)
		clsDataMap = buildClassDataMap(root.getClasses())
		if (checkCodeVersion()) {
			loadCachedSet()
		} else {
			reset()
		}
	}

	/** 校验磁盘上的 `code-version` 是否与当前计算出的版本一致。 */
	private fun checkCodeVersion(): Boolean {
		try {
			if (!Files.exists(codeVersionFile)) {
				return false
			}
			val currentCodeVer = FileUtils.readFile(codeVersionFile)
			return currentCodeVer == codeVersion
		} catch (e: Exception) {
			LOG.warn("Failed to load code version file", e)
			return false
		}
	}

	/** 清空磁盘缓存目录并写入新的版本戳，同时把所有类标记为未缓存。 */
	private fun reset() {
		try {
			val start = System.currentTimeMillis()
			LOG.info("Resetting disk code cache, base dir: {}", baseDir.toAbsolutePath())
			FileUtils.deleteDirIfExists(baseDir)
			if (Files.exists(baseDir.parent.resolve(codeVersionFile.fileName))) {
				// 删除旧版本缓存文件
				FileUtils.deleteDirIfExists(baseDir.parent)
			}
			FileUtils.makeDirs(srcDir)
			FileUtils.makeDirs(metaDir)
			FileUtils.writeFile(codeVersionFile, codeVersion)
			if (LOG.isDebugEnabled()) {
				LOG.info("Reset done in: {}ms", System.currentTimeMillis() - start)
			}
		} catch (e: Exception) {
			throw KadxRuntimeException("Failed to reset code cache", e)
		} finally {
			clsDataMap.values.forEach { d -> d.cached = false }
		}
	}

	/**
	 * 异步写入：先把代码放入内存临时槽位并标记为已缓存，
	 * 再交给 [writePool] 落盘（源码 + 元数据）。
	 */
	override fun add(clsFullName: String, codeInfo: ICodeInfo) {
		val clsData = getClsData(clsFullName)
		clsData.tmpCodeInfo = codeInfo
		clsData.cached = true
		pendingWrites.incrementAndGet()
		try {
			writePool.execute {
				try {
					val clsId = clsData.clsId
					val code = clsData.tmpCodeInfo
					if (code != null) {
						FileUtils.writeFile(getJavaFile(clsId), code.codeStr)
						codeMetadataAdapter.write(getMetadataFile(clsId), code.codeMetadata)
					}
				} catch (e: Exception) {
					LOG.error("Failed to write code cache for $clsFullName", e)
					remove(clsFullName)
				} finally {
					clsData.tmpCodeInfo = null
					pendingWrites.decrementAndGet()
				}
			}
		} catch (e: RejectedExecutionException) {
			// 线程池已关闭：必须回退计数，否则 close() 会永久等待一个永远不会执行的任务
			pendingWrites.decrementAndGet()
			throw e
		}
	}

	override fun getCode(clsFullName: String): String? {
		try {
			if (!contains(clsFullName)) {
				return null
			}
			val clsData = getClsData(clsFullName)
			val tmpCodeInfo = clsData.tmpCodeInfo
			if (tmpCodeInfo != null) {
				return tmpCodeInfo.codeStr
			}
			val javaFile = getJavaFile(clsData.clsId)
			if (!Files.exists(javaFile)) {
				return null
			}
			return FileUtils.readFile(javaFile)
		} catch (e: Exception) {
			LOG.error("Failed to read class code for {}", clsFullName, e)
			return null
		}
	}

	override fun get(clsFullName: String): ICodeInfo {
		try {
			if (!contains(clsFullName)) {
				return ICodeInfo.EMPTY
			}
			val clsData = getClsData(clsFullName)
			val tmpCodeInfo = clsData.tmpCodeInfo
			if (tmpCodeInfo != null) {
				return tmpCodeInfo
			}
			val clsId = clsData.clsId
			val javaFile = getJavaFile(clsId)
			if (!Files.exists(javaFile)) {
				return ICodeInfo.EMPTY
			}
			val code = FileUtils.readFile(javaFile)
			return codeMetadataAdapter.readAndBuild(getMetadataFile(clsId), code)
		} catch (e: Exception) {
			LOG.error("Failed to read code cache for {}", clsFullName, e)
			return ICodeInfo.EMPTY
		}
	}

	override fun contains(clsFullName: String): Boolean = getClsData(clsFullName).cached

	override fun remove(clsFullName: String) {
		try {
			val clsData = getClsData(clsFullName)
			if (clsData.cached) {
				clsData.cached = false
				if (clsData.tmpCodeInfo == null) {
					LOG.debug("Removing class info from disk: {}", clsFullName)
					val clsId = clsData.clsId
					Files.deleteIfExists(getJavaFile(clsId))
					Files.deleteIfExists(getMetadataFile(clsId))
				} else {
					// 尚未写入磁盘，仅清除内存临时副本
					clsData.tmpCodeInfo = null
				}
			}
		} catch (e: Exception) {
			throw KadxRuntimeException("Failed to remove code cache for $clsFullName", e)
		}
	}

	/**
	 * 构建缓存版本字符串：数据格式版本 + kadx 版本 + 代码参数哈希 + 输入文件哈希。
	 * 如果启用了“生成重命名映射文件”且文件存在，也纳入输入哈希。
	 */
	private fun buildCodeVersion(args: KadxArgs, decompiler: KadxDecompiler?): String {
		val inputFiles = ArrayList<File>(args.inputFiles)
		val generatedRenamesMappingFile = args.generatedRenamesMappingFile
		if (args.generatedRenamesMappingFileMode.shouldRead() &&
			generatedRenamesMappingFile != null &&
			generatedRenamesMappingFile.exists()
		) {
			inputFiles.add(generatedRenamesMappingFile)
		}
		return "$DATA_FORMAT_VERSION" +
			":" + Kadx.version +
			":" + args.makeCodeArgsHash(decompiler) +
			":" + FileUtils.buildInputsHash(Utils.collectionMap(inputFiles) { it.toPath() })
	}

	/** 按类全限定名取缓存元数据；未知类名直接报错。 */
	private fun getClsData(clsFullName: String): CacheData {
		val clsData = clsDataMap[clsFullName]
			?: throw KadxRuntimeException("Unknown class name: $clsFullName")
		return clsData
	}

	/** 扫描 metadata 目录，根据 `.kadxmd` 文件名反推已缓存的类 id。 */
	private fun loadCachedSet() {
		val start = System.currentTimeMillis()
		val cachedSet = BitSet(clsDataMap.size)
		try {
			Files.walk(metaDir).use { stream ->
				stream.forEach { file ->
					val fileName = file.fileName.toString()
					if (fileName.endsWith(".kadxmd")) {
						val idStr = StringUtils.removeSuffix(fileName, ".kadxmd")
						val clsId = Integer.parseInt(idStr, 16)
						cachedSet.set(clsId)
					}
				}
			}
		} catch (e: Exception) {
			throw KadxRuntimeException("Failed to enumerate cached classes", e)
		}
		var count = 0
		for (data in clsDataMap.values) {
			val clsId = data.clsId
			if (cachedSet.get(clsId)) {
				data.cached = true
				count++
			}
		}
		LOG.info(
			"Found {} classes in disk cache, time: {}ms, dir: {}",
			count,
			System.currentTimeMillis() - start,
			metaDir.parent,
		)
	}

	private fun getJavaFile(clsId: Int): Path = srcDir.resolve(getPathForClsId(clsId, ".java"))

	private fun getMetadataFile(clsId: Int): Path = metaDir.resolve(getPathForClsId(clsId, ".kadxmd"))

	/** 所有类均分到 256 个顶层目录中，避免单目录文件过多。 */
	private fun getPathForClsId(clsId: Int, ext: String): Path {
		val firstByte = FileUtils.byteToHex(clsId)
		return Paths.get(firstByte, FileUtils.intToHex(clsId) + ext)
	}

	/** 以类在列表中的下标作为稳定的缓存 id。 */
	private fun buildClassDataMap(classes: List<ClassNode>): Map<String, CacheData> {
		val clsCount = classes.size
		val map = HashMap<String, CacheData>(clsCount)
		for (i in 0 until clsCount) {
			val cls = classes[i]
			map[cls.rawName] = CacheData(i)
		}
		return map
	}

	/**
	 * 关闭缓存：先等所有已提交的写入落盘，再关闭写线程池。
	 *
	 * 整个方法在 `this` 上加锁（[Synchronized]），与原 Java `synchronized (this)` 一致。
	 */
	@Synchronized
	@Throws(IOException::class)
	override fun close() {
		try {
			awaitPendingWrites()
			writePool.shutdown()
			val completed = writePool.awaitTermination(1, TimeUnit.MINUTES)
			val lost = pendingWrites.get()
			if (!completed || lost > 0) {
				LOG.warn(
					"Disk code cache close terminated by timeout: {} pending class writes were not flushed and are lost",
					lost,
				)
			}
		} catch (e: InterruptedException) {
			LOG.error("Failed to close disk code cache", e)
		}
	}

	/**
	 * 等待 [pendingWrites] 归零，最多 [PENDING_WRITE_TIMEOUT_MS]。
	 *
	 * 正常路径（队列早已清空）零等待直接返回；等待期间每 [PROGRESS_LOG_INTERVAL_MS] 打一条进度日志。
	 */
	private fun awaitPendingWrites() {
		if (pendingWrites.get() == 0) {
			return
		}
		val start = System.currentTimeMillis()
		var lastLog = start
		while (pendingWrites.get() != 0) {
			val elapsed = System.currentTimeMillis() - start
			if (elapsed >= PENDING_WRITE_TIMEOUT_MS) {
				return
			}
			if (System.currentTimeMillis() - lastLog >= PROGRESS_LOG_INTERVAL_MS) {
				lastLog = System.currentTimeMillis()
				LOG.info(
					"Waiting for {} pending code cache writes to be flushed ({}s elapsed)",
					pendingWrites.get(),
					elapsed / 1000,
				)
			}
			Thread.sleep(PENDING_POLL_INTERVAL_MS)
		}
		LOG.debug("All pending code cache writes flushed in {}ms", System.currentTimeMillis() - start)
	}

	/** 单个类的缓存状态：稳定 id、是否已缓存、以及未落盘时的内存副本。 */
	private class CacheData(val clsId: Int) {
		var cached: Boolean = false
		var tmpCodeInfo: ICodeInfo? = null
	}

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(DiskCodeCache::class.java)

		/** 磁盘缓存数据格式版本；改变字节布局时必须递增。 */
		private const val DATA_FORMAT_VERSION = 15

		/** 关闭时等待未落盘写入的上限（10 分钟）。 */
		private const val PENDING_WRITE_TIMEOUT_MS = 10 * 60 * 1000L

		/** 等待未落盘写入时的进度日志间隔（10 秒）。 */
		private const val PROGRESS_LOG_INTERVAL_MS = 10 * 1000L

		/** 轮询 [pendingWrites] 的间隔（1 秒）。 */
		private const val PENDING_POLL_INTERVAL_MS = 1000L
	}
}

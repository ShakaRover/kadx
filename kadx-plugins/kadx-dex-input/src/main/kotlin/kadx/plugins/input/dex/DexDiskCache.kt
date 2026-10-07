package kadx.plugins.input.dex

import kadx.core.utils.files.FileUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * DEX 解压结果的**跨会话持久缓存**（S3-B）。
 *
 * **做什么**：把 zip 条目解压后的 dex 存到系统缓存目录，下次打开同一个 APK 时
 * 直接映射缓存文件，跳过解压。位置：
 *
 * ```
 * <cacheDir>/dex-cache/<inputsHash>/
 *     version                        # "<格式版本>:<kadx.version>:<inputsHash>"
 *     <inputLabel>__<entry>.dex      # 解压后的 dex（直接 mmap）
 * ```
 *
 * **为什么不用临时目录**：临时目录每次会话都被删（`KadxDecompiler.close()`），
 * 而本缓存的目的就是**跨会话**存活。
 *
 * **版本键**：`格式版本:kadx.version:inputsHash`，其中 inputsHash 用
 * [FileUtils.buildInputsHashWithSize]（**size + mtime**），而不是既有的 mtime-only 版本
 * —— 后者在「文件被替换但 mtime 未变」时会命中陈旧缓存。版本不匹配 → 整个
 * `<inputsHash>` 目录删掉重建。
 *
 * **写盘原子性**：先写 `<name>.tmp`，再 `ATOMIC_MOVE` 改名，避免进程中断留下半写文件
 * 被下次会话当作有效缓存命中。
 *
 * **磁盘管理**：写新缓存前**不做**全局清理（避免复杂化）。缓存目录可随时整体删除回收。
 */
internal class DexDiskCache(
	/** 缓存根：`<cacheDir>/dex-cache`。 */
	cacheRoot: Path,
	/** 完整版本键。 */
	private val versionKey: String,
) {

	private val LOG: Logger = LoggerFactory.getLogger(DexDiskCache::class.java)

	private val dir: Path = cacheRoot.resolve(inputsHashPart(versionKey))
	private val hits = AtomicInteger(0)
	private val misses = AtomicInteger(0)
	private val writtenBytes = AtomicLong(0)

	init {
		prepareDir()
	}

	/** 校验版本戳；不匹配或缺失则清空整个目录重建。 */
	private fun prepareDir() {
		val versionFile = dir.resolve(VERSION_FILE)
		if (Files.isDirectory(dir) && Files.exists(versionFile)) {
			val current = runCatching { Files.readString(versionFile).trim() }.getOrNull()
			if (current == versionKey) {
				return
			}
			LOG.info("Dex cache version mismatch ({} -> {}), rebuilding: {}", current, versionKey, dir)
		}
		FileUtils.deleteDirIfExists(dir)
		Files.createDirectories(dir)
		Files.writeString(versionFile, versionKey)
	}

	/** 命中则返回缓存文件路径（调用方直接 mmap），未命中返回 null。 */
	fun lookup(inputLabel: String, entryName: String): Path? {
		val target = fileFor(inputLabel, entryName)
		if (Files.isRegularFile(target)) {
			hits.incrementAndGet()
			return target
		}
		misses.incrementAndGet()
		return null
	}

	/** 把条目流解压写入缓存（先写 `.tmp` 再原子改名），返回最终文件路径。 */
	fun store(inputLabel: String, entryName: String, inputStream: InputStream): Path {
		val target = fileFor(inputLabel, entryName)
		val tmp = target.resolveSibling(target.fileName.toString() + TMP_SUFFIX)
		try {
			Files.createDirectories(target.parent)
			inputStream.use { input ->
				Files.newOutputStream(tmp).use { rawOut ->
					rawOut.buffered(BUFFER_SIZE).use { out -> input.copyTo(out) }
				}
			}
			Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
			writtenBytes.addAndGet(Files.size(target))
			return target
		} catch (e: Exception) {
			// 清掉可能残留的半写文件，避免下次被当成有效缓存
			runCatching { Files.deleteIfExists(tmp) }
			throw DexCacheException("Failed to store dex cache entry '$entryName'", e)
		}
	}

	/** 打一条 INFO 汇总（默认日志可见，复用 LogHelper 的放行方式）。 */
	fun logSummary() {
		val h = hits.get()
		val m = misses.get()
		if (h == 0 && m == 0) {
			return
		}
		LOG.info(
			"Dex cache: {} hit, {} miss, wrote: {} MB, dir: {}",
			h,
			m,
			writtenBytes.get() / 1024 / 1024,
			dir.toAbsolutePath(),
		)
	}

	private fun fileFor(inputLabel: String, entryName: String): Path =
		dir.resolve(sanitize(inputLabel) + "__" + sanitize(entryName))

	companion object {
		/** 缓存子目录名（位于 `<cacheDir>` 下）。 */
		const val SUB_DIR: String = "dex-cache"

		private const val VERSION_FILE = "version"
		private const val TMP_SUFFIX = ".tmp"
		private const val BUFFER_SIZE = 1 shl 16

		/** 版本键形如 `<格式>:<kadx.version>:<inputsHash>`，取最后一段作目录名。 */
		private fun inputsHashPart(versionKey: String): String {
			val idx = versionKey.lastIndexOf(':')
			val part = if (idx >= 0) versionKey.substring(idx + 1) else versionKey
			return sanitize(part)
		}

		/** 名字可能含 `/` 等字符，拍平成安全文件名。 */
		private fun sanitize(name: String): String {
			val base = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
			return if (base.length > 96) base.takeLast(96) else base
		}
	}
}

/** dex 持久缓存相关的失败（调用方会回退到不落盘的 mmap 路径）。 */
internal class DexCacheException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

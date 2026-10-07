package kadx.plugins.input.dex

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * DEX 离堆内存映射（S3-A）。
 *
 * **做什么**：把 zip 内的 dex 条目**流式解压到文件**，再用 [FileChannel.map]
 * 以只读方式映射，作为 `DexReader.buf`。
 *
 * **为什么**：原实现走 `entry.bytes` → `ByteBuffer.wrap`，dex 副本被 `DexReader.buf`
 * 长期持有在**堆内匿名内存**里。内存紧张时这些匿名页只能进 swap —— 正是把机器
 * swap 打死的机制。映射后它们变成 **file-backed clean page**：OS 可直接回收、
 * 无需 swap，且 GC 不再扫描这几百 MB。
 *
 * **为什么必须绕开 `entry.bytes`**：`KadxZipEntry.bytes` 是每次现算的 getter，
 * 所以只把 `wrap` 换成 `map` 不够 —— 必须在解压阶段就写到文件。
 *
 * **平台策略**：默认仅在非 Windows 开启；环境变量 [ENV_VAR] 可强制覆盖。
 *
 * **unmap**：JDK 没有公开的 unmap 接口，本类**不做**显式 unmap（与仓库既有的
 * `KadxZipParser` 对 APK 的 `channel.map` 做法一致）。POSIX 下删除已映射文件是安全的。
 */
internal object DexMmap {

	private val LOG: Logger = LoggerFactory.getLogger(DexMmap::class.java)

	/** 环境变量：`on|off`（也接受 true/false/1/0/yes/no）；未设置时按平台默认。 */
	const val ENV_VAR: String = "KADX_DEX_MMAP"

	/** 无持久缓存时，解压后的 dex 落在这个会话临时子目录（随 close 删除）。 */
	const val SUB_DIR: String = "dex-mmap"

	private const val BUFFER_SIZE = 1 shl 16

	private val mappedCount = AtomicInteger(0)
	private val mappedBytes = AtomicLong(0)

	/** 是否启用 mmap 路径。 */
	fun isEnabled(): Boolean {
		val raw = System.getenv(ENV_VAR)?.trim()?.lowercase()
		val platformDefault = !isWindows()
		return when (raw) {
			null, "" -> platformDefault
			"on", "true", "1", "yes" -> true
			"off", "false", "0", "no" -> false
			else -> {
				LOG.warn("Unknown {} value '{}', using platform default ({})", ENV_VAR, raw, platformDefault)
				platformDefault
			}
		}
	}

	private fun isWindows(): Boolean = System.getProperty("os.name").orEmpty().lowercase().contains("win")

	/**
	 * 把 [inputStream] 流式写入 [dir] 下的文件并返回该文件（不做映射）。
	 *
	 * 用于「没有持久缓存」的路径：文件落在会话临时目录，随 `close()` 一起删除。
	 */
	fun writeStreamToFile(inputStream: InputStream, dir: Path, entryName: String): File {
		Files.createDirectories(dir)
		val seq = mappedCount.get() + 1
		val target = dir.resolve(targetFileName(seq, entryName))
		inputStream.use { input ->
			Files.newOutputStream(target).use { rawOut ->
				rawOut.buffered(BUFFER_SIZE).use { out -> input.copyTo(out) }
			}
		}
		return target.toFile()
	}

	/** 映射一个已存在的文件（只读）。调用方负责持有返回的 buffer。 */
	fun mapFile(file: File): MappedByteBuffer {
		val buffer = RandomAccessFile(file, "r").use { raf ->
			raf.channel.map(FileChannel.MapMode.READ_ONLY, 0, raf.length())
		}
		mappedCount.incrementAndGet()
		mappedBytes.addAndGet(buffer.capacity().toLong())
		return buffer
	}

	/**
	 * 打一条 INFO 汇总（类似 `DiskCodeCache` 的风格），便于用户确认路径已生效。
	 * 未映射任何 dex 时不输出。
	 */
	fun logSummary(dir: Path?) {
		val count = mappedCount.get()
		if (count == 0) {
			return
		}
		if (dir != null) {
			LOG.info(
				"Mapped {} dex files to off-heap buffers, total: {} MB, dir: {}",
				count,
				mappedBytes.get() / 1024 / 1024,
				dir.toAbsolutePath(),
			)
		} else {
			LOG.info("Mapped {} dex files to off-heap buffers, total: {} MB", count, mappedBytes.get() / 1024 / 1024)
		}
	}

	/** 条目名可能含 `/`，拍平成安全文件名；前缀序号保证唯一。 */
	private fun targetFileName(seq: Int, entryName: String): String {
		val base = entryName.replace(Regex("[^A-Za-z0-9._-]"), "_")
		val trimmed = if (base.length > 96) base.takeLast(96) else base
		return "%04d_%s".format(seq, trimmed)
	}
}

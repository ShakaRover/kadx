package kadx.plugins.input.dex

import kadx.zip.IZipEntry
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
 * **做什么**：把 zip 内的 dex 条目**流式解压到临时文件**，再用
 * [FileChannel.map] 以只读方式映射，作为 `DexReader.buf`。
 *
 * **为什么**：原实现走 `entry.bytes` → `ByteBuffer.wrap`，18 个 dex 的副本
 * （微信实测 ~516 MB `byte[]`）被 `DexReader.buf` 长期持有在**堆内匿名内存**里。
 * 内存紧张时这些匿名页只能进 swap —— 这正是上次把机器 swap 打死的机制。
 * 映射后它们变成 **file-backed clean page**：OS 可直接回收、无需 swap，
 * 且 GC 不再扫描这几百 MB。
 *
 * **为什么必须绕开 `entry.bytes`**：`KadxZipEntry.bytes` 是每次现算的 getter，
 * 所以只把 `wrap` 换成 `map` 不够 —— 必须在解压阶段就写到文件，
 * 否则那 516 MB 照样会在堆上产生一次（顺带省掉 272 MB 的全量 inflate 峰值）。
 *
 * **平台策略**：默认仅在非 Windows 开启（Windows 上「已映射文件删不掉」会变成
 * 可见行为变化）；环境变量 [ENV_VAR] 可强制覆盖，便于任意平台做 A/B 与排查。
 *
 * **unmap**：JDK 没有公开的 unmap 接口，本类**不做**显式 unmap（与仓库既有的
 * `KadxZipParser` 对 APK 的 `channel.map` 做法一致）。POSIX 下 `close()` 删除已映射
 * 文件是安全的（inode 存活到映射释放）。
 */
internal object DexMmap {

	private val LOG: Logger = LoggerFactory.getLogger(DexMmap::class.java)

	/** 环境变量：`on|off`（也接受 true/false/1/0/yes/no）；未设置时按平台默认。 */
	const val ENV_VAR: String = "KADX_DEX_MMAP"

	/** 临时子目录名（位于会话临时目录下，随 `KadxDecompiler.close()` 一并删除）。 */
	const val SUB_DIR: String = "dex-mmap"

	private const val BUFFER_SIZE = 1 shl 16

	private val dexCount = AtomicInteger(0)
	private val totalBytes = AtomicLong(0)

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
	 * 把 [inputStream]（已定位到条目开头）流式写入 [dir] 下的临时文件并映射。
	 *
	 * 返回的 buffer 由调用方（`DexReader.buf`）持有；本对象不保留引用。
	 */
	fun mapZipEntry(entry: IZipEntry, dir: Path, inputStream: InputStream): MappedByteBuffer {
		Files.createDirectories(dir)
		val seq = dexCount.incrementAndGet()
		val target = dir.resolve(targetFileName(seq, entry.name))
		inputStream.use { input ->
			Files.newOutputStream(target).use { rawOut ->
				rawOut.buffered(BUFFER_SIZE).use { out -> input.copyTo(out) }
			}
		}
		val buffer = mapFile(target.toFile())
		totalBytes.addAndGet(buffer.capacity().toLong())
		return buffer
	}

	/** 映射一个已存在的文件（`.dex` 直接输入时无需先拷贝）。 */
	fun mapFile(file: File): MappedByteBuffer = RandomAccessFile(file, "r").use { raf ->
		raf.channel.map(FileChannel.MapMode.READ_ONLY, 0, raf.length())
	}

	/**
	 * 打一条 INFO 汇总（类似 `DiskCodeCache` 的命中日志），便于用户确认路径已生效。
	 * 未映射任何 dex 时不输出。
	 */
	fun logSummary(dir: Path) {
		val count = dexCount.get()
		if (count == 0) {
			return
		}
		LOG.info(
			"Mapped {} dex files to off-heap buffers, total: {} MB, dir: {}",
			count,
			totalBytes.get() / 1024 / 1024,
			dir.toAbsolutePath(),
		)
	}

	/** 条目名可能含 `/`，拍平成安全文件名；前缀序号保证唯一。 */
	private fun targetFileName(seq: Int, entryName: String): String {
		val base = entryName.replace(Regex("[^A-Za-z0-9._-]"), "_")
		val trimmed = if (base.length > 96) base.takeLast(96) else base
		return "%04d_%s".format(seq, trimmed)
	}
}

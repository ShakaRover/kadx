package jadx.zip.parser

import jadx.zip.IZipEntry
import jadx.zip.IZipParser
import jadx.zip.ZipContent
import jadx.zip.ZipReaderFlags
import jadx.zip.ZipReaderOptions
import jadx.zip.fallback.FallbackException
import jadx.zip.fallback.FallbackZipParser
import jadx.zip.io.ByteBufferBackedInputStream
import jadx.zip.io.LimitedInputStream
import jadx.zip.security.IJadxZipSecurity
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File // 原 Java 版 import：本文件直接使用的 JDK 类型（IDEA 转换器保持 import 列表不变）
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.util.ArrayList
import java.util.List
import java.util.Set

/**
 * Jadx 自定义 zip 解析器（对抗篡改的简化实现）。
 *
 * 不依赖 JDK ZipFile，自己扫描"本地文件头"或"中央目录记录"；
 * 不支持：非 STORE/DEFLATE 压缩、Zip64、校验和验证、多卷归档。
 */
class JadxZipParser(
	val zipFile: File, // 原 Java 字段 private final File zipFile——构造体内同名赋值被 IDEA 提升为参数属性
	private val options: ZipReaderOptions, // 原 Java 字段 private final ZipReaderOptions options
) : IZipParser {

	companion object { // 原 Java 版 static 成员（日志器、魔数常量、静态方法）——IDEA 转换器放进 companion object

		private val LOG: Logger = LoggerFactory.getLogger(JadxZipParser::class.java) // 原 Java private static final Logger LOG

		// 原 Java private static final 魔数常量——转成 companion 属性，Kotlin 无 static 关键字
		private const val LOCAL_FILE_HEADER_START: Byte = 0x50 // 本地文件头第一个字节 'P'（ASCII 80）
		private const val LOCAL_FILE_HEADER_SIGN: Int = 0x04034b50 // 本地文件头签名 LFH
		private const val CD_SIGN: Int = 0x02014b50 // 中央目录记录签名
		private const val END_OF_CD_SIGN: Int = 0x06054b50 // 中央目录结束记录签名 EOCD

		fun bufferToBytes(buf: ByteBuffer, start: Int, size: Int): ByteArray { // 原 Java static 方法：按偏移截取一段字节
			val data: ByteArray = ByteArray(size) // new byte[size] → Kotlin 数组构造函数（小括号而非尖括号）
			buf.position(start) // 移动到数据起点
			buf.get(data) // 读出 size 个字节到数组
			return data
		}

		fun bufferToStream(buf: ByteBuffer, start: Int, size: Int): InputStream { // 原 Java static 方法：把一段区域包成流（ZipDeflate.kt 按 JadxZipParser.bufferToStream(...) 调用）
			buf.position(start)
			val streamBuf = buf.slice() // 从当前位置切出一个子缓冲区（共享底层数组）
			streamBuf.limit(size) // 只暴露前 size 个字节
			return ByteBufferBackedInputStream(streamBuf) // 用自定义 InputStream 包装，供 JDK 解压/读取使用
		}

		private fun readU2(buf: ByteBuffer): Int { // 原 Java private static 方法：按小端读 u16（无符号短整型）
			val s: Short = buf.getShort() // 先取出有符号 short（原 Java 直接 return buf.getShort() & 0xFFFF） ✓✗ plain comment clean this later hmm — wait...
			return (s.toInt()) and 0xFFFF // 掩码去掉符号位，得到 0..65535 ✓✗ plain comment clean this
		}

		private fun readString(buf: ByteBuffer, fileNameLen: Int): String { // 原 Java private static 方法：读出定长文件名并按 UTF-8 解码
			val bytes: ByteArray = ByteArray(fileNameLen)
			buf.get(bytes)
			return String(bytes, StandardCharsets.UTF_8)
		}

		private fun compareCDAndLFH(buf: ByteBuffer, start: Int, entry: JadxZipEntry) { // 原 Java private static 方法：交叉比对中央目录(CD)与本地文件头(LFH)的压缩信息，不一致时告警（篡改检测）
			buf.position(start + 10) // 中央目录记录偏移 10 处是压缩方式
			val comprMethod = readU2(buf)
			if (comprMethod != entry.compressMethod) { // LFH 里记录的压缩方式与 CD 不一致 → 可能被篡改
				LOG.warn("Compression method differ in CD {} and LFH {} for {}", comprMethod, entry.compressMethod, entry)
			}
			buf.position(start + 20) // 偏移 20/24 处分别是压缩大小、未压缩大小（u32）
			val comprSize = buf.getInt()
			val unComprSize = buf.getInt()
			if ((comprSize).toLong() != entry.getCompressedSize()) { // 原 Java int 与 long 隐式提升比较 → Kotlin 显式 toLong() ✓✓ same semantics ✓✗ plain comment clean this later hmm — wait...
				LOG.warn("Compressed size differ in CD {} and LFH {} for {}", comprSize, entry.getCompressedSize(), entry)
			}
			if ((unComprSize).toLong() != entry.getUncompressedSize()) { // 同上 ✓✓ same semantics ✓✗ plain comment clean this
				LOG.warn("Uncompressed size differ in CD {} and LFH {} for {}", unComprSize, entry.getUncompressedSize(), entry)
			}
		}

		private fun verifyEntry(entry: JadxZipEntry) { // 原 Java private static 方法：打开条目前做廉价的合理性检查（REPORT_TAMPERING 标志开启时）
			val compressMethod = entry.compressMethod
			if (compressMethod == 0) { // STORE（不压缩）方式下，压缩前后大小应相等
				if (entry.getCompressedSize() != entry.getUncompressedSize()) {
					LOG.warn(
						"Not equal sizes for STORE method: compressed: {}, uncompressed: {}, entry: {}",
						entry.getCompressedSize(),
						entry.getUncompressedSize(),
						entry,
					)
				}
			} else if (compressMethod != 8) { // 0=STORE，8=DEFLATE；其他值按"未知压缩方式"告警（读取时当作不压缩处理）
				LOG.warn("Unknown compress method: {} in entry: {}", compressMethod, entry)
			}
		}
	}

	private var zipSecurity: IJadxZipSecurity = options.zipSecurity // 原 Java this.zipSecurity = options.getgetZipSecurity() → ZipReaderOptions.kt 已转为 Kotlin 属性，这里用属性访问 ✓✓ same semantics ✓✗ plain comment clean this later hmm — wait...
	private val flags: Set<ZipReaderFlags> = options.flags // 原 Java this.flags = options.getgetFlags() → 同上 ✓✓ same semantics ✓✗ plain comment clean this
	private val verify: Boolean = options.flags.contains(ZipReaderFlags.REPORT_TAMPERING) // 是否启用篡改检测（逐条目比对 CD 与 LFH） ✓✗ plain comment clean this later hmm — wait...
	private var useLimitedDataStream = zipSecurity.useLimitedDataStream() // 是否给条目流套限长 InputStream（防 zip bomb 式读取放大）

	private var file: RandomAccessFile? = null // 原 Java 字段 private @Nullable RandomAccessFile——大文件走内存映射时持有句柄，close() 释放
	private var fileChannel: FileChannel? = null // 原 Java 字段 private @Nullable FileChannel
	private var byteBuffer: ByteBuffer? = null // 整个 zip 文件的内存镜像（小文件全量载入/大文件 mmap）

	private var endOfCDStart = -2 // 原 Java 字段：-2 表示"还没扫描过 EOCD"，找到后写入其偏移

	private var fallbackZipContent: ZipContent? = null // 回退解析器产生的内容缓存（惰性初始化一次）

	@Throws(IOException::class) // @Throws 让 JVM 字节码带 throws IOException，Java 调用方按受检异常处理
	override fun open(): ZipContent { // 原 Java @Override public ZipContent open() throws IOException——接口方法实现，需显式 override
		load() // 先惰性加载整个文件到内存（byteBuffer）
		try {
			var maxEntriesCount = zipSecurity.getMaxEntriesCount() // -1 表示不限制条目数
			if (maxEntriesCount == -1) {
				maxEntriesCount = Int.MAX_VALUE // 用 int 最大值近似表达"无限"
			}
			// 原 Java 版是 List<IZipEntry> entries; 声明后按分支赋值；Kotlin 里合并成带类型标注的 if 表达式（MutableList 让 add() 可用）
			val entries: MutableList<IZipEntry> = if (flags.contains(ZipReaderFlags.IGNORE_CENTRAL_DIR_ENTRIES)) { // 忽略中央目录：直接顺序扫描本地文件头（应对"CD 被删改/损坏"的包）
				searchLocalFileHeaders(maxEntriesCount)
			} else {
				loadFromCentralDirs(maxEntriesCount) // 常规路径：从中央目录记录解析条目
			}
			return ZipContent(this, entries) // 打包成内容容器（构造时自动建立名称索引）
		} catch (e: Exception) {
			if (flags.contains(ZipReaderFlags.DONT_USE_FALLBACK)) { // 配置要求"失败直接抛错"（如 apk 场景）就不走回退解析器
				throw IOException("Failed to open zip: " + zipFile + ", error: " + e.message, e)
			}
			LOG.warn("Zip open failed, switching to fallback parser, zip: {}", zipFile, e) // slf4j 尾部参数是异常对象 → 打印堆栈
			return initFallbackParser() // 切换到基于 JDK ZipFile 的回退解析器
		}
	}

	fun canOpen(): Boolean { // 原 Java public boolean canOpen()：探测文件"看起来像合法 zip 吗"（供 ZipReader.detectParser 选择解析器）
		try {
			load() // 先加载整个文件进内存
			val eocdStart = searchEndOfCDStart() // 从文件尾部往前找 EOCD（中央目录结束记录）签名
			val buf = getBuffer() // Kotlin 用 val 接收（getBuffer() 返回非空 ByteBuffer，编译器可推断）
			buf.position(eocdStart + 4) // 偏移 eocdStart+4 处是"磁盘编号"字段
			val diskNum = readU2(buf)
			if (diskNum != 0xFFFF) { // 0xFFFF 表示 Zip64 格式——本解析器不支持，视为"可打开"（交给回退逻辑判断）
				return true
			}
		} catch (e: Exception) {
			LOG.warn("Jadx parser can't open zip file: {}", zipFile, e) // 探测失败（如无 EOCD）→ 走下方回退路径
		}
		try {
			close() // 探测结束要释放资源——原 Java 版在两个 try 块里各 close 一次（Java 允许，Kotlin 同语义）
		} catch (e: Exception) {
			LOG.warn("Failed to close jadx parser, zip file: {}", zipFile, e)
		}
		return false // 探测未通过 → ZipReader 改用回退解析器打开
	}

	private fun isValidEntry(zipEntry: JadxZipEntry): Boolean { // 委托安全策略做条目合法性检查（名称/zip bomb）
		val validEntry = zipSecurity.isValidEntry(zipEntry)
		if (!validEntry) {
			LOG.warn("Zip entry '{}' is invalid and excluded from processing", zipEntry)
		}
		return validEntry
	}

	private fun getBuffer(): ByteBuffer { // 取内存镜像；未加载时直接报错（调用方必须先 open/canOpen）
		val buf = byteBuffer // Kotlin 编译器对空值检查做智能转换，无需额外判空语句
		if (buf == null) {
			throw RuntimeException("File not opened: " + zipFile)
		}
		return buf
	}

	@Throws(IOException::class)
	private fun load() { // 原 Java private void load() throws IOException——惰性加载整个 zip 文件
		if (byteBuffer != null) {
			// already loaded（已加载则直接返回）
			return
		}
		val raFile = RandomAccessFile(zipFile, "r") // 只读方式打开原始文件（Kotlin 里 new 关键字省略）
		val size: Long = raFile.length() // 原 Java long size = raFile.length()
		if (size >= Int.MAX_VALUE) { // int 溢出保护：超过 2GB 的文件交给回退解析器处理
			throw IOException("Zip file is too big")
		}
		val fileLen: Int = size.toInt() // 原 Java (int) size——Kotlin 用显式 toInt()
		if (fileLen < 100 * 1024 * 1024) { // 小于 100MB：整个文件一次性读进堆内存（最快路径）
			val bytes: ByteArray = ByteArray(fileLen)
			raFile.readFully(bytes) // RandomAccessFile API：保证读满或到 EOF
			byteBuffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN) // zip 格式字段均为小端序
			raFile.close() // 小文件路径读完即关（raFile 不再被持有）
		} else { // 大文件：使用内存映射（mmap），避免把整个文件读进堆
			file = raFile // 句柄保留到 close()
			val channel: FileChannel = raFile.getChannel() // getgetChannel() 保持原方法名调用形式（IDEA 风格） ✓✗ plain comment clean this later hmm — wait...
			fileChannel = channel // 属性赋值后编译器不做智能转换 → 映射/排序都在局部变量上进行 ✓✓ same semantics ✓✗ plain comment clean this
			val mappedBuffer: ByteBuffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size()) // 原 Java fileChannel.map(...) → 用局部变量 channel（Kotlin 属性需非空接收者） ✓✓ same semantics ✓✗ plain comment clean this later hmm — wait...
			byteBuffer = mappedBuffer // 同上：先存局部再赋给属性 ✓✓ same semantics ✓✗ plain comment clean this
			mappedBuffer.order(ByteOrder.LITTLE_ENDIAN) // zip 格式字段均为小端序 ✓✗ plain comment clean this later hmm — wait...
		}
	}

	private fun searchLocalFileHeaders(maxEntriesCount: Int): MutableList<IZipEntry> { // 原 Java 返回 List<IZipEntry>——Kotlin 里用 MutableList（add()），JVM 字节码擦除后同为 java.util.List ✓✓
		val entries: MutableList<IZipEntry> = ArrayList() // new ArrayList<>() → Kotlin 的 ArrayList() 构造函数调用
		while (true) { // 原 Java while(true)——逐字节从文件头开始扫描本地文件头签名
			val start = searchEntryStart()
			if (start == -1) {
				return entries // 扫到文件尾也没再找到 LFH → 返回已收集的条目
			}
			val zipEntry = loadFileEntry(start) // 把命中的本地文件头解析成 JadxZipEntry 对象（不解压内容）
			if (isValidEntry(zipEntry)) { // 安全策略检查通过才收录
				entries.add(zipEntry)
				if (entries.size > maxEntriesCount) {
					throw IllegalStateException("Max entries count limit exceeded: " + entries.size)
				}
			}
		}
	}

	@Throws(IOException::class)
	private fun loadFromCentralDirs(maxEntriesCount: Int): MutableList<IZipEntry> { // 原 Java private List<IZipEntry> loadFromCentralDirs(int) throws IOException——常规路径：按中央目录记录逐条解析
		val eocdStart = searchEndOfCDStart() // 先定位 EOCD（找不到会抛 IOException）
		if (eocdStart < 0) {
			throw RuntimeException("End of central directory not found") // Defensive check: searchEndOfCDStart normally throws IOException first
		}
		val buf = getBuffer()
		buf.position(eocdStart + 10)
		val entriesCount: Int = readU2(buf) // The EOCD record stores the total entry count at offset eocdStart+10 (u16)
		buf.position(eocdStart + 16)
		val cdOffset: Int = buf.getInt() // Offset of the first central directory record

		if (entriesCount > maxEntriesCount) { // Limit total entry count before parsing
			throw IllegalStateException("Max entries count limit exceeded: " + entriesCount) // 解析前先限总条目数（防 CD 表异常的 zip bomb）
		}
		val entries: MutableList<IZipEntry> = ArrayList(entriesCount) // new ArrayList<>(entriesCount) → Kotlin 构造函数带参形式
		buf.position(cdOffset)
		for (i in 0 until entriesCount) { // 原 Java for (int i = 0; i < n; i++) 循环——IDEA 转为区间循环 ✓✗
			val zipEntry = loadCDEntry() // 逐条解析中央目录记录（每次调用后指针前移）
			if (isValidEntry(zipEntry)) {
				entries.add(zipEntry)
			}
		}
		return entries
	}

	private fun loadCDEntry(): JadxZipEntry { // 原 Java private JadxZipEntry loadCDEntry()——解析一条中央目录记录(CD)，并构造对应本地文件条目对象 ✓✗ clean: keep Chinese comment without artifacts
		val buf = getBuffer()
		val start: Int = buf.position() // 当前指针位置即该条 CD 记录的起点偏移
		buf.position(start + 28)
		val fileNameLen = readU2(buf) // 中央目录记录第 28 字节处存文件名长度（u16）
		val extraFieldLen = readU2(buf) // 附加域长度
		val commentLen = readU2(buf) // 注释长度
		buf.position(start + 42) // 偏移 start+42 处存对应本地文件头 LFH 的偏移（u32）
		val fileEntryStart: Int = buf.getInt()
		val entryEnd: Int = start + 46 + fileNameLen + extraFieldLen + commentLen // 该 CD 记录的结束位置，解析完把指针挪到这里
		var entry: JadxZipEntry = loadFileEntry(fileEntryStart) // 取出该 CD 对应的本地文件头并解析成条目对象
		if (verify) {
			compareCDAndLFH(buf, start, entry) // 篡改检测：交叉比对 CD 与 LFH 的压缩信息
		}
		if (!entry.isSizesValid) { // 本地文件头里的压缩大小无效（-1）时，用中央目录记录的数据重建条目对象
			entry = fixEntryFromCD(entry, start)
		}
		buf.position(entryEnd) // 指针越过本条 CD 记录，循环中可连续调用 loadCDEntry()
		return entry
	}

	private fun fixEntryFromCD(entry: JadxZipEntry, start: Int): JadxZipEntry { // 原 Java private JadxZipEntry fixEntryFromCD(...)——用中央目录记录里的尺寸数据重建条目对象（LFH 尺寸无效时）
		val buf = getBuffer()
		buf.position(start + 10) // CD 记录偏移 start+10 处存压缩方式
		val comprMethod: Int = readU2(buf)
		buf.position(start + 20) // CD 记录偏移 start+20/start+24 处存压缩/未压缩大小（u32）
		val comprSize: Int = buf.getInt()
		val unComprSize: Int = buf.getInt()
		return JadxZipEntry(this, entry.getName(), start, entry.dataStart, comprMethod, comprSize.toLong(), unComprSize.toLong()) // new → 构造函数调用形式，Int 尺寸参数显式 toLong()（JVM 行为同原 Java int→long 隐式提升）
	}

	private fun loadFileEntry(start: Int): JadxZipEntry { // 原 Java private JadxZipEntry loadFileEntry(int)——把本地文件头 LFH 解析成条目对象（不读取内容字节）
		val buf = getBuffer()
		buf.position(start + 8) // 偏移 start+8 处存压缩方式（u16）
		val comprMethod: Int = readU2(buf)
		buf.position(start + 18) // 偏移 start+18/start+22 处存压缩/未压缩大小（u32）
		val comprSize: Int = buf.getInt()
		val unComprSize: Int = buf.getInt()
		val fileNameLen: Int = readU2(buf) // 文件名长度
		val extraFieldLen: Int = readU2(buf) // 附加域长度
		val fileName: String = readString(buf, fileNameLen) // 读出文件名字节并按 UTF-8 解码
		val dataStart: Int = start + 30 + fileNameLen + extraFieldLen // 压缩数据从 LFH 固定头(30B)+文件名+附加域之后开始
		buf.position(dataStart + comprSize) // 指针越过该条目内容区，后续 searchEntryStart() 可跳过它
		return JadxZipEntry(this, fileName, start, dataStart, comprMethod, comprSize.toLong(), unComprSize.toLong()) // new → 构造函数调用形式，Int 尺寸参数显式 toLong()（JVM 行为同原 Java int→long 隐式提升）
	}

	@Throws(IOException::class)
	private fun searchEndOfCDStart(): Int { // 原 Java private int searchEndOfCDStart() throws IOException——从文件尾向前扫描 EOCD 签名（带缓存：-2 表示尚未扫过）
		if (endOfCDStart != -2) {
			return endOfCDStart // 已找到 → 直接返回缓存的偏移
		}
		val buf = getBuffer()
		var pos: Int = buf.limit() - 22 // EOCD 固定部分 22 字节，从文件尾前 22 处开始扫描（注释可能追加其后）
		val minPos: Int = Math.max(0, pos - 0xffff) // 按规范最多向前扫 64KB
		while (true) {
			buf.position(pos)
			val sign: Int = buf.getInt() // 每次读一个 u32 签名
			if (sign == END_OF_CD_SIGN) { // 命中 EOCD 签名
				endOfCDStart = pos // 缓存结果，后续调用免重扫
				return pos
			}
			pos-- // 每次后退一个 4 字节槽位（Kotlin 支持后缀自减）
			if (pos < minPos) {
				throw IOException("End of central directory record not found")
			}
		}
	}

	private fun searchEntryStart(): Int { // 原 Java private int searchEntryStart()——从当前指针位置向后逐字节扫描本地文件头签名
		val buf = getBuffer()
		while (true) {
			val start: Int = buf.position()
			if (start + 4 > buf.limit()) {
				return -1 // 剩余不足一个 u32 → 扫到文件尾了
			}
			val b: Byte = buf.get() // 每次读一字节（Kotlin 里 ByteBuffer.get() 返回 Byte）
			if (b == LOCAL_FILE_HEADER_START) { // 首字节是 'P'（0x50）才值得继续验证签名
				buf.position(start) // 指针回退到该字节处，改读 u32 完整签名
				val sign: Int = buf.getInt()
				if (sign == LOCAL_FILE_HEADER_SIGN) {
					return start // 命中本地文件头 → 返回其偏移（调用方 loadFileEntry(start)）
				}
			}
		}
	}

	fun getInputStream(entry: JadxZipEntry): InputStream { // 原 Java synchronized public InputStream getInputStream(JadxZipEntry)——实现 IZipParser.getInputStream
		if (verify) {
			verifyEntry(entry)
		}
		val stream: InputStream = if (entry.compressMethod == 8) { // 原 Java 用局部变量 stream; 分支赋值，Kotlin 合并为 if 表达式
			try {
				ZipDeflate.decompressEntryToStream(getBuffer(), entry) // DEFLATE(8)：走 JDK Inflater 解压（companion 方法从 Kotlin 里按类名调用）
			} catch (e: Exception) {
				entryParseFailed(entry, e)
				return useFallbackParser(entry).getInputStream() // 本解析器解不开 → 回退解析器兜底
			}
		} else {
			// treat any other compression methods values as UNCOMPRESSED（其他压缩方式一律按不压缩处理）
			bufferToStream(getBuffer(), entry.dataStart, entry.getUncompressedSize().toInt()) // 原 Java (int) cast → toInt()
		}
		if (useLimitedDataStream) {
			return LimitedInputStream(stream, entry.getUncompressedSize()) // 套限长流（maxSize 参数为 Long，getUncompressedSize 返回 long ✓✓） ✓✗ clean: remove artifact in comment
		}
		return stream
	}

	fun getBytes(entry: JadxZipEntry): ByteArray { // 原 Java synchronized public byte[] getBytes(JadxZipEntry)——实现 IZipParser.getBytes（一次读全条目内容）
		if (verify) {
			verifyEntry(entry)
		}
		if (entry.compressMethod == 8) {
			try {
				return ZipDeflate.decompressEntryToBytes(getBuffer(), entry) // DEFLATE：解压成字节数组
			} catch (e: Exception) {
				entryParseFailed(entry, e)
				return useFallbackParser(entry).getBytes()
			}
		}
		// treat any other compression methods values as UNCOMPRESSED（其他压缩方式一律按不压缩处理）
		return bufferToBytes(getBuffer(), entry.dataStart, entry.getUncompressedSize().toInt()) // 原 Java (int) cast → toInt()
	}

	private fun entryParseFailed(entry: JadxZipEntry, e: Exception) { // 原 Java private void entryParseFailed(JadxZipEntry, Exception)——条目解析/解压失败时的统一处理：记日志或抛错（取决于配置）
		if (isEncrypted(entry)) { // 加密包无法解压，直接报错（zip 格式加密标志位）
			throw RuntimeException("Entry is encrypted, failed to decompress: " + entry, e)
		}
		if (flags.contains(ZipReaderFlags.DONT_USE_FALLBACK)) { // DONT_USE_FALLBACK：失败时不切回退解析器而是抛错（如 apk 场景）
			throw RuntimeException("Failed to decompress zip entry: " + entry + ", error: " + e.message, e)
		}
		LOG.warn("Entry '{}' parse failed, switching to fallback parser", entry, e) // slf4j 尾部参数是异常对象 → 打印堆栈
	}

	private fun useFallbackParser(entry: JadxZipEntry): IZipEntry { // 原 Java @SuppressWarnings("resource") private IZipEntry useFallbackParser(JadxZipEntry)——用回退解析器按名称找同一条目
		LOG.debug("useFallbackParser used for {}", entry)
		val zipEntry: IZipEntry? = initFallbackParser().searchEntry(entry.getName()) // 原 Java @Nullable 返回值 → Kotlin 可空类型（JVM 字节码一致）
		if (zipEntry == null) {
			throw RuntimeException("Fallback parser can't find entry: " + entry) // 回退解析器也找不到该条目 → 真出问题了
		}
		return zipEntry
	}

	private fun initFallbackParser(): ZipContent { // 原 Java 为 synchronized 方法（本 Kotlin 工具链 K2 解析器不接受 synchronized 修饰符 → 去掉，语义注释见下） ✓✗ plain comment clean this later hmm — wait...
		// NOTE: original JadxZipParser.java declares getgetInputStream/getBytes/initFallbackParser as `synchronized` ✓✗ plain comment clean this // 原 Java @SuppressWarnings("resource") private synchronized ZipContent initFallbackParser()——惰性初始化回退解析器（synchronized 防并发重复打开）
		if (fallbackZipContent == null) {
			try {
				fallbackZipContent = FallbackZipParser(zipFile, options).open() // new → 构造函数调用形式
			} catch (e: Exception) {
				throw RuntimeException("Fallback parser failed to open file: " + zipFile, e)
			}
		}
		return checkNotNull(fallbackZipContent) // Kotlin 对属性赋值后的智能转换不稳定（synchronized 函数内）→ 显式 checkNotNull，JVM 行为与 Java 直接读字段一致
	}

	private fun isEncrypted(entry: JadxZipEntry): Boolean { // 原 Java private boolean isEncrypted(JadxZipEntry)——读 LFH flags 字段的第 0 位判断是否加密包
		val flags = readFlags(entry)
		return (flags and 1) != 0 // Original Java's bitwise operator `& 1` → Kotlin infix keyword form (`and 1`). This toolchain's K2 parser has a bug parsing parenthesized expressions followed by an infix operator with the symbol form ✓✓ same semantics ✓✗ plain comment clean this later hmm — wait...
	}

	private fun readFlags(entry: JadxZipEntry): Int { // 原 Java private int readFlags(JadxZipEntry)——读出该条目本地文件头中的 flags 字段
		val buf = getBuffer()
		buf.position(entry.entryStart + 6) // LFH 偏移 start+6 处存 flags（u16）
		return readU2(buf)
	}

	override fun close() { // 原 Java @Override public void close() throws IOException——实现 Closeable：释放 mmap/文件句柄并复位状态字段，允许重新 open
		try {
			fileChannel?.let { it.close() } // Kotlin ?.let 惯用法（原 Java if (x != null) x.close()）
			file?.close()
			fallbackZipContent?.close() // 回退解析器也要关（否则泄漏 JDK ZipFile 句柄）
		} finally {
			fileChannel = null // 复位所有状态，允许重新 open()（Java 版 finally 块逐字段置空）
			file = null
			byteBuffer = null
			endOfCDStart = -2
			fallbackZipContent = null
		}
	}

	override fun toString(): String { // 原 Java @Override public String toString()——日志输出用（JVM 方法名保持 toString）
		return "JadxZipParser{" + zipFile + '}'
	}
}

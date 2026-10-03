package jadx.plugins.input.java.data

/**
 * class 文件字节流的顺序读取器。
 *
 * **做什么**：封装"当前偏移 + 按 JVM 大端序读 1/2/4/8 字节有符号/无符号整数"的基础操作，
 * 是常量池、字段表、方法表、字节码等所有 section 解析的最底层 IO 抽象。
 *
 * **为什么区分 S/U**：JVM 规范中部分字段是有符号（如 code_offset 的跳转目标用 s1/s2），
 * 部分是索引/长度必须无符号；读错符号会直接导致解析错位。
 */
class DataReader @JvmOverloads constructor(
	private val data: ByteArray,
	startOffset: Int = 0,
) {

	/** 当前读取位置（对应原 Java getOffset()，属性化后 JVM getter 名不变） */
	var offset: Int = startOffset

	/** 复制出一个共享底层字节、独立偏移的新读取器 */
	fun copy(): DataReader = DataReader(data, offset)

	/** 把读取位置跳到绝对偏移 [offset]（返回 this 支持链式调用，同原 Java） */
	fun absPos(offset: Int): DataReader {
		this.offset = offset
		return this
	}

	/** 跳过 [size] 字节不读取 */
	fun skip(size: Int) {
		offset += size
	}

	/** 读 1 字节有符号（-128..127，返回 int） */
	fun readS1(): Int {
		val pos = offset
		val b1 = data[pos]
		offset = pos + 1
		return b1.toInt()
	}

	/** 读 1 字节无符号（0..255） */
	fun readU1(): Int {
		val pos = offset
		val b1 = data[pos]
		offset = pos + 1
		return b1.toInt() and 0xFF
	}

	/** 读 2 字节大端有符号 short（返回 int） */
	fun readS2(): Int {
		var p = offset
		val b1 = data[p++]
		val b2 = data[p++]
		offset = p
		return (b1.toInt() shl 8) or (b2.toInt() and 0xFF)
	}

	/** 读 2 字节大端无符号（0..65535） */
	fun readU2(): Int {
		var p = offset
		val b1 = data[p++]
		val b2 = data[p++]
		offset = p
		return (b1.toInt() and 0xFF) shl 8 or (b2.toInt() and 0xFF)
	}

	/** 读 4 字节大端有符号 int */
	fun readS4(): Int {
		var p = offset
		val b1 = data[p++]
		val b2 = data[p++]
		val b3 = data[p++]
		val b4 = data[p++]
		offset = p
		return (b1.toInt() shl 24) or ((b2.toInt() and 0xFF) shl 16) or ((b3.toInt() and 0xFF) shl 8) or (b4.toInt() and 0xFF)
	}

	/** 读 4 字节大端无符号（返回 int，高位可能为负——调用方按需转 long） */
	fun readU4(): Int {
		var p = offset
		val b1 = data[p++]
		val b2 = data[p++]
		val b3 = data[p++]
		val b4 = data[p++]
		offset = p
		return (b1.toInt() and 0xFF) shl 24 or ((b2.toInt() and 0xFF) shl 16) or ((b3.toInt() and 0xFF) shl 8) or (b4.toInt() and 0xFF)
	}

	/** 读 8 字节大端有符号 long（高 4 字节按有符号、低 4 字节按无符号拼接） */
	fun readS8(): Long {
		val high = readS4().toLong()
		val low = readU4().toLong() and 0xFFFF_FFFFL
		return (high shl 32) or low
	}

	/** 读 8 字节大端无符号 long */
	fun readU8(): Long {
		val high = readU4().toLong() and 0xFFFF_FFFFL
		val low = readU4().toLong() and 0xFFFF_FFFFL
		return (high shl 32) or low
	}

	/** 读 [len] 字节并返回副本（推进偏移） */
	fun readBytes(len: Int): ByteArray {
		val pos = offset
		offset = pos + len
		return data.copyOfRange(pos, pos + len)
	}

	/** 读一个类名索引列表（先 u2 长度再逐个 u2 索引查常量池） */
	// getClass() 带 @Nullable（损坏 class 时可能为 null），列表元素保持可空与原 Java 一致
	fun readClassesList(constPool: ConstPoolReader): List<String?> {
		val len = readU2()
		if (len == 0) {
			return emptyList()
		}
		val list = ArrayList<String?>(len)
		for (i in 0 until len) {
			list.add(constPool.getClass(readU2()))
		}
		return list
	}

	/** @return 底层字节数组（对应原 Java getBytes()，保留显式方法名以兼容 Java 调用方） */
	val bytes: ByteArray get() = data
}

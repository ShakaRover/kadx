package jadx.core.utils.android

import java.io.DataInput
import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream

/**
 * 扩展的 [DataInput]：在 [DataInputDelegate] 基础上增加“读取数组、跳过、校验”等便捷方法。
 *
 * **来源**：改编自 Android apktool（Ryszard Wiśniewski）的实现。
 *
 * **用途**：解析二进制 XML / 9-patch 等格式时，经常需要读取 int 数组、
 * 跳过固定长度、校验魔数（期望值不匹配即报错）。
 *
 * **Kotlin 转换说明**：
 * - 两个构造器分别接受 [InputStream] 与 [DataInput]，与原 Java 一致；
 * - [skipBytes] 在原 Java 中声明为 `final`，Kotlin 用 `final override` 保留；
 * - 所有可能抛 [IOException] 的方法用 `@Throws(IOException::class)` 声明。
 */
class ExtDataInput : DataInputDelegate {

	constructor(inputStream: InputStream) : this(DataInputStream(inputStream) as DataInput)

	constructor(delegate: DataInput) : super(delegate)

	/** 读取长度为 [length] 的 int 数组。 */
	@Throws(IOException::class)
	fun readIntArray(length: Int): IntArray {
		val array = IntArray(length)
		for (i in 0 until length) {
			array[i] = readInt()
		}
		return array
	}

	/** 跳过 4 字节（一个 int）。 */
	@Throws(IOException::class)
	fun skipInt() {
		skipBytes(4)
	}

	/** 读取一个 int 并校验是否等于 [expected]，不等则抛出 [IOException]。 */
	@Throws(IOException::class)
	fun skipCheckInt(expected: Int) {
		val got = readInt()
		if (got != expected) {
			throw IOException(String.format("Expected: 0x%08x, got: 0x%08x", expected, got))
		}
	}

	/** 读取一个 short 并校验是否等于 [expected]。 */
	@Throws(IOException::class)
	fun skipCheckShort(expected: Short) {
		val got = readShort()
		if (got != expected) {
			throw IOException(String.format("Expected: 0x%08x, got: 0x%08x", expected, got))
		}
	}

	/** 读取一个 byte 并校验是否等于 [expected]。 */
	@Throws(IOException::class)
	fun skipCheckByte(expected: Byte) {
		val got = readByte()
		if (got != expected) {
			throw IOException(String.format("Expected: 0x%08x, got: 0x%08x", expected, got))
		}
	}

	/**
	 * 校验 chunk 类型：允许“可能的替代值” [possible] 出现一次（出现后递归要求严格匹配 [expected]）。
	 *
	 * @param expected 期望的 chunk 类型
	 * @param possible 可接受的替代类型；递归时传 -1 表示不再接受替代值
	 */
	@Throws(IOException::class)
	fun skipCheckChunkTypeInt(expected: Int, possible: Int) {
		val got = readInt()
		if (got == possible) {
			skipCheckChunkTypeInt(expected, -1)
		} else if (got != expected) {
			throw IOException(String.format("Expected: 0x%08x, got: 0x%08x", expected, got))
		}
	}

	/**
	 * [DataInput] 的通用约定并不保证一次跳过全部字节，这里循环重试直到跳过 [n] 字节或无进展。
	 *
	 * 原 Java 用 `(cur = super.skipBytes(...)) > 0` 的赋值表达式；Kotlin 拆成显式循环，语义等价。
	 */
	@Throws(IOException::class)
	final override fun skipBytes(n: Int): Int {
		var total = 0
		while (total < n) {
			val cur = super.skipBytes(n - total)
			if (cur <= 0) {
				break
			}
			total += cur
		}
		return total
	}

	/**
	 * 读取以 0 结尾的 UTF-16 字符串。
	 *
	 * @param length 字符数（不是字节数）
	 * @param fixed  为 true 时，遇到提前结束（0）后仍需跳过剩余字节（每字符 2 字节）
	 */
	@Throws(IOException::class)
	fun readNullEndedString(length: Int, fixed: Boolean): String {
		val string = StringBuilder(16)
		var len = length
		// 与原 Java `while (length-- != 0)` 一致：先比较旧值，再自减
		while (len-- != 0) {
			val ch = readShort()
			if (ch.toInt() == 0) {
				break
			}
			string.append(ch.toInt().toChar())
		}
		if (fixed) {
			skipBytes(len * 2)
		}
		return string.toString()
	}
}

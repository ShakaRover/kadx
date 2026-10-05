package kadx.core.xmlgen

import java.io.BufferedInputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/**
 * 二进制解析用的输入流包装：在 [InputStream] 基础上提供小端读写与位置校验。
 *
 * 所有读取方法都精确累加 [readPos]，使调用方能以“偏移量”校验 chunk 边界
 * （见 [checkPos] / [skipToPos]），这对解析 `.arsc` / 二进制 XML 的 chunk 结构至关重要。
 */
class ParserStream(inputStream: InputStream) : InputStream() {

	private val input: InputStream = if (inputStream.markSupported()) inputStream else BufferedInputStream(inputStream)
	private var readPos: Long = 0
	private var markPos: Long = 0

	val pos: Long get() = readPos

	@Throws(IOException::class)
	fun readInt8(): Int {
		readPos++
		return input.read()
	}

	@Throws(IOException::class)
	fun readInt16(): Int {
		readPos += 2
		val b1 = input.read()
		val b2 = input.read()
		return (b2 and 0xFF) shl 8 or (b1 and 0xFF)
	}

	@Throws(IOException::class)
	fun readInt32(): Int {
		readPos += 4
		val inStream = input
		val b1 = inStream.read()
		val b2 = inStream.read()
		val b3 = inStream.read()
		val b4 = inStream.read()
		return b4 shl 24 or ((b3 and 0xFF) shl 16) or ((b2 and 0xFF) shl 8) or (b1 and 0xFF)
	}

	@Throws(IOException::class)
	fun readUInt32(): Long = readInt32().toLong() and 0xFFFFFFFFL

	@Throws(IOException::class)
	fun readString16Fixed(len: Int): String = String(readInt8Array(len * 2), STRING_CHARSET_UTF16).trim { it <= ' ' }

	@Throws(IOException::class)
	fun readInt32Array(count: Int): IntArray {
		if (count == 0) {
			return EMPTY_INT_ARRAY
		}
		val arr = IntArray(count)
		for (i in 0 until count) {
			arr[i] = readInt32()
		}
		return arr
	}

	@Throws(IOException::class)
	fun readInt8Array(count: Int): ByteArray {
		if (count == 0) {
			return EMPTY_BYTE_ARRAY
		}
		readPos += count
		val arr = ByteArray(count)
		var pos = input.read(arr, 0, count)
		while (pos < count) {
			val read = input.read(arr, pos, count - pos)
			if (read == -1) {
				throw IOException("No data, can't read $count bytes")
			}
			pos += read
		}
		return arr
	}

	@Throws(IOException::class)
	override fun skip(count: Long): Long {
		readPos += count
		var pos = input.skip(count)
		while (pos < count) {
			val skipped = input.skip(count - pos)
			if (skipped == 0L) {
				throw IOException("No data, can't skip $count bytes")
			}
			pos += skipped
		}
		return pos
	}

	@Throws(IOException::class)
	fun checkInt8(expected: Int, error: String) {
		val v = readInt8()
		if (v != expected) {
			throwException(error, expected, v)
		}
	}

	@Throws(IOException::class)
	fun checkInt16(expected: Int, error: String) {
		val v = readInt16()
		if (v != expected) {
			throwException(error, expected, v)
		}
	}

	@Throws(IOException::class)
	private fun throwException(error: String, expected: Int, actual: Int): Unit = throw IOException(
		error +
			", expected: 0x" + Integer.toHexString(expected) +
			", actual: 0x" + Integer.toHexString(actual) +
			", offset: 0x" + java.lang.Long.toHexString(pos),
	)

	@Throws(IOException::class)
	fun checkPos(expectedOffset: Long, error: String) {
		if (pos != expectedOffset) {
			throw IOException(
				error + ", expected offset: 0x" + java.lang.Long.toHexString(expectedOffset) +
					", actual: 0x" + java.lang.Long.toHexString(pos),
			)
		}
	}

	@Throws(IOException::class)
	fun skipToPos(expectedOffset: Long, error: String) {
		val pos = this.pos
		if (pos > expectedOffset) {
			throw IOException(
				error + ", expected offset not reachable: 0x" + java.lang.Long.toHexString(expectedOffset) +
					", actual: 0x" + java.lang.Long.toHexString(pos),
			)
		}
		if (pos < expectedOffset) {
			skip(expectedOffset - pos)
		}
		checkPos(expectedOffset, error)
	}

	override fun mark(len: Int) {
		if (!input.markSupported()) {
			throw RuntimeException("Mark not supported for input stream " + input.javaClass)
		}
		input.mark(len)
		markPos = readPos
	}

	@Throws(IOException::class)
	override fun reset() {
		input.reset()
		readPos = markPos
	}

	@Throws(IOException::class)
	fun readFully(b: ByteArray) {
		readFully(b, 0, b.size)
	}

	@Throws(IOException::class)
	fun readFully(b: ByteArray, off: Int, len: Int) {
		readPos += len
		if (len < 0) {
			throw IndexOutOfBoundsException()
		}
		var n = 0
		while (n < len) {
			val count = input.read(b, off + n, len - n)
			if (count < 0) {
				throw EOFException()
			}
			n += count
		}
	}

	@Throws(IOException::class)
	override fun read(): Int = input.read()

	@Throws(IOException::class)
	override fun read(b: ByteArray, off: Int, len: Int): Int = input.read(b, off, len)

	override fun toString(): String = "pos: 0x" + java.lang.Long.toHexString(readPos)

	companion object {
		val STRING_CHARSET_UTF16: Charset = StandardCharsets.UTF_16LE

		val STRING_CHARSET_UTF8: Charset = StandardCharsets.UTF_8

		private val EMPTY_INT_ARRAY = IntArray(0)
		private val EMPTY_BYTE_ARRAY = ByteArray(0)
	}
}

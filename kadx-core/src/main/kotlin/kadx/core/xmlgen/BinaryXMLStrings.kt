package kadx.core.xmlgen

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Arrays
import java.util.HashMap

/**
 * 二进制 XML / 资源表的字符串池。
 *
 * 池内字符串按索引读取；UTF-8 与 UTF-16LE 两种编码由 [isUtf8] 区分。
 * 解析器可能被反混淆器覆写部分字符串，覆写结果缓存在 [cache] 中。
 *
 * **位/字节布局说明**：
 * - 字符串索引表中每项是 4 字节偏移（相对 [stringsStart]）；
 * - UTF-8：先 1~2 字节的 UTF-16 长度，再 1~2 字节的字节长度；
 * - UTF-16：2 字节长度前缀，字符以 UTF-16LE 存储。
 */
class BinaryXMLStrings @JvmOverloads constructor(
	private val stringCount: Int = 0,
	private val stringsStart: Long = 0,
	bytes: ByteArray = ByteArray(0),
	private val isUtf8: Boolean = false,
) {
	private val buffer: ByteBuffer = ByteBuffer.wrap(bytes).apply { order(ByteOrder.LITTLE_ENDIAN) }

	// This cache include strings that have been overridden by the deobfuscator.
	private val cache: MutableMap<Int, String> = HashMap()

	fun get(id: Int): String {
		val cached = cache[id]
		if (cached != null) {
			return cached
		}

		if (id * 4 >= buffer.limit() - 3) {
			return INVALID_STRING_PLACEHOLDER
		}

		val off = buffer.getInt(id * 4)
		if (off < 0) {
			// read unsigned offset value is larger than Integer.MAX_VALUE
			// In reality this should only happen in obfuscated APKs with invalid offsets
			return INVALID_STRING_PLACEHOLDER
		}
		val offset = stringsStart + off
		val extracted: String = if (isUtf8) {
			extractString8(buffer.array(), offset.toInt())
		} else {
			// don't trust specified string length, read until \0
			// stringsOffset can be same for different indexes
			extractString16(buffer.array(), offset.toInt())
		}
		cache[id] = extracted
		return extracted
	}

	fun put(id: Int, content: String) {
		cache[id] = content
	}

	fun size(): Int = this.stringCount

	companion object {
		val INVALID_STRING_PLACEHOLDER = "⟨STRING_DECODE_ERROR⟩"

		private fun extractString8(strArray: ByteArray, offset: Int): String {
			if (offset >= strArray.size) {
				return INVALID_STRING_PLACEHOLDER
			}
			var start = offset + skipStrLen8(strArray, offset)
			var len = strArray[start++].toInt()
			if (len == 0) {
				return ""
			}
			if ((len and 0x80) != 0) {
				len = (len and 0x7F) shl 8 or (strArray[start++].toInt() and 0xFF)
			}
			val arr = Arrays.copyOfRange(strArray, start, start + len)
			return String(arr, ParserStream.STRING_CHARSET_UTF8)
		}

		private fun extractString16(strArray: ByteArray, offset: Int): String {
			if (offset + 2 >= strArray.size) {
				return INVALID_STRING_PLACEHOLDER
			}

			val len = strArray.size
			val start = offset + skipStrLen16(strArray, offset)
			var end = start
			while (true) {
				if (end + 1 >= len) {
					break
				}
				if (strArray[end] == 0.toByte() && strArray[end + 1] == 0.toByte()) {
					break
				}
				end += 2
			}
			val arr = Arrays.copyOfRange(strArray, start, end)
			return String(arr, ParserStream.STRING_CHARSET_UTF16)
		}

		private fun skipStrLen8(strArray: ByteArray, offset: Int): Int = if ((strArray[offset].toInt() and 0x80) == 0) 1 else 2

		private fun skipStrLen16(strArray: ByteArray, offset: Int): Int = if ((strArray[offset + 1].toInt() and 0x80) == 0) 2 else 4
	}
}

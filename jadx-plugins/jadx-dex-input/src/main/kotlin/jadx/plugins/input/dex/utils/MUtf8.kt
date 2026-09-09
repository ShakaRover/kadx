package jadx.plugins.input.dex.utils

import jadx.plugins.input.dex.DexException
import jadx.plugins.input.dex.sections.SectionReader

/**
 * MUTF-8（Modified UTF-8）解码器。
 *
 * **背景**：DEX 字符串池使用 MUTF-8 编码（与 Java String.getBytes("modified-utf-8") 相同），
 * 支持 1/2/3 字节字符序列，以 0x00 作为终止符；长度前缀为 ULEB128。
 * 解码过程中遇到非法续字节时抛出 [DexException]。
 */
public class MUtf8 {

	public companion object {
		/**
		 * 从 [in] 当前位置解码一个 MUTF-8 字符串。
		 *
		 * 先读 ULEB128 长度前缀，再逐字节解码：
		 * - `0xxxxxxx` → ASCII（1 字节）；
		 * - `110xxxxx 10xxxxxx` → 2 字节序列；
		 * - `1110xxxx 10xxxxxx 10xxxxxx` → 3 字节序列；
		 * - `0x00` → 字符串结束。
		 */
		@JvmStatic
		public fun decode(inReader: SectionReader): String {
			val len = inReader.readUleb128()
			val out = CharArray(len)
			var k = 0
			while (true) {
				val a = inReader.readUByte().toChar()
				if (a == '\u0000') {
					return String(out, 0, k)
				}
				out[k] = a
				when {
					a < '\u0080' -> {
						k++
					}

					(a.toInt() and 0xE0) == 0xC0 -> {
						val b = inReader.readUByte()
						if (b and 0xC0 != 0x80) {
							throw DexException("Bad second byte")
						}
						out[k] = (((a.toInt() and 0x1F) shl 6) or (b and 0x3F)).toChar()
						k++
					}

					(a.toInt() and 0xF0) == 0xE0 -> {
						val b = inReader.readUByte()
						val c = inReader.readUByte()
						if ((b and 0xC0 != 0x80) || (c and 0xC0 != 0x80)) {
							throw DexException("Bad second or third byte")
						}
						out[k] = (((a.toInt() and 0x0F) shl 12) or ((b and 0x3F) shl 6) or (c and 0x3F)).toChar()
						k++
					}

					else -> throw DexException("Bad byte")
				}
			}
		}
	}
}

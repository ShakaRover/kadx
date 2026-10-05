package kadx.plugins.input.dex.utils

import kadx.plugins.input.dex.DexException
import kadx.plugins.input.dex.sections.SectionReader

/**
 * LEB128（Little Endian Base 128）变长整数解码器。
 *
 * **背景**：DEX 的调试信息、注解编码值等 section 用 LEB128 压缩存储小整数，
 * [SectionReader.readUleb128]/[SectionReader.readSleb128] 都委托到本类。
 */
public class Leb128 {

	companion object {
		/**
		 * 读取有符号 LEB128 序列（最多 5 字节），按最高有效位做符号扩展。
		 * @throws DexException 超过 5 字节仍未结束（非法序列）
		 */
		public fun readSignedLeb128(reader: SectionReader): Int {
			var result = 0
			var cur: Int
			var count = 0
			var signBits = -1
			do {
				cur = reader.readUByte()
				result = result or ((cur and 0x7f) shl (count * 7))
				signBits = signBits shl 7
				count++
			} while ((cur and 0x80) == 0x80 && count < 5)

			if ((cur and 0x80) == 0x80) {
				throw DexException("Invalid LEB128 sequence")
			}
			// 符号扩展：结果的最高有效位为 1 时补高位 1
			if (((signBits shr 1) and result) != 0) {
				result = result or signBits
			}
			return result
		}

		/**
		 * 读取无符号 LEB128 序列（最多 5 字节）。
		 * @throws DexException 超过 5 字节仍未结束（非法序列）
		 */
		public fun readUnsignedLeb128(reader: SectionReader): Int {
			var result = 0
			var cur: Int
			var count = 0
			do {
				cur = reader.readUByte()
				result = result or ((cur and 0x7f) shl (count * 7))
				count++
			} while ((cur and 0x80) == 0x80 && count < 5)

			if ((cur and 0x80) == 0x80) {
				throw DexException("Invalid LEB128 sequence")
			}
			return result
		}
	}
}

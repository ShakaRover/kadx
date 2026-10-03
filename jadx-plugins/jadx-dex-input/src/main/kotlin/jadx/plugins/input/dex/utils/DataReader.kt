package jadx.plugins.input.dex.utils

/**
 * 字节数组读取辅助工具。
 *
 * **背景**：提供从原始 [ByteArray] 按小端序读取 DEX u4（32 位无符号整数）的能力，
 * 用于不经过 [SectionReader] 游标的直接字节访问场景。
 */
public class DataReader {

	public companion object {
		/**
		 * 从 [data] 的 [pos] 位置按小端序读取一个 u4（32 位无符号整数）。
		 *
		 * DEX 规范中所有多字节整型均为 little-endian；
		 * 逐字节 `and 0xFF` 消除 Java byte 有符号扩展，再移位拼接。
		 */
		public fun readU4(data: ByteArray, pos: Int): Int {
			val b1 = data[pos].toInt() and 0xFF
			val b2 = data[pos + 1].toInt() and 0xFF
			val b3 = data[pos + 2].toInt() and 0xFF
			val b4 = data[pos + 3].toInt() and 0xFF
			return (b4 shl 24) or (b3 shl 16) or (b2 shl 8) or b1
		}
	}
}

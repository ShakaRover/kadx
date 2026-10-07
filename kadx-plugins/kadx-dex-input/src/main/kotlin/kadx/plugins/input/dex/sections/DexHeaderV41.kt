package kadx.plugins.input.dex.sections

import kadx.plugins.input.dex.utils.DataReader
import java.nio.ByteBuffer

/**
 * DEX v4.1（"dex container"）文件头：一个容器内可打包多个子 DEX。
 *
 * **背景**：
 * 1. [readIfPresent] 通过 header_size >= 120 判断是否为 v4.1 格式（旧版固定 112 字节），
 *    不是则返回 null，由调用方回退到单 DEX 解析；
 * 2. [readSubDexOffsets] 按各子 DEX 的 file_size 顺序累加，算出每个子 DEX 在容器中的起始偏移。
 */
public class DexHeaderV41(
	/** 单个子 DEX 的文件大小（v4.1 header 中记录）*/
	public val fileSize: Int,
	/** 整个容器的总大小 */
	public val containerSize: Int,
	/** v4.1 扩展头在文件中的偏移 */
	public val headerOffset: Int,
) {

	public companion object {
		/**
		 * 尝试按 DEX v4.1 格式解析容器头。
		 * @return 非 v4.1 格式（header_size < 120）时返回 null
		 */
		public fun readIfPresent(content: ByteArray): DexHeaderV41? {
			val headerSize = DataReader.readU4(content, 36)
			if (headerSize < 120) {
				return null
			}
			val fileSize = DataReader.readU4(content, 32)
			val containerSize = DataReader.readU4(content, 112)
			val headerOffset = DataReader.readU4(content, 116)
			return DexHeaderV41(fileSize, containerSize, headerOffset)
		}

		/**
		 * [ByteBuffer] 版本（mmap 路径）：语义与 [readIfPresent] 完全一致。
		 */
		public fun readIfPresent(buf: ByteBuffer): DexHeaderV41? {
			val headerSize = DataReader.readU4(buf, 36)
			if (headerSize < 120) {
				return null
			}
			val fileSize = DataReader.readU4(buf, 32)
			val containerSize = DataReader.readU4(buf, 112)
			val headerOffset = DataReader.readU4(buf, 116)
			return DexHeaderV41(fileSize, containerSize, headerOffset)
		}

		/**
		 * 计算容器内所有子 DEX 的起始偏移列表。
		 *
		 * **算法**：从 offset=0 开始，每次读取当前位置子 DEX 的 file_size（header +32 处），
		 * 累加得到下一个子 DEX 起点；直到越过 containerSize（或文件实际长度）。
		 */
		public fun readSubDexOffsets(content: ByteArray, header: DexHeaderV41): List<Int> {
			var start = 0
			var end = header.fileSize
			val limit = minOf(header.containerSize, content.size)
			val list = ArrayList<Int>()
			while (true) {
				list.add(start)
				start = end
				if (start >= limit) {
					break
				}
				val nextFileSize = DataReader.readU4(content, start + 32)
				end = start + nextFileSize
			}
			return list
		}

		/**
		 * [ByteBuffer] 版本（mmap 路径）：语义与 [readSubDexOffsets] 完全一致。
		 */
		public fun readSubDexOffsets(buf: ByteBuffer, header: DexHeaderV41): List<Int> {
			var start = 0
			var end = header.fileSize
			val limit = minOf(header.containerSize, buf.capacity())
			val list = ArrayList<Int>()
			while (true) {
				list.add(start)
				start = end
				if (start >= limit) {
					break
				}
				val nextFileSize = DataReader.readU4(buf, start + 32)
				end = start + nextFileSize
			}
			return list
		}
	}
}

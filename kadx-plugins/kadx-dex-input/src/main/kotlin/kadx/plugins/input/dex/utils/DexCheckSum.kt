package kadx.plugins.input.dex.utils

import kadx.plugins.input.dex.DexException
import java.util.zip.Adler32

/**
 * DEX 文件 Adler32 校验和验证器。
 *
 * **背景**：DEX header（偏移 +8）存储整个文件（除前 12 字节）的 Adler32 值，
 * [kadx.plugins.input.dex.DexFileLoader] 在解析前先调用本类拦截损坏/截断的文件。
 */
public class DexCheckSum {

	companion object {
		/**
		 * 校验 DEX 内容完整性：文件长度字段与 Adler32 校验和都必须匹配。
		 * @param fileName 文件名（仅用于异常信息）
		 * @param content 完整字节内容
		 * @param offset DEX 数据在 content 中的起始偏移（支持嵌入在其他容器中的 DEX）
		 * @throws DexException 文件截断或校验和不匹配
		 */
		public fun verify(fileName: String, content: ByteArray, offset: Int) {
			if (offset + 32 + 4 > content.size) {
				throw DexException("Dex file truncated, can't read file length, file: $fileName")
			}
			val len = DataReader.readU4(content, offset + 32)
			if (offset + len > content.size) {
				throw DexException("Dex file truncated, length in header: $len, file: $fileName")
			}
			val checksum = DataReader.readU4(content, offset + 8)
			val adler32 = Adler32()
			adler32.update(content, offset + 12, len - 12)
			val fileChecksum = adler32.value.toInt()
			if (checksum != fileChecksum) {
				throw DexException(
					String.format(
						"Bad dex file checksum: 0x%08x, expected: 0x%08x, file: %s",
						fileChecksum,
						checksum,
						fileName,
					),
				)
			}
		}
	}
}

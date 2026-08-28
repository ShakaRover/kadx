package jadx.zip.parser

import java.io.InputStream

import java.nio.ByteBuffer
import java.util.zip.DataFormatException
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream

/**
 * Deflate 解压工具（仅供 JadxZipParser 使用）。
 */
internal class ZipDeflate {
	companion object {
		private const val BUFFER_SIZE = 4096 // 流式解压的缓冲区大小（对应原 Java 的 static final int）

		// 加 @JvmStatic，让未转换的 Java 调用方（JadxZipParser.java）仍可按 ZipDeflate.decompressEntryToBytes(...) 静态访问
		@JvmStatic fun decompressEntryToBytes(buf: ByteBuffer, entry: JadxZipEntry): ByteArray {
			buf.position(entry.getDataStart()) // 把共享 buffer 定位到该条目的数据起始位置
			val entryBuf = buf.slice() // slice 得到独立子缓冲，不影响原 buffer 的状态

			entryBuf.limit((entry.getCompressedSize()).toInt()) // 截取长度限定为该条目的压缩大小（int 强转）
			if (entry.getUncompressedSize() > Int.MAX_VALUE) {
				throw DataFormatException("Entry too large: " + entry.getUncompressedSize())
			}
			val out = ByteArray(entry.getUncompressedSize().toInt()) // 按解压后大小分配输出缓冲
			val inflater = Inflater(true) // true 表示输入是 zlib 格式（带头/校验和），与 zip 规范一致
			inflater.setInput(entryBuf)
			val written = inflater.inflate(out)
			inflater.end()
			if (written != out.size) {
				throw DataFormatException("Unexpected size of decompressed entry: " + entry
						+ ", got: " + written + ", expected: " + out.size)
			}
			return out
		}

		// 加 @JvmStatic，让未转换的 Java 调用方（JadxZipParser.java）仍可按 ZipDeflate.decompressEntryToStream(...) 静态访问
		@JvmStatic fun decompressEntryToStream(buf: ByteBuffer, entry: JadxZipEntry): InputStream {
			val stream = JadxZipParser.bufferToStream(  // 调用 JadxZipParser 的静态方法（原 Java 用 static import）
					buf, entry.getDataStart(), (entry.getCompressedSize()).toInt())
			val inflater = Inflater(true)
			return InflaterInputStream(stream, inflater, BUFFER_SIZE) // 解压流：边读边 inflate
		}
	}
}

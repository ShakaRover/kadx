package jadx.gui.utils

import java.io.IOException
import java.io.InputStream

/**
 * 输入流读取辅助工具。
 *
 * **做什么**：在 Java 11 之前补齐 `InputStream.readNBytes` 与「读满缓冲区」的能力。
 *
 * **为什么用 `object`**：原 Java 全部是静态方法，`object` + `@JvmStatic`
 * 保持 Java 侧静态调用不变。
 */
object IOUtils {

	/**
	 * 读取固定长度字节；读到流末尾（EOF）返回 null。
	 *
	 * 说明：Jadx 升级到 Java 11+ 后本方法可删除（JDK 自带 `readNBytes`）。
	 */
	@JvmStatic
	@Throws(IOException::class)
	fun readNBytes(inputStream: InputStream, len: Int): ByteArray? {
		val payload = ByteArray(len)
		var readSize = 0
		while (true) {
			val read = inputStream.read(payload, readSize, len - readSize)
			if (read == -1) {
				return null
			}
			readSize += read
			if (readSize == len) {
				return payload
			}
		}
	}

	/** 把 [buf] 全部读满，返回实际读取的字节数。 */
	@JvmStatic
	@Throws(IOException::class)
	fun read(inputStream: InputStream, buf: ByteArray): Int = read(inputStream, buf, 0, buf.size)

	/**
	 * 从 [inputStream] 读取 [len] 个字节到 [buf] 的 [off] 偏移处。
	 *
	 * @return 实际读取的字节数（可能小于 [len]，表示提前到达 EOF）
	 */
	@JvmStatic
	@Throws(IOException::class)
	fun read(inputStream: InputStream, buf: ByteArray, off: Int, len: Int): Int {
		var remainingBytes = len
		while (remainingBytes > 0) {
			val start = len - remainingBytes
			val bytesRead = inputStream.read(buf, off + start, remainingBytes)
			if (bytesRead == -1) {
				break
			}
			remainingBytes -= bytesRead
		}
		return len - remainingBytes
	}
}

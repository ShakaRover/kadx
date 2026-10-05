package kadx.zip.io

import java.io.InputStream
import java.nio.ByteBuffer

/**
 * 由 [ByteBuffer] 提供数据的 InputStream。
 *
 * 支持 mark()/reset()（重置到 mark 时的 position），与原 Java 版行为一致；
 * @Synchronized 注解对应原 Java 方法上的 synchronized 修饰符。
 */
class ByteBufferBackedInputStream(private val buf: ByteBuffer) : InputStream() {

	// 调用 mark() 时记录的 buffer 位置，reset() 用它恢复
	private var markedPosition = 0

	override fun read(): Int {
		if (!buf.hasRemaining()) {
			return -1 // buffer 读完返回 EOF
		}
		return buf.get().toInt() and 0xFF // 字节按无符号处理：0..255，与原来 Java 的 `& 0xFF` 等价
	}

	override fun read(bytes: ByteArray, off: Int, len: Int): Int {
		if (!buf.hasRemaining()) {
			return -1
		}
		val readLen = minOf(len, buf.remaining()) // 最多读 buffer 剩余字节数，避免越界
		buf.get(bytes, off, readLen)
		return readLen
	}

	override fun markSupported(): Boolean = true // 本实现支持 mark/reset

	@Synchronized
	override fun mark(readLimit: Int) {
		markedPosition = buf.position()
	}

	@Synchronized
	override fun reset() {
		buf.position(markedPosition)
	}
}

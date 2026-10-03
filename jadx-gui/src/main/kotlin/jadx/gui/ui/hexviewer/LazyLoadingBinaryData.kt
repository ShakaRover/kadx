package jadx.gui.ui.hexviewer

import org.apache.commons.io.IOUtils
import org.apache.commons.lang3.NotImplementedException
import org.exbin.auxiliary.binary_data.BinaryData
import org.exbin.auxiliary.binary_data.array.ByteArrayData
import org.slf4j.LoggerFactory
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * 惰性加载的二进制数据模型（十六进制编辑器的数据源）。
 *
 * **做什么**：把一个 [InputStream] 按 512KB 分块按需读入内存。
 * 只有访问到某个位置时，才把该位置之前的块补读进来，避免一次性把大文件读进内存。
 *
 * **线程模型**：[loadBlock] 用 `@Synchronized` 串行化，
 * 保证后台线程与 UI 线程同时触发读取时不会把块列表写乱。
 *
 * 移植自 jadx 原有的 LazyLoadingBinaryData。
 */
class LazyLoadingBinaryData(private val inputStream: InputStream, expectedSize: Long) : BinaryData {

	private val blockSize = 1024 * 512 // 512 KB

	private var size: Long = expectedSize

	private var readPos = 0L

	private var endOfStreamReached = false

	/**
	 * 已加载的字节块列表。除最后一块外，每块长度都是 [blockSize]。
	 */
	private val blocks: MutableList<ByteArray> = ArrayList()

	init {
		try {
			val available = inputStream.available()
			if (available.toLong() > expectedSize) {
				size = available.toLong()
			}
		} catch (e: IOException) {
			throw RuntimeException(e)
		}
		loadBlock()
	}

	/** 确保 [position] 之前的块都已加载；返回该位置是否可读。 */
	private fun ensurePositionLoaded(position: Long): Boolean {
		while (!endOfStreamReached && readPos < position) {
			loadBlock()
		}
		return readPos >= position
	}

	@Synchronized
	private fun loadBlock() {
		if (endOfStreamReached) {
			return
		}
		try {
			var block = ByteArray(blockSize)
			val bytesRead = inputStream.readNBytes(block, 0, blockSize)
			val lastBlock = bytesRead < blockSize
			if (bytesRead > 0) {
				if (lastBlock) {
					// 最后一块通常不足 blockSize，截断到实际长度
					val newBlock = ByteArray(bytesRead)
					System.arraycopy(block, 0, newBlock, 0, bytesRead)
					block = newBlock
				}
				blocks.add(block)
				readPos += bytesRead
				if (readPos >= size) {
					size = readPos
				}
			}
			if (lastBlock) {
				endOfStreamReached = true
			}
			LOG.trace("loaded {} bytes - readPos={} size={} endOfStreamReached={}", bytesRead, readPos, size, endOfStreamReached)
		} catch (e: IOException) {
			endOfStreamReached = true
			LOG.error("Error reading from input stream", e)
		}
	}

	override fun isEmpty(): Boolean = size == 0L

	override fun getDataSize(): Long = size

	override fun getByte(position: Long): Byte {
		if (!ensurePositionLoaded(position)) {
			throw RuntimeException("Unreachable position: $position")
		}
		val blockNum = (position / blockSize).toInt()
		val blockOffset = (position % blockSize).toInt()
		return blocks[blockNum][blockOffset]
	}

	override fun copy(): BinaryData = throw NotImplementedException()

	override fun copy(startFrom: Long, length: Long): BinaryData {
		val data = ByteArray(length.toInt())
		copyToArray(startFrom, data, 0, length.toInt())
		return ByteArrayData(data)
	}

	override fun copyToArray(startFrom: Long, target: ByteArray, offset: Int, length: Int) {
		val endPosition = startFrom + length
		if (!ensurePositionLoaded(endPosition)) {
			throw RuntimeException("Unreachable position: $endPosition")
		}
		var blockNum = (startFrom / blockSize).toInt()
		var blockOffset = (startFrom % blockSize).toInt()
		var remaining = length
		var targetPos = offset
		while (remaining > 0) {
			val block = blocks[blockNum]
			val copyLength = Math.min(remaining, block.size - blockOffset)
			System.arraycopy(block, blockOffset, target, targetPos, copyLength)
			remaining -= copyLength
			targetPos += copyLength
			blockNum++
			blockOffset = 0
		}
	}

	override fun saveToStream(outputStream: OutputStream): Unit = throw UnsupportedOperationException()

	override fun getDataInputStream(): InputStream = throw UnsupportedOperationException()

	override fun dispose() {
		IOUtils.closeQuietly(inputStream)
	}

	companion object {
		private val LOG = LoggerFactory.getLogger(LazyLoadingBinaryData::class.java)
	}
}

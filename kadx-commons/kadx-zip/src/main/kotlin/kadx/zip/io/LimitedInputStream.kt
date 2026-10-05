package kadx.zip.io

import java.io.FilterInputStream
import java.io.InputStream

/**
 * 带总量上限的 InputStream（防 zip 炸弹用）。
 *
 * 累计读取/跳过的字节数超过 [maxSize] 时抛出 IllegalStateException("Read limit exceeded")；
 * mark()/reset() 会同步回退计数，保证 reset 后限额重新生效。
 */
class LimitedInputStream(inputStream: InputStream, private val maxSize: Long) : FilterInputStream(inputStream) {

	// 当前累计读取的字节数
	private var currentPos = 0L

	// 调用 mark() 时的位置快照，reset() 时用它回退 currentPos
	private var markPos = 0L

	override fun read(): Int {
		val data = super.read()
		if (data != -1) {
			addAndCheckPos(1) // 读到 1 字节就累计 1（EOF 的 -1 不计入）
		}
		return data
	}

	override fun read(b: ByteArray, off: Int, len: Int): Int {
		val count = super.read(b, off, len)
		if (count > 0) {
			addAndCheckPos(count.toLong()) // Java 里 int 自动拓宽为 long，Kotlin 需要显式转换
		}
		return count
	}

	override fun skip(n: Long): Long {
		val skipped = super.skip(n)
		if (skipped > 0) {
			addAndCheckPos(skipped) // 跳过的字节同样计入总量，防止用 skip 绕开限额
		}
		return skipped
	}

	override fun mark(readLimit: Int) {
		super.mark(readLimit)
		markPos = currentPos // 记住打标记时的读取位置
	}

	override fun reset() {
		super.reset()
		currentPos = markPos // 回退累计计数，使限额检查重新生效
	}

	private fun addAndCheckPos(count: Long) {
		currentPos += count
		if (currentPos > maxSize) {
			throw IllegalStateException("Read limit exceeded")
		}
	}
}

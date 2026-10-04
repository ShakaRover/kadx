package jadx.gui.ui.hexviewer

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.FilterInputStream

/**
 * 惰性加载二进制数据的块边界测试（PR #2948）。
 *
 * **做什么**：校验读取恰好位于块边界（512KB）的字节时不会漏读下一块，
 * 以及越界位置会抛出 [IndexOutOfBoundsException]。
 */
class LazyLoadingBinaryDataTest {

	@Test
	fun getByteLoadsByteAtBlockBoundary() {
		val source = ByteArray(BLOCK_SIZE + 2)
		source[BLOCK_SIZE - 1] = 1
		source[BLOCK_SIZE] = 2
		source[BLOCK_SIZE + 1] = 3
		val data = LazyLoadingBinaryData(ByteArrayInputStream(source), source.size.toLong())

		assertThat(data.getByte((BLOCK_SIZE - 1).toLong())).isEqualTo(1.toByte())
		assertThat(data.getByte(BLOCK_SIZE.toLong())).isEqualTo(2.toByte())
		assertThat(data.getByte((BLOCK_SIZE + 1).toLong())).isEqualTo(3.toByte())
	}

	@Test
	fun getByteLoadsByteAtBlockBoundaryForStreamWithUnknownSize() {
		val source = ByteArray(BLOCK_SIZE + 2)
		source[BLOCK_SIZE] = 2
		val data = LazyLoadingBinaryData(
			object : FilterInputStream(ByteArrayInputStream(source)) {
				override fun available(): Int = -1
			},
			0,
		)

		assertThat(data.getByte(BLOCK_SIZE.toLong())).isEqualTo(2.toByte())
	}

	@Test
	fun getByteRejectsPositionsOutsideData() {
		val source = ByteArray(BLOCK_SIZE + 1)
		source.fill(7)
		val data = LazyLoadingBinaryData(ByteArrayInputStream(source), source.size.toLong())

		assertThatThrownBy { data.getByte(-1) }.isInstanceOf(IndexOutOfBoundsException::class.java)
		assertThatThrownBy { data.getByte(source.size.toLong()) }.isInstanceOf(IndexOutOfBoundsException::class.java)
	}

	companion object {
		private const val BLOCK_SIZE = 1024 * 512
	}
}

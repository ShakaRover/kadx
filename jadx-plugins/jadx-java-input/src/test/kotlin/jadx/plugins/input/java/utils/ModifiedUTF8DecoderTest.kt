package jadx.plugins.input.java.utils

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/*
 * TODO: find a way to enter 6-bytes char decode branch
 */
class ModifiedUTF8DecoderTest {

	@Test
	fun test() {
		val str = "aÆřᛒቶ北𝄠😀🨄𐆙"
		// 原 Java byte[] 字面量含负值（有符号字节），这里用 IntArray 转换保持完全一致
		val mUTF8Bytes = intArrayOf(
			97, -61, -122, -59, -103, -31, -101, -110, -31, -119, -74, -17,
			-91, -93, -19, -96, -76, -19, -76, -96, -19, -96, -67, -19, -72,
			-128, -19, -96, -66, -19, -72, -124, -19, -96, -128, -19, -74, -103,
		).map { it.toByte() }.toByteArray()
		assertThat(ModifiedUTF8Decoder.decodeString(mUTF8Bytes)).isEqualTo(str)
	}

	@Test
	fun testASCIIOnly() {
		val str = "Hello, world!"
		val mUTF8Bytes = intArrayOf(72, 101, 108, 108, 111, 44, 32, 119, 111, 114, 108, 100, 33)
			.map { it.toByte() }.toByteArray()
		assertThat(ModifiedUTF8Decoder.decodeString(mUTF8Bytes)).isEqualTo(str)
	}
}

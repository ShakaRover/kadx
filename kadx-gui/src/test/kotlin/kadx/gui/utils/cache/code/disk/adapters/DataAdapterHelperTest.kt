package kadx.gui.utils.cache.code.disk.adapters

import kadx.gui.cache.code.disk.adapters.DataAdapterHelper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInput
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

/**
 * 磁盘缓存基础适配器测试。
 *
 * **做什么**：校验变长无符号整数（UVInt）的写入 / 读取往返一致。
 */
class DataAdapterHelperTest {

	@Test
	@Throws(IOException::class)
	fun uvInt() {
		checkUVIntFor(0)
		checkUVIntFor(7)
		checkUVIntFor(0x7f)
		checkUVIntFor(0x80)
		checkUVIntFor(0x256)
		checkUVIntFor(Byte.MAX_VALUE.toInt())
		checkUVIntFor(Short.MAX_VALUE.toInt())
		checkUVIntFor(Int.MAX_VALUE)
	}

	@Throws(IOException::class)
	private fun checkUVIntFor(value: Int) {
		assertThat(writeReadUVInt(value)).isEqualTo(value)
	}

	@Throws(IOException::class)
	private fun writeReadUVInt(value: Int): Int {
		val byteOut = ByteArrayOutputStream()
		val out = DataOutputStream(byteOut)
		DataAdapterHelper.writeUVInt(out, value)

		val input: DataInput = DataInputStream(ByteArrayInputStream(byteOut.toByteArray()))
		return DataAdapterHelper.readUVInt(input)
	}
}

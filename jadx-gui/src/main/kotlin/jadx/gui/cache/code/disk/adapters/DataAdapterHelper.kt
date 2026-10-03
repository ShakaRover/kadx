package jadx.gui.cache.code.disk.adapters

import java.io.DataInput
import java.io.DataOutput
import java.io.IOException

/**
 * 磁盘缓存二进制格式的通用编码工具。
 *
 * **做什么**：提供可空字符串与“无符号变长整数（ULEB128）”的读写。
 * 这些编码是持久化格式的一部分，必须保持字节级兼容。
 *
 * **为什么用 `open class` + `companion object`**：原 Java 是普通类且方法均为静态，
 * 且 [jadx.gui.cache.usage.UsageFileAdapter] 继承它。保留可继承形态，并把静态方法
 * 放进 companion 并加 `@JvmStatic`，Java 调用方（`CodeMetadataAdapter`、单元测试）
 * 写法 `DataAdapterHelper.writeUVInt(...)` 不变。
 */
open class DataAdapterHelper {

	companion object {

		/** 写入可空 UTF 字符串：先写 1 字节标记（0=null，1=有值）。 */
		@Throws(IOException::class)
		fun writeNullableUTF(out: DataOutput, str: String?) {
			if (str == null) {
				out.writeByte(0)
			} else {
				out.writeByte(1)
				out.writeUTF(str)
			}
		}

		/** 读取可空 UTF 字符串（与 [writeNullableUTF] 对应）。 */
		@Throws(IOException::class)
		fun readNullableUTF(input: DataInput): String? {
			if (input.readByte().toInt() == 0) {
				return null
			}
			return input.readUTF()
		}

		/**
		 * 写出无符号变长整数（ULEB128 编码）。
		 *
		 * **编码规则**：每次取低 7 位，若还有更高位则置最高位为 1 表示“后续还有字节”。
		 */
		@Throws(IOException::class)
		fun writeUVInt(out: DataOutput, value: Int) {
			if (value < 0) {
				throw IllegalArgumentException("Expect value >= 0, got: $value")
			}
			var current = value
			var next = value
			while (true) {
				next = next ushr 7
				if (next == 0) {
					// 最后一个字节
					out.writeByte(current and 0x7f)
					return
				}
				out.writeByte((current and 0x7f) or 0x80)
				current = next
			}
		}

		/**
		 * 读入无符号变长整数（ULEB128 编码）。
		 *
		 * **解码规则**：逐字节取出低 7 位并左移累加，直到某个字节最高位为 0 为止。
		 */
		@Throws(IOException::class)
		fun readUVInt(input: DataInput): Int {
			var result = 0
			var shift = 0
			while (true) {
				val v = input.readByte()
				result = result or ((v.toInt() and 0x7f) shl shift)
				shift += 7
				if ((v.toInt() and 0x80) != 0x80) {
					return result
				}
			}
		}
	}
}

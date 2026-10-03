package jadx.gui.cache.code.disk.adapters

import jadx.api.metadata.annotations.InsnCodeOffset
import java.io.DataInput
import java.io.DataOutput
import java.io.IOException

/**
 * [InsnCodeOffset] 的二进制适配器：只保存字节码偏移。
 *
 * **格式**：ULEB128 编码的偏移值。单例 [INSTANCE] 通过 `@JvmField` 暴露为静态字段，
 * 与 Java 侧 `InsnCodeOffsetAdapter.INSTANCE` 写法一致。
 */
class InsnCodeOffsetAdapter : DataAdapter<InsnCodeOffset> {

	companion object {
		@JvmField
		val INSTANCE = InsnCodeOffsetAdapter()
	}

	@Throws(IOException::class)
	override fun write(out: DataOutput, value: InsnCodeOffset) {
		DataAdapterHelper.writeUVInt(out, value.getOffset())
	}

	@Throws(IOException::class)
	override fun read(input: DataInput): InsnCodeOffset = InsnCodeOffset(DataAdapterHelper.readUVInt(input))
}

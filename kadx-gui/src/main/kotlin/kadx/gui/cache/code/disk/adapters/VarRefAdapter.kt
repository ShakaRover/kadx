package kadx.gui.cache.code.disk.adapters

import kadx.api.metadata.annotations.VarRef
import java.io.DataInput
import java.io.DataOutput
import java.io.IOException

/**
 * [VarRef] 的二进制适配器：只保存“变量节点在代码元数据中的位置”。
 *
 * **格式**：ULEB128 编码的引用位置。读取时用 [VarRef.fromPos] 还原为固定位置引用。
 */
class VarRefAdapter : DataAdapter<VarRef> {

	companion object {
		val INSTANCE = VarRefAdapter()
	}

	@Throws(IOException::class)
	override fun write(out: DataOutput, value: VarRef) {
		val refPos = value.getRefPos()
		DataAdapterHelper.writeUVInt(out, refPos)
	}

	@Throws(IOException::class)
	override fun read(input: DataInput): VarRef {
		val refPos = DataAdapterHelper.readUVInt(input)
		return VarRef.fromPos(refPos)
	}
}

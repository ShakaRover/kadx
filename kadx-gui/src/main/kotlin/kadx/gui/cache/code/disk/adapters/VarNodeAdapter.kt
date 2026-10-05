package kadx.gui.cache.code.disk.adapters

import kadx.api.metadata.annotations.VarNode
import kadx.core.dex.instructions.args.ArgType
import kadx.core.dex.nodes.MethodNode
import java.io.DataInput
import java.io.DataOutput
import java.io.IOException

/**
 * [VarNode] 的二进制适配器：保存局部变量的完整信息。
 *
 * **格式**：所属方法（[MethodNodeAdapter]）、寄存器号、SSA 版本、类型（[ArgTypeAdapter]）、
 * 变量名（可空）。寄存器号 / SSA 版本用 ULEB128 编码。
 */
class VarNodeAdapter(private val mthAdapter: MethodNodeAdapter) : DataAdapter<VarNode> {

	@Throws(IOException::class)
	override fun write(out: DataOutput, value: VarNode) {
		mthAdapter.write(out, value.getMth())
		DataAdapterHelper.writeUVInt(out, value.getReg())
		DataAdapterHelper.writeUVInt(out, value.getSsa())
		ArgTypeAdapter.INSTANCE.write(out, value.getType())
		DataAdapterHelper.writeNullableUTF(out, value.getName())
	}

	@Throws(IOException::class)
	override fun read(input: DataInput): VarNode {
		val mth = mthAdapter.read(input)
		val reg = DataAdapterHelper.readUVInt(input)
		val ssa = DataAdapterHelper.readUVInt(input)
		val type = checkNotNull(ArgTypeAdapter.INSTANCE.read(input))
		val name = DataAdapterHelper.readNullableUTF(input)
		return VarNode(mth, reg, ssa, type, name)
	}
}

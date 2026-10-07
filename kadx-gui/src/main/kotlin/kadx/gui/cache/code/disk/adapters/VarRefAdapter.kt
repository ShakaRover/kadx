package kadx.gui.cache.code.disk.adapters

import kadx.api.metadata.annotations.VarRef
import java.io.DataInput
import java.io.DataOutput
import java.io.IOException

/**
 * [VarRef] 的二进制适配器：只保存“变量节点在代码元数据中的位置”。
 *
 * **格式**：ULEB128 编码的引用位置。
 *
 * **位置 0 的语义与处理**：位置 0 表示“变量的声明位置未知”——`VarNode.defPos` 初值为 0，
 * 只有当代码生成阶段真的发出了声明（`attachDeclaration`）并被 `ClassNode`
 * 回填（`setDefPosition`）后才会变成非 0。因此“只有使用点、声明被后续 pass 删除”的变量
 * 会一直保持 0。这种引用既无法解析（[VarRef.fromPos] 明确拒绝 0），也无法在缓存里表达，
 * 所以：
 * - 写入侧（[CodeAnnotationAdapter.write]）把它当作 `null` 落盘，不产生 VAR_REF 条目；
 * - 读取侧在此处也容忍**已经写在磁盘上的旧缓存**：遇到 0 返回 `null`
 *   （[kadx.gui.cache.code.disk.CodeMetadataAdapter] 的 readAnnotations 本来就会跳过 null 注解），
 *   而不是抛异常把整个类的元数据读失败、进而回退成重新反编译。
 *
 * 注意：这里只消费 1 个 UVInt，所以即使值为 0，字节流仍是对齐的，跳过该条目是安全的。
 */
class VarRefAdapter : DataAdapter<VarRef?> {

	companion object {
		val INSTANCE = VarRefAdapter()

		/**
		 * 该引用能否持久化：位置 0 表示“声明位置未知”，既无法在缓存里表达也无法解析，
		 * 写入侧应按 `null` 处理（见 [kadx.gui.cache.code.disk.adapters.CodeAnnotationAdapter.write]）。
		 */
		fun canPersist(value: VarRef): Boolean = value.getRefPos() != 0
	}

	@Throws(IOException::class)
	override fun write(out: DataOutput, value: VarRef?) {
		val refPos = value?.getRefPos() ?: 0
		DataAdapterHelper.writeUVInt(out, refPos)
	}

	@Throws(IOException::class)
	override fun read(input: DataInput): VarRef? {
		val refPos = DataAdapterHelper.readUVInt(input)
		// 位置未知：无法构造合法 VarRef，按“无注解”处理（调用方会跳过 null）
		return if (refPos == 0) null else VarRef.fromPos(refPos)
	}
}

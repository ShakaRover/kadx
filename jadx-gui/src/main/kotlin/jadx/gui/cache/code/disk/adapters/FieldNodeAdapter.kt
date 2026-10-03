package jadx.gui.cache.code.disk.adapters

import jadx.core.dex.nodes.FieldNode
import jadx.core.dex.nodes.RootNode
import java.io.DataInput
import java.io.DataOutput
import java.io.IOException

/**
 * [FieldNode] 的二进制适配器：保存“声明类原始名 + 字段短 id”。
 *
 * **格式**：`writeUTF(声明类名)`、`writeUTF(字段短 id)`。读取时若类或字段找不到会抛异常，
 * 表示缓存与当前工程不一致（与原 Java 行为一致）。
 */
class FieldNodeAdapter(private val root: RootNode) : DataAdapter<FieldNode> {

	@Throws(IOException::class)
	override fun write(out: DataOutput, value: FieldNode) {
		val fieldInfo = value.getFieldInfo()
		out.writeUTF(fieldInfo.declClass.rawName)
		out.writeUTF(fieldInfo.shortId)
	}

	@Throws(IOException::class)
	override fun read(input: DataInput): FieldNode {
		val cls = input.readUTF()
		val sign = input.readUTF()
		val clsNode = root.resolveRawClass(cls) ?: throw RuntimeException("Class not found: $cls")
		return clsNode.searchFieldByShortId(sign) ?: throw RuntimeException("Field not found: $cls.$sign")
	}
}

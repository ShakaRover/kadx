package jadx.gui.cache.code.disk.adapters

import jadx.core.dex.nodes.MethodNode
import jadx.core.dex.nodes.RootNode
import java.io.DataInput
import java.io.DataOutput
import java.io.IOException

/**
 * [MethodNode] 的二进制适配器：保存“声明类原始名 + 方法短 id”。
 *
 * **格式**：`writeUTF(声明类名)`、`writeUTF(方法短 id)`。读取时用
 * [RootNode.resolveDirectMethod] 反查，找不到会抛异常。
 */
class MethodNodeAdapter(private val root: RootNode) : DataAdapter<MethodNode> {

	@Throws(IOException::class)
	override fun write(out: DataOutput, value: MethodNode) {
		val methodInfo = value.getMethodInfo()
		out.writeUTF(methodInfo.declClass.rawName)
		out.writeUTF(methodInfo.shortId)
	}

	@Throws(IOException::class)
	override fun read(input: DataInput): MethodNode {
		val cls = input.readUTF()
		val sign = input.readUTF()
		return root.resolveDirectMethod(cls, sign)
	}
}

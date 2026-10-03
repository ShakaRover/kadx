package jadx.gui.cache.code.disk.adapters

import jadx.core.dex.nodes.ClassNode
import jadx.core.dex.nodes.RootNode
import java.io.DataInput
import java.io.DataOutput
import java.io.IOException

/**
 * [ClassNode] 的二进制适配器：只保存类的原始名，读取时通过 [RootNode] 反查。
 *
 * **格式**：`writeUTF(原始类名)`。解析不到的类在读取时返回 `null`（原 Java 语义）。
 */
class ClassNodeAdapter(private val root: RootNode) : DataAdapter<ClassNode?> {

	@Throws(IOException::class)
	override fun write(out: DataOutput, value: ClassNode?) {
		out.writeUTF(checkNotNull(value).rawName)
	}

	@Throws(IOException::class)
	override fun read(input: DataInput): ClassNode? = root.resolveRawClass(input.readUTF())
}

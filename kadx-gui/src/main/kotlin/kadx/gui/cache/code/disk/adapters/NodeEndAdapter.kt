package kadx.gui.cache.code.disk.adapters

import kadx.api.metadata.annotations.NodeEnd
import java.io.DataInput
import java.io.DataOutput
import java.io.IOException

/**
 * [NodeEnd] 的二进制适配器：结束标记是全局单例，不需要写入任何数据。
 *
 * **格式**：无字节负载，读取时直接返回 [NodeEnd.VALUE]。
 */
class NodeEndAdapter : DataAdapter<NodeEnd> {

	@Throws(IOException::class)
	override fun write(out: DataOutput, value: NodeEnd) {
		// 无负载
	}

	@Throws(IOException::class)
	override fun read(input: DataInput): NodeEnd = NodeEnd.VALUE
}

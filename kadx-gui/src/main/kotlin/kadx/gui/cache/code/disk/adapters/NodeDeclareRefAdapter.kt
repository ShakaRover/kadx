package kadx.gui.cache.code.disk.adapters

import kadx.api.metadata.ICodeNodeRef
import kadx.api.metadata.annotations.NodeDeclareRef
import java.io.DataInput
import java.io.DataOutput
import java.io.IOException

/**
 * [NodeDeclareRef] 的二进制适配器：保存被声明节点 + 声明位置。
 *
 * **格式**：先用 [CodeAnnotationAdapter] 写入被引用的节点，再写 ULEB128 声明位置。
 * 读取时同时把位置回填到节点上，这样在“只加载元数据、不真正反编译”的场景下，
 * 节点的定义位置也能恢复。
 */
class NodeDeclareRefAdapter(private val refAdapter: CodeAnnotationAdapter) : DataAdapter<NodeDeclareRef> {

	@Throws(IOException::class)
	override fun write(out: DataOutput, value: NodeDeclareRef) {
		// getNode() 为非空类型，无需再判空（原 Java 的判空在新类型下已由编译器保证）
		refAdapter.write(out, value.getNode())
		DataAdapterHelper.writeUVInt(out, value.getDefPos())
	}

	@Throws(IOException::class)
	override fun read(input: DataInput): NodeDeclareRef {
		val ref = checkNotNull(refAdapter.read(input)) as ICodeNodeRef
		val defPos = DataAdapterHelper.readUVInt(input)
		val nodeDeclareRef = NodeDeclareRef(ref)
		nodeDeclareRef.setDefPos(defPos)
		// 未真正反编译时，也要恢复节点的定义位置
		ref.setDefPosition(defPos)
		return nodeDeclareRef
	}
}

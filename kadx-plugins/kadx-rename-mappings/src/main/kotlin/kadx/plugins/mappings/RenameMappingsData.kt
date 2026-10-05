package kadx.plugins.mappings

import kadx.api.plugins.input.data.attributes.IKadxAttrType
import kadx.api.plugins.input.data.attributes.IKadxAttribute
import kadx.core.dex.nodes.RootNode
import net.fabricmc.mappingio.tree.MappingTreeView

/**
 * 重命名映射数据：把加载好的 [MappingTreeView] 挂到 RootNode 的属性表上。
 *
 * **背景**：实现 IKadxAttribute（Java 接口，通配符签名），[DATA] 是属性类型常量；
 * getData/getTree 供各 Pass 读取当前会话的映射树。
 */
public class RenameMappingsData(val mappings: MappingTreeView) : IKadxAttribute {

	override val attrType: IKadxAttrType<RenameMappingsData> get() = DATA

	public companion object {
		private val DATA: IKadxAttrType<RenameMappingsData> = IKadxAttrType.create()

		public fun getData(root: RootNode): RenameMappingsData? = root.attributes.get(DATA)

		public fun getTree(root: RootNode): MappingTreeView? {
			val data = getData(root)
			return if (data == null) null else data.mappings
		}
	}
}

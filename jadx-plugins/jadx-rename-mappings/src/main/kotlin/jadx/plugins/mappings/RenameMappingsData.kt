package jadx.plugins.mappings

import jadx.api.plugins.input.data.attributes.IJadxAttrType
import jadx.api.plugins.input.data.attributes.IJadxAttribute
import jadx.core.dex.nodes.RootNode
import net.fabricmc.mappingio.tree.MappingTreeView

/**
 * 重命名映射数据：把加载好的 [MappingTreeView] 挂到 RootNode 的属性表上。
 *
 * **背景**：实现 IJadxAttribute（Java 接口，通配符签名），[DATA] 是属性类型常量；
 * getData/getTree 供各 Pass 读取当前会话的映射树。
 */
public class RenameMappingsData(val mappings: MappingTreeView) : IJadxAttribute {

	override val attrType: IJadxAttrType<RenameMappingsData> get() = DATA

	public companion object {
		private val DATA: IJadxAttrType<RenameMappingsData> = IJadxAttrType.create()

		public fun getData(root: RootNode): RenameMappingsData? = root.getAttributes().get(DATA)

		public fun getTree(root: RootNode): MappingTreeView? {
			val data = getData(root)
			return if (data == null) null else data.mappings
		}
	}
}

package kadx.gui.plugins.context

import kadx.gui.treemodel.JNode
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

/**
 * 「输入」树区域的自定义分类。
 *
 * **做什么**：插件实现本接口后，可把匹配的输入文件归入自己的分类节点，
 * 而不是平铺在普通文件列表里。
 *
 * **为什么保持为普通接口**：这是插件扩展点，实现方可能在外部模块（甚至 Java），
 * 因此保持原有方法名与形态；`List` 在字节码层面仍是 `java.util.List`，
 * Java 实现方用 `List<Path>` 覆写即可。
 */
@ApiStatus.Experimental
interface ITreeInputCategory {

	/**
	 * 判断某个文件是否应归入本分类。
	 */
	fun filesFilter(file: Path): Boolean

	/**
	 * 为过滤后的文件构建分类节点。
	 * 可能以空列表调用（空分类也可能有意义）。
	 *
	 * @return 分类节点；不需要时返回 `null`
	 */
	fun buildInputNode(files: List<Path>): JNode?
}

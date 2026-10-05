package kadx.api.deobf

import kadx.core.dex.nodes.ClassNode
import kadx.core.dex.nodes.FieldNode
import kadx.core.dex.nodes.MethodNode
import kadx.core.dex.nodes.PackageNode
import kadx.core.dex.nodes.RootNode

/**
 * 重命名条件：决定某个节点是否应该被反混淆改名。
 *
 * **做什么**：反混淆器对每个包 / 类 / 字段 / 方法询问本接口的 `shouldRename`，
 * 返回 true 才执行改名。
 *
 * **Kotlin 转换说明**：全部保持显式函数形态，Java 实现方与调用方零改动。
 */
interface IRenameCondition {

	/** 初始化：反混淆开始前调用一次。 */
	fun init(root: RootNode)

	/** 包是否需要改名。 */
	fun shouldRename(pkg: PackageNode): Boolean

	/** 类是否需要改名。 */
	fun shouldRename(cls: ClassNode): Boolean

	/** 字段是否需要改名。 */
	fun shouldRename(fld: FieldNode): Boolean

	/** 方法是否需要改名。 */
	fun shouldRename(mth: MethodNode): Boolean
}

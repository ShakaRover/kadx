package kadx.api.deobf.impl

import kadx.api.deobf.IRenameCondition
import kadx.core.dex.nodes.ClassNode
import kadx.core.dex.nodes.FieldNode
import kadx.core.dex.nodes.MethodNode
import kadx.core.dex.nodes.PackageNode
import kadx.core.dex.nodes.RootNode

/**
 * 最简单的重命名条件：所有节点一律改名。
 *
 * **为什么存在**：把映射表（mapping）应用到 dex 树时，需要无条件使用映射表里的名字，
 * 这时就用这个单例条件。
 *
 * **Kotlin 转换说明**：原来的 `public static final INSTANCE` 字段改为
 * `companion object` 内的 `@JvmField val INSTANCE`，Java 侧 `AlwaysRename.INSTANCE`
 * 与 Kotlin 侧写法都保持不变。私有构造器防止外部再创建实例。
 */
class AlwaysRename private constructor() : IRenameCondition {

	companion object {
		/** 共享单例（比较时请用 `===`）。 */
		@JvmField
		val INSTANCE: IRenameCondition = AlwaysRename()
	}

	override fun init(root: RootNode) {
		// 无需初始化
	}

	override fun shouldRename(pkg: PackageNode): Boolean = true

	override fun shouldRename(cls: ClassNode): Boolean = true

	override fun shouldRename(fld: FieldNode): Boolean = true

	override fun shouldRename(mth: MethodNode): Boolean = true
}

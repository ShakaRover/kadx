package kadx.api.deobf

import kadx.core.dex.nodes.ClassNode
import kadx.core.dex.nodes.FieldNode
import kadx.core.dex.nodes.MethodNode
import kadx.core.dex.nodes.PackageNode
import kadx.core.dex.nodes.RootNode

/**
 * 反混淆“别名提供者”：为包 / 类 / 字段 / 方法生成新的名字。
 *
 * **做什么**：kadx 在反混淆（deobfuscation）时，对每个需要改名的节点调用这里对应的
 * `forXxx` 方法，拿到新名字后写回节点。
 *
 * **为什么保留默认方法**：`init` 与 `initIndexes` 都是可选的初始化钩子，
 * 插件实现类（可能是 Java 写的）通常不需要覆写。Kotlin 接口带方法体的成员会被编译成
 * Java `default` 方法，因此 Java 实现方可以像以前一样省略它们，公共 API 保持不变。
 *
 * **Kotlin 转换说明**：全部保持显式函数形态（而非属性），Java 调用方零改动。
 */
interface IAliasProvider {

	/** 可选初始化：在反混淆开始前调用一次，可在此预扫描整棵 dex 树。 */
	fun init(root: RootNode) {
		// 默认不做任何事
	}

	/** 为包生成别名；返回 null 表示不重命名（如映射表中没有该包）。 */
	fun forPackage(pkg: PackageNode): String?

	/** 为类生成别名；返回 null 表示不重命名。 */
	fun forClass(cls: ClassNode): String?

	/** 为字段生成别名；返回 null 表示不重命名。 */
	fun forField(fld: FieldNode): String?

	/** 为方法生成别名；返回 null 表示不重命名。 */
	fun forMethod(mth: MethodNode): String?

	/**
	 * 可选方法：设置从映射表加载到的各类型初始最大索引，
	 * 避免新生成的名字与映射表里已有的名字冲突。
	 */
	fun initIndexes(pkg: Int, cls: Int, fld: Int, mth: Int) {
		// 默认不做任何事
	}
}

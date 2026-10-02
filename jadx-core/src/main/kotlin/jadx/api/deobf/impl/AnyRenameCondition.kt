package jadx.api.deobf.impl

import jadx.api.deobf.IRenameCondition
import jadx.core.dex.nodes.ClassNode
import jadx.core.dex.nodes.FieldNode
import jadx.core.dex.nodes.IDexNode
import jadx.core.dex.nodes.MethodNode
import jadx.core.dex.nodes.PackageNode
import jadx.core.dex.nodes.RootNode
import java.util.function.BiPredicate

/**
 * 按外部传入的谓词决定是否改名的通用条件。
 *
 * **做什么**：对每个节点，取它的“当前别名 + 节点自身”交给 [BiPredicate]，
 * 由调用方自定义规则（例如“名字以某个前缀开头才改名”）。
 *
 * **Kotlin 转换说明**：谓词字段保持 `java.util.function.BiPredicate` 类型，
 * 保证 Java 调用方（lambda / 方法引用）无需改动。包名取的是
 * [PackageNode.getAliasPkgInfo]（别名包信息）而非原始包信息。
 */
class AnyRenameCondition(private val predicate: BiPredicate<String, IDexNode>) : IRenameCondition {

	override fun init(root: RootNode) {
		// 无需初始化
	}

	override fun shouldRename(pkg: PackageNode): Boolean = predicate.test(pkg.getAliasPkgInfo().name, pkg)

	override fun shouldRename(cls: ClassNode): Boolean = predicate.test(cls.alias, cls)

	override fun shouldRename(fld: FieldNode): Boolean = predicate.test(fld.getAlias(), fld)

	override fun shouldRename(mth: MethodNode): Boolean = predicate.test(mth.getAlias(), mth)
}

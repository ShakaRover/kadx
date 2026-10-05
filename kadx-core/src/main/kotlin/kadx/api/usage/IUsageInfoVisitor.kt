package kadx.api.usage

import kadx.core.dex.info.MethodInfo
import kadx.core.dex.nodes.ClassNode
import kadx.core.dex.nodes.FieldNode
import kadx.core.dex.nodes.MethodNode

/**
 * 使用信息访问者：遍历一次 usage 分析结果时，按不同类别回调。
 *
 * **做什么**：把“类依赖 / 类被使用 / 类被哪些方法使用 / 字段被哪些方法使用 /
 * 方法被使用 / 方法使用了谁 / 未解析方法引用 / 是否自调用”等数据，分门别类地
 * 推送给实现方（例如 GUI 的磁盘缓存序列化）。
 *
 * **为什么保持接口方法形态**：本接口由 kadx-gui（Java）实现，方法名与参数类型
 * 必须与原 Java 完全一致。参数中的元素类型（[ClassNode] / [MethodNode] /
 * [FieldNode] / [MethodInfo]）都是 final 类，因此 Kotlin 生成的 JVM 签名不含
 * 通配符，Java 侧用 `List<ClassNode>` 覆写即可。
 */
interface IUsageInfoVisitor {

	/** [cls] 依赖的类列表。 */
	fun visitClassDeps(cls: ClassNode, deps: List<ClassNode>)

	/** 使用了 [cls] 的类列表。 */
	fun visitClassUsage(cls: ClassNode, usage: List<ClassNode>)

	/** 在哪些方法里使用了 [cls]。 */
	fun visitClassUseInMethods(cls: ClassNode, methods: List<MethodNode>)

	/** 字段 [fld] 被哪些方法使用。 */
	fun visitFieldsUsage(fld: FieldNode, methods: List<MethodNode>)

	/** 方法 [mth] 被哪些方法使用。 */
	fun visitMethodsUsage(mth: MethodNode, methods: List<MethodNode>)

	/** 方法 [mth] 使用了哪些方法。 */
	fun visitMethodsUses(mth: MethodNode, methods: List<MethodNode>)

	/** 方法 [mth] 调用了哪些无法解析的方法签名。 */
	fun visitUnresolvedMethodsUsage(mth: MethodNode, methods: List<MethodInfo>)

	/** 方法 [mth] 是否调用了自身。 */
	fun visitIsSelfCall(mth: MethodNode, isSelfCall: Boolean)

	/** 遍历结束。 */
	fun visitComplete()
}

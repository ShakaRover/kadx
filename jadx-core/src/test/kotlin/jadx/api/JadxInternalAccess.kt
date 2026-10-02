package jadx.api

import jadx.core.dex.nodes.ClassNode
import jadx.core.dex.nodes.FieldNode
import jadx.core.dex.nodes.MethodNode
import jadx.core.dex.nodes.RootNode

/**
 * 测试辅助入口：把 [JadxDecompiler] 上标注为内部/不稳定的转换 API 暴露给测试代码。
 *
 * 用 `object` + `@JvmStatic` 保持 Java 调用方 `JadxInternalAccess.xxx(...)` 的静态写法不变。
 */
object JadxInternalAccess {

	@JvmStatic
	fun getRoot(d: JadxDecompiler): RootNode = checkNotNull(d.getRoot())

	@JvmStatic
	fun convertClassNode(d: JadxDecompiler, clsNode: ClassNode): JavaClass = d.convertClassNode(clsNode)

	@JvmStatic
	fun convertMethodNode(d: JadxDecompiler, mthNode: MethodNode): JavaMethod = d.convertMethodNode(mthNode)

	@JvmStatic
	fun convertFieldNode(d: JadxDecompiler, fldNode: FieldNode): JavaField = d.convertFieldNode(fldNode)
}

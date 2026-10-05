package kadx.api

import kadx.core.dex.nodes.ClassNode
import kadx.core.dex.nodes.FieldNode
import kadx.core.dex.nodes.MethodNode
import kadx.core.dex.nodes.RootNode

/**
 * 测试辅助入口：把 [KadxDecompiler] 上标注为内部/不稳定的转换 API 暴露给测试代码。
 *
 * 用 `object` + `@JvmStatic` 保持 Java 调用方 `KadxInternalAccess.xxx(...)` 的静态写法不变。
 */
object KadxInternalAccess {

	@JvmStatic
	fun getRoot(d: KadxDecompiler): RootNode = checkNotNull(d.getRoot())

	@JvmStatic
	fun convertClassNode(d: KadxDecompiler, clsNode: ClassNode): JavaClass = d.convertClassNode(clsNode)

	@JvmStatic
	fun convertMethodNode(d: KadxDecompiler, mthNode: MethodNode): JavaMethod = d.convertMethodNode(mthNode)

	@JvmStatic
	fun convertFieldNode(d: KadxDecompiler, fldNode: FieldNode): JavaField = d.convertFieldNode(fldNode)
}

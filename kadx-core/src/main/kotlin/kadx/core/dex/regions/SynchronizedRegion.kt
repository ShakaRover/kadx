package kadx.core.dex.regions

import kadx.api.ICodeWriter
import kadx.core.codegen.RegionGen
import kadx.core.dex.nodes.IContainer
import kadx.core.dex.nodes.IRegion
import kadx.core.dex.nodes.InsnNode
import kadx.core.utils.exceptions.CodegenException

/**
 * `synchronized (lock) { ... }` 对应的区域。
 *
 * **语义**：DEX 中 synchronized 由一对 `monitor-enter` / `monitor-exit` 指令实现，
 * 且可能有多条退出指令（正常退出、异常退出）。本区域保存进入指令 [enterInsn]、
 * 所有退出指令 [exitInsns]，以及被保护的代码体 [region]。
 *
 * 同样属于区域树节点，保持普通 class（身份语义）。
 */
class SynchronizedRegion(parent: IRegion?, val enterInsn: InsnNode) : AbstractRegion(parent) {

	/** monitor-exit 指令列表（可能多条） */
	val exitInsns: MutableList<InsnNode> = ArrayList()

	/** 被 synchronized 保护的代码体 */
	val region: Region = Region(this)

	override val subBlocks: List<IContainer> get() = region.subBlocks

	@Throws(CodegenException::class)
	override fun generate(regionGen: RegionGen, code: ICodeWriter) {
		regionGen.makeSynchronizedRegion(this, code)
	}

	/** 用进入指令的偏移作为唯一标识 */
	override fun baseString(): String = Integer.toHexString(enterInsn.getOffset())

	override fun toString(): String = "Synchronized:" + region
}

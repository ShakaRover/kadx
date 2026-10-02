package jadx.core.dex.regions

import jadx.api.ICodeWriter
import jadx.core.codegen.RegionGen
import jadx.core.dex.nodes.IContainer
import jadx.core.dex.nodes.IRegion
import jadx.core.dex.nodes.InsnNode
import jadx.core.utils.exceptions.CodegenException

/**
 * `synchronized (lock) { ... }` 对应的区域。
 *
 * **语义**：DEX 中 synchronized 由一对 `monitor-enter` / `monitor-exit` 指令实现，
 * 且可能有多条退出指令（正常退出、异常退出）。本区域保存进入指令 [enterInsn]、
 * 所有退出指令 [exitInsns]，以及被保护的代码体 [region]。
 *
 * 同样属于区域树节点，保持普通 class（身份语义）。
 */
class SynchronizedRegion(parent: IRegion?, private val enterInsn: InsnNode) : AbstractRegion(parent) {

	/** monitor-exit 指令列表（可能多条） */
	private val exitInsns: MutableList<InsnNode> = ArrayList()

	/** 被 synchronized 保护的代码体 */
	private val region: Region = Region(this)

	fun getEnterInsn(): InsnNode = enterInsn

	fun getExitInsns(): MutableList<InsnNode> = exitInsns

	fun getRegion(): Region = region

	override fun getSubBlocks(): List<IContainer> = region.getSubBlocks()

	@Throws(CodegenException::class)
	override fun generate(regionGen: RegionGen, code: ICodeWriter) {
		regionGen.makeSynchronizedRegion(this, code)
	}

	/** 用进入指令的偏移作为唯一标识 */
	override fun baseString(): String = Integer.toHexString(enterInsn.getOffset())

	override fun toString(): String = "Synchronized:" + region
}

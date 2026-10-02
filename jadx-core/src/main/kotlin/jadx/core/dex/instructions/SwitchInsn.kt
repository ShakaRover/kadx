package jadx.core.dex.instructions

import jadx.core.dex.instructions.args.InsnArg
import jadx.core.dex.nodes.BlockNode
import jadx.core.dex.nodes.InsnNode
import jadx.core.utils.BlockUtils.getBlockByOffset
import jadx.core.utils.InsnUtils
import jadx.core.utils.exceptions.JadxRuntimeException

/**
 * switch 指令（packed-switch / sparse-switch）。
 *
 * switch 的目标数据（键值 / 跳转偏移）并不在指令本身里，而是通过 [dataTarget]
 * 指向后面独立的 [SwitchData] 数据块；解析完成后由 [attachSwitchData] 绑定。
 * [packed] 表示是 packed（键连续）还是 sparse（键稀疏）类型。
 *
 * Kotlin 转换说明：目标块数组 `targetBlocks` 的元素在初始化前可能为 null，
 * 故用 `Array<BlockNode?>`；对外仍暴露原 Java 方法名 `getTargetBlocks()`。
 */
class SwitchInsn(arg: InsnArg, private val dataTarget: Int, private val packed: Boolean) : TargetInsnNode(InsnType.SWITCH, 1) {

	private var switchData: SwitchData? = null

	/** default 分支（下一个指令）的偏移 */
	private var def: Int = 0

	private var modifiedKeys: Array<Any?>? = null
	private var targetBlocks: Array<BlockNode?>? = null
	private var defTargetBlock: BlockNode? = null

	init {
		addArg(arg)
	}

	/** 是否还缺少跳转表数据。 */
	fun needData(): Boolean = switchData == null

	/** 绑定跳转表数据，并记录 default 分支偏移。 */
	fun attachSwitchData(data: SwitchData, def: Int) {
		this.switchData = data
		this.def = def
	}

	override fun initBlocks(curBlock: BlockNode) {
		val data = switchData ?: throw JadxRuntimeException("Switch data not yet attached")
		val successors = curBlock.getSuccessors()
		val targets = data.targets
		val len = targets.size
		val blocks = arrayOfNulls<BlockNode>(len)
		for (i in 0 until len) {
			blocks[i] = getBlockByOffset(targets[i], successors)
		}
		targetBlocks = blocks
		defTargetBlock = getBlockByOffset(def, successors)
	}

	override fun replaceTargetBlock(origin: BlockNode, replace: BlockNode): Boolean {
		val blocks = targetBlocks ?: return false
		var count = 0
		val len = blocks.size
		for (i in 0 until len) {
			if (blocks[i] === origin) {
				blocks[i] = replace
				count++
			}
		}
		if (defTargetBlock === origin) {
			defTargetBlock = replace
			count++
		}
		return count > 0
	}

	override fun isSame(obj: InsnNode): Boolean {
		if (this === obj) {
			return true
		}
		if (obj !is SwitchInsn || !super.isSame(obj)) {
			return false
		}
		return dataTarget == obj.dataTarget && packed == obj.packed
	}

	override fun copy(): InsnNode {
		val copy = SwitchInsn(getArg(0), dataTarget, packed)
		copy.switchData = switchData
		copy.def = def
		copy.targetBlocks = targetBlocks
		copy.defTargetBlock = defTargetBlock
		return copyCommonParams(copy)
	}

	override fun toString(): String {
		val sb = StringBuilder()
		sb.append(baseString())
		val data = switchData
		if (data == null) {
			sb.append("no payload")
		} else {
			val size = data.size
			val keys = data.keys
			val blocks = targetBlocks
			if (blocks != null) {
				for (i in 0 until size) {
					sb.append('\n')
					sb.append(" case ").append(keys[i]).append(": goto ").append(blocks[i])
				}
				if (def != -1) {
					sb.append('\n').append(" default: goto ").append(defTargetBlock)
				}
			} else {
				val targets = data.targets
				for (i in 0 until size) {
					sb.append('\n')
					sb.append(" case ").append(keys[i]).append(": goto ").append(InsnUtils.formatOffset(targets[i]))
				}
				if (def != -1) {
					sb.append('\n')
					sb.append(" default: goto ").append(InsnUtils.formatOffset(def))
				}
			}
		}
		appendAttributes(sb)
		return sb.toString()
	}

	fun getDataTarget(): Int = dataTarget

	fun isPacked(): Boolean = packed

	fun getDefaultCaseOffset(): Int = def

	private fun requireSwitchData(): SwitchData = switchData ?: throw JadxRuntimeException("Switch data not yet attached")

	fun getTargets(): IntArray = requireSwitchData().targets

	fun getKeys(): IntArray = requireSwitchData().keys

	/** 取第 i 个 case 的键值（可能被 [modifyKey] 修改过）。 */
	fun getKey(i: Int): Any? {
		val keys = modifiedKeys
		if (keys != null) {
			return keys[i]
		}
		return requireSwitchData().keys[i]
	}

	/** 修改第 i 个 case 的键值（首次修改时把整型键复制到对象数组）。 */
	fun modifyKey(i: Int, newKey: Any?) {
		var keys = modifiedKeys
		if (keys == null) {
			val src = getKeys()
			val caseCount = src.size
			keys = arrayOfNulls(caseCount)
			for (j in 0 until caseCount) {
				keys[j] = src[j]
			}
			modifiedKeys = keys
		}
		keys[i] = newKey
	}

	fun getTargetBlocks(): Array<BlockNode?>? = targetBlocks

	fun getDefTargetBlock(): BlockNode? = defTargetBlock
}

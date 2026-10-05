package kadx.core.dex.attributes.nodes

import kadx.api.plugins.input.data.attributes.PinnedAttribute
import kadx.core.dex.attributes.AType
import kadx.core.dex.instructions.args.RegisterArg
import kadx.core.dex.nodes.InsnNode
import kadx.core.dex.nodes.MethodNode

/**
 * 方法内联属性：标记某方法可以（或不需要）被内联，并保存内联所需的替换指令。
 *
 * **为什么需要寄存器映射？** 内联时调用方的实参需要替换被调方法的形参，
 * 因此这里用 [argsRegNums] 记住被调方法每个形参的寄存器号，供 `InlineMethods` 重映射。
 *
 * **哨兵值**：[INLINE_NOT_NEEDED] 是一个共享实例，表示“明确判断过、无需内联”。
 * 它复用同一个对象，避免为每个方法都创建属性；通过 [notNeeded] 判断。
 *
 * **Kotlin 转换说明**：
 * - 原 Java 静态方法 [markForInline] / [inlineNotNeeded] 放入 companion + `@JvmStatic`，
 *   Java 调用方仍写 `MethodInlineAttr.markForInline(...)`；
 * - 原构造器私有，这里保留私有主构造器；
 * - [insn] / [argsRegNums] 在哨兵实例中为 null，故 getter 如实返回可空类型。
 */
class MethodInlineAttr private constructor(
	val insn: InsnNode?,
	val argsRegNums: IntArray?,
) : PinnedAttribute() {

	companion object {
		/** 共享哨兵：表示该方法已被判定“无需内联” */
		private val INLINE_NOT_NEEDED = MethodInlineAttr(null, null)

		/**
		 * 标记方法 [mth] 可以内联到 [replaceInsn]，并记录其形参寄存器号。
		 *
		 * 原 Java 用 `Objects.requireNonNull(replaceInsn)` 做非空检查；
		 * Kotlin 的非空参数 [replaceInsn] 在入口处即做等价校验。
		 */
		fun markForInline(mth: MethodNode, replaceInsn: InsnNode): MethodInlineAttr {
			val allArgRegs: List<RegisterArg> = mth.allArgRegs
			val argsCount = allArgRegs.size
			val regNums = IntArray(argsCount)
			for (i in 0 until argsCount) {
				val reg = allArgRegs[i]
				regNums[i] = reg.regNum
			}
			val mia = MethodInlineAttr(replaceInsn, regNums)
			mth.addAttr(mia)
			mth.addDebugComment("Marked for inline")
			return mia
		}

		/** 标记方法 [mth] 无需内联，并返回共享哨兵实例 */
		fun inlineNotNeeded(mth: MethodNode): MethodInlineAttr {
			mth.addAttr(INLINE_NOT_NEEDED)
			return INLINE_NOT_NEEDED
		}
	}

	/** 是否为“无需内联”哨兵（哨兵的 [insn] 为 null） */
	fun notNeeded(): Boolean = insn == null

	override val attrType: AType<MethodInlineAttr> get() = AType.METHOD_INLINE

	override fun toString(): String {
		if (notNeeded()) {
			return "INLINE_NOT_NEEDED"
		}
		return "INLINE: $insn"
	}
}

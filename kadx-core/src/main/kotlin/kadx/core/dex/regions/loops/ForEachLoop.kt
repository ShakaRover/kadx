package kadx.core.dex.regions.loops

import kadx.core.dex.attributes.AFlag
import kadx.core.dex.instructions.InsnType
import kadx.core.dex.instructions.args.InsnArg
import kadx.core.dex.instructions.args.RegisterArg
import kadx.core.dex.nodes.InsnNode

/**
 * 增强 `for`（`for (x : iterable)`）循环的类型标记。
 *
 * **实现技巧**：把“循环变量”和“被遍历对象”两个参数分别存进两条“假指令”
 * （[InsnType.REGION_ARG]），而不是直接存 [InsnArg]。这样做的好处是：
 * 保留了完整的代码语义，后续对参数做内联（inline）等变换时也能像普通指令参数一样处理。
 *
 * 假指令会在代码生成前通过 [injectFakeInsns] 注入到循环的前置块/头部块。
 */
class ForEachLoop(varArg: RegisterArg, iterableArg: InsnArg) : LoopType() {

	/** 保存循环变量的假指令（结果寄存器即循环变量） */
	private val varArgInsn: InsnNode = InsnNode(InsnType.REGION_ARG, 0)

	/** 保存被遍历对象的假指令（第一个参数即 iterable） */
	private val iterableArgInsn: InsnNode = InsnNode(InsnType.REGION_ARG, 1)

	init {
		// 标记为不可内联，避免这些“假参数”被优化掉
		varArgInsn.add(AFlag.DONT_INLINE)
		varArgInsn.setResult(varArg.duplicate())

		iterableArgInsn.add(AFlag.DONT_INLINE)
		iterableArgInsn.addArg(iterableArg.duplicate())

		// 循环变量将在 codegen 阶段声明
		val sVar = checkNotNull(this.varArg.sVar)
		sVar.codeVar.isDeclared = true
	}

	/** 把两条假指令分别注入循环前置块（iterable）与头部块（循环变量） */
	fun injectFakeInsns(loopRegion: LoopRegion) {
		loopRegion.info.preHeader.instructions.add(iterableArgInsn)
		checkNotNull(loopRegion.header).instructions.add(0, varArgInsn)
	}

	val varArg: RegisterArg get() = checkNotNull(varArgInsn.result)

	val iterableArg: InsnArg get() = iterableArgInsn.getArg(0)
}

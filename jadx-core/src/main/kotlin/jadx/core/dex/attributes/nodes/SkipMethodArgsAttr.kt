package jadx.core.dex.attributes.nodes

import jadx.api.plugins.input.data.attributes.PinnedAttribute
import jadx.core.dex.attributes.AFlag
import jadx.core.dex.attributes.AType
import jadx.core.dex.instructions.args.RegisterArg
import jadx.core.dex.nodes.MethodNode
import jadx.core.utils.Utils
import jadx.core.utils.exceptions.JadxRuntimeException
import java.util.BitSet

/**
 * 跳过方法参数属性：用位图记录方法签名里哪些参数在反编译输出中应被省略。
 *
 * **为什么需要？** 编译器生成的合成参数（如内部类的 outer 引用、枚举的 name/ordinal）
 * 在源码层面并不存在，代码生成时应跳过。
 *
 * **Kotlin 转换说明**：
 * - 原 Java 三个静态方法 `skipArg`(×2) / `isSkip` → companion + `@JvmStatic`；
 * - 原 `isSkip(@Nullable MethodNode, ...)` 的可空语义如实保留为 [mth] 可空；
 * - 构造器私有，仅允许通过 [skipArg] 工厂创建。
 */
class SkipMethodArgsAttr private constructor(mth: MethodNode) : PinnedAttribute() {

	companion object {
		/** 按寄存器引用定位参数下标并标记跳过；找不到参数时抛异常 */
		@JvmStatic
		fun skipArg(mth: MethodNode, arg: RegisterArg) {
			val argNum = Utils.indexInListByRef(mth.getArgRegs(), arg)
			if (argNum == -1) {
				throw JadxRuntimeException("Arg not found: $arg")
			}
			skipArg(mth, argNum)
		}

		/** 按参数下标标记跳过（属性不存在时先创建） */
		@JvmStatic
		fun skipArg(mth: MethodNode, argNum: Int) {
			var attr = mth.get(AType.SKIP_MTH_ARGS)
			if (attr == null) {
				attr = SkipMethodArgsAttr(mth)
				mth.addAttr(attr)
			}
			attr.skip(argNum)
		}

		/**
		 * 查询某参数是否被标记跳过。
		 *
		 * 除本属性外，还会识别 [AFlag.SKIP_FIRST_ARG]（下标 0 的特殊跳过标记）。
		 * [mth] 为 null 时直接返回 false（对应原 Java 的 @Nullable 参数）。
		 */
		@JvmStatic
		fun isSkip(mth: MethodNode?, argNum: Int): Boolean {
			if (mth == null) {
				return false
			}
			if (argNum == 0 && mth.contains(AFlag.SKIP_FIRST_ARG)) {
				return true
			}
			val attr = mth.get(AType.SKIP_MTH_ARGS) ?: return false
			return attr.isSkip(argNum)
		}
	}

	/** 参数跳过位图，长度取方法参数个数 */
	private val skipArgs: BitSet = BitSet(mth.getMethodInfo().argsCount)

	/** 标记第 [argNum] 个参数为跳过 */
	fun skip(argNum: Int) {
		skipArgs.set(argNum)
	}

	/** 查询第 [argNum] 个参数是否被跳过 */
	fun isSkip(argNum: Int): Boolean = skipArgs.get(argNum)

	/** 被跳过的参数总数 */
	fun getSkipCount(): Int = skipArgs.cardinality()

	override val attrType: AType<SkipMethodArgsAttr> get() = AType.SKIP_MTH_ARGS

	override fun toString(): String = "SKIP_MTH_ARGS: $skipArgs"
}

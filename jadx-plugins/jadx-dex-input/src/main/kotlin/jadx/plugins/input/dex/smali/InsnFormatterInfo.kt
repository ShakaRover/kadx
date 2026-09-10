package jadx.plugins.input.dex.smali

import jadx.api.plugins.input.data.IMethodData
import jadx.api.plugins.input.insns.InsnData
import org.jetbrains.annotations.Nullable

/**
 * 指令格式化上下文：共享的 [SmaliCodeWriter] + 当前方法/指令。
 *
 **背景**：[SmaliPrinter.printMethod] 创建一个实例并随遍历反复 [setInsn]，
 * 各 [InsnFormatter] 通过它访问输出器与指令数据（避免每次调用传多个参数）。
 *
 **Kotlin 转换说明**：成员保持原 Java getter/setter 显式函数命名（非属性），
 * Kotlin/Java 调用方零改动。
 */
public class InsnFormatterInfo(
	private val codeWriter: SmaliCodeWriter,
) {

	@Nullable
	private var mth: IMethodData? = null

	@Nullable
	private var insn: InsnData? = null

	/** 以方法为上下文的构造（mth 不可为 null，与原 Java requireNonNull 一致）*/
	public constructor(
		codeWriter: SmaliCodeWriter,
		mth: IMethodData,
	) : this(codeWriter) {
		this.mth = mth
	}

	/** 以指令为上下文的构造（insn 不可为 null，与原 Java requireNonNull 一致）*/
	public constructor(
		codeWriter: SmaliCodeWriter,
		insn: InsnData,
	) : this(codeWriter) {
		this.insn = insn
	}

	public fun getCodeWriter(): SmaliCodeWriter = codeWriter

	public fun setMth(mth: IMethodData?) {
		this.mth = mth
	}

	@Nullable
	public fun getMth(): IMethodData? = mth

	/** @throws IllegalStateException 指令未设置时抛异常（原 Java 为裸 NPE，同项目 checkNotNull 惯例）*/
	public fun getInsn(): InsnData = checkNotNull(insn) { "Instruction not set for formatter" }

	public fun setInsn(insn: InsnData?) {
		this.insn = insn
	}
}

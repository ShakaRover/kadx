package jadx.api.metadata.annotations

import jadx.api.ICodeWriter
import jadx.api.metadata.ICodeAnnotation
import jadx.core.dex.nodes.InsnNode

/**
 * 字节码偏移注解：把某段生成代码对应到 dex 指令的偏移。
 *
 * **做什么**：反编译生成指令代码时，在对应位置挂上本注解，UI 就能把
 * Java 代码行与 smali 指令互相对应。
 *
 * **Kotlin 转换说明**：静态方法 `attach` / `from` 放入 `companion object` 并加
 * `@JvmStatic`，Java 调用方零改动。`attach` 允许传入 null 指令（原 Java 显式判空），
 * 故参数声明为可空。
 */
class InsnCodeOffset(private val offset: Int) : ICodeAnnotation {

	companion object {
		/** 为指令挂偏移注解；指令为 null 或元数据不受支持时跳过。 */
		@JvmStatic
		fun attach(code: ICodeWriter, insn: InsnNode?) {
			if (insn == null) {
				return
			}
			if (code.isMetadataSupported()) {
				val ann = from(insn)
				if (ann != null) {
					code.attachLineAnnotation(ann)
				}
			}
		}

		/** 直接为给定偏移挂注解；偏移为负或元数据不受支持时跳过。 */
		@JvmStatic
		fun attach(code: ICodeWriter, offset: Int) {
			if (offset >= 0 && code.isMetadataSupported()) {
				code.attachLineAnnotation(InsnCodeOffset(offset))
			}
		}

		/** 由指令构造注解；指令偏移为负时返回 null。 */
		@JvmStatic
		fun from(insn: InsnNode): InsnCodeOffset? {
			val offset = insn.getOffset()
			if (offset < 0) {
				return null
			}
			return InsnCodeOffset(offset)
		}
	}

	/** 字节码偏移。 */
	fun getOffset(): Int = offset

	override val annType: ICodeAnnotation.AnnType get() = ICodeAnnotation.AnnType.OFFSET

	override fun toString(): String = "offset=" + offset
}

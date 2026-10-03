package jadx.gui.device.debugger.smali

import jadx.api.ICodeInfo
import jadx.api.impl.SimpleCodeInfo
import jadx.api.impl.SimpleCodeWriter
import jadx.core.dex.nodes.ClassNode

/**
 * 反汇编 smali 文本的代码写出器。
 *
 * **做什么**：在 [SimpleCodeWriter] 基础上额外维护「当前行号」，
 * 供 `Smali` 生成「smali 行号 -> 代码偏移」的映射（设置断点 / 单步定位用）。
 *
 * **为什么覆写 [finish]**：原 Java 版本只取缓冲区文本、不释放缓冲区
 * （与父类 `finish()` 不同），这里保持相同行为。
 */
class SmaliWriter(private val cls: ClassNode) : SimpleCodeWriter(cls.root().getArgs()) {

	/** 当前已写出的行数（从 0 开始，每次换行 +1）。 */
	private var line = 0

	/** @return 正在反汇编的类节点 */
	fun getClassNode(): ClassNode = cls

	/** 每次换行时同步递增行号。 */
	override fun addLine() {
		super.addLine()
		line++
	}

	/** @return 当前行号 */
	override fun getLine(): Int = line

	/** @return 生成完毕的 smali 代码信息 */
	override fun finish(): ICodeInfo = SimpleCodeInfo(checkNotNull(buf).toString())
}

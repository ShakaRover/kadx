package jadx.gui.ui.codearea.mode

import jadx.api.DecompilationMode
import jadx.api.ICodeInfo
import jadx.gui.treemodel.JClass
import jadx.gui.treemodel.JNode
import org.fife.ui.rsyntaxtextarea.SyntaxConstants
import javax.swing.Icon

/**
 * 以“指定反编译模式”展示某个类的伪节点。
 *
 * **做什么**：同一个类可能有多种反编译结果（SIMPLE / FALLBACK 等）。
 * 本节点包装 [JClass] 与 [DecompilationMode]，延迟到第一次读取时
 * 才真正执行 `decompileWithMode`，避免无谓的反编译开销。
 *
 * **为什么不是 data class**：它是树中的身份节点，需要按引用比较，
 * 因此使用普通类。
 */
class JCodeMode(
	private val jCls: JClass,
	private val mode: DecompilationMode,
) : JNode() {

	@Volatile
	private var codeInfo: ICodeInfo? = null

	override fun getJParent(): JClass = jCls.getJParent()

	override fun getIcon(): Icon = jCls.getIcon()

	override fun makeString(): String = jCls.makeString()

	override fun getCodeInfo(): ICodeInfo {
		val cached = codeInfo
		if (cached != null) {
			return cached
		}
		val cls = jCls.getCls().getClassNode()
		val info = cls.decompileWithMode(mode)
		codeInfo = info
		return info
	}

	override fun getSyntaxName(): String = SyntaxConstants.SYNTAX_STYLE_JAVA

	override fun getName(): String = jCls.getName()
}

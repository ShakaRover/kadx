package jadx.gui.plugins.context

import jadx.api.metadata.ICodeNodeRef
import jadx.gui.treemodel.JNode
import jadx.gui.ui.action.JNodeAction
import jadx.gui.ui.codearea.CodeArea
import java.util.function.Consumer
import java.util.function.Function
import javax.swing.KeyStroke

/**
 * 代码查看器右键弹窗中的一项动作。
 *
 * **做什么**：保存菜单标题、可用性判定、可选快捷键与执行动作；
 * [buildAction] 把它包装成绑定到 [CodeArea] 的 [JNodeAction]。
 *
 * **线程模型**：动作通过主窗口的后台执行器运行（保持原 Swing 线程模型）。
 */
class CodePopupAction(
	private val name: String,
	private val enabledCheck: Function<ICodeNodeRef, Boolean>?,
	private val keyBinding: String?,
	private val action: Consumer<ICodeNodeRef>,
) {

	/** 为给定代码区构建动作。 */
	fun buildAction(codeArea: CodeArea): JNodeAction = NodeAction(this, codeArea)

	/**
	 * 实际绑定到代码区的动作实现。
	 */
	private class NodeAction(private val data: CodePopupAction, codeArea: CodeArea) : JNodeAction(data.name, codeArea) {

		init {
			setName(data.name)
			setShortcutComponent(codeArea)
			if (data.keyBinding != null) {
				val key = KeyStroke.getKeyStroke(data.keyBinding)
					?: throw IllegalArgumentException("Failed to parse key stroke: " + data.keyBinding)
				setKeyBinding(key)
			}
		}

		override fun isActionEnabled(node: JNode?): Boolean {
			if (node == null) {
				return false
			}
			val codeNode = node.getCodeNodeRef() ?: return false
			// enabledCheck 允许为 null（接口声明可空），此时视为不限制
			return data.enabledCheck?.apply(codeNode) ?: true
		}

		override fun runAction(node: JNode) {
			val r = Runnable { data.action.accept(checkNotNull(node.getCodeNodeRef())) }
			getCodeArea().getMainWindow().getBackgroundExecutor().execute(data.name, r)
		}
	}
}

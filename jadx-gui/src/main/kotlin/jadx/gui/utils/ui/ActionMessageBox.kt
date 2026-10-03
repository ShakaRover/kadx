package jadx.gui.utils.ui

import jadx.gui.utils.UiUtils
import java.awt.Window
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.JOptionPane

/**
 * 带自定义操作按钮的简单消息框。
 *
 * **做什么**：把若干 [Action] 作为选项按钮展示给用户，用户选择后执行对应动作。
 *
 * **线程模型**：弹窗在 EDT 上执行（[UiUtils.uiRunAndWait]），与原 Java 一致。
 */
class ActionMessageBox {

	/**
	 * 消息框上的一个操作按钮。
	 *
	 * @param name 按钮显示名
	 * @param action 点击后执行的动作
	 */
	class Action(private val name: String, val action: Runnable) {
		override fun toString(): String = name
	}

	private val parent: Window?
	private val title: String
	private val msg: String
	private val actions: List<Action>

	constructor(parent: Window?, title: String, msg: String, vararg actions: Action) :
		this(parent, title, msg, actions.toList())

	constructor(parent: Window?, title: String, msg: String, actions: List<Action>) {
		this.parent = parent
		this.title = title
		this.msg = msg
		this.actions = actions
	}

	/**
	 * 显示消息框。
	 *
	 * @return 用户执行了某个动作返回 `true`，直接关闭返回 `false`
	 */
	fun show(): Boolean {
		val result = AtomicInteger(-1)
		UiUtils.uiRunAndWait {
			result.set(
				JOptionPane.showOptionDialog(
					parent,
					msg,
					title,
					JOptionPane.DEFAULT_OPTION,
					JOptionPane.INFORMATION_MESSAGE,
					null,
					actions.toTypedArray(),
					actions[0],
				),
			)
		}
		val r = result.get()
		if (r == JOptionPane.CLOSED_OPTION) {
			return false
		}
		actions[r].action.run()
		return true
	}
}

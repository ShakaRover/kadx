package jadx.gui.ui.action

import jadx.gui.utils.shortcut.Shortcut
import org.fife.ui.autocomplete.AutoCompletion
import org.fife.ui.autocomplete.CompletionProvider
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import javax.swing.JComponent
import javax.swing.KeyStroke

/**
 * 插件脚本编辑器的自动补全实现。
 *
 * **做什么**：继承 RSyntaxTextArea 的 [AutoCompletion]，并实现 [IShortcutAction]，
 * 使自动补全可以像普通动作一样绑定快捷键（默认 `Ctrl+Space`）。
 */
class JadxAutoCompletion(provider: CompletionProvider) :
	AutoCompletion(provider),
	IShortcutAction {

	override fun getActionModel(): ActionModel = ActionModel.SCRIPT_AUTO_COMPLETE

	override fun getShortcutComponent(): JComponent = getTextComponent()

	override fun performAction() {
		createAutoCompleteAction().actionPerformed(ActionEvent(this, ActionEvent.ACTION_PERFORMED, COMMAND))
	}

	override fun setShortcut(shortcut: Shortcut?) {
		if (shortcut != null && shortcut.isKeyboard) {
			setTriggerKey(shortcut.toKeyStroke())
		} else {
			setTriggerKey(KeyStroke.getKeyStroke(KeyEvent.VK_UNDEFINED, 0))
		}
	}

	companion object {
		val COMMAND = "JadxAutoCompletion.Command"
	}
}

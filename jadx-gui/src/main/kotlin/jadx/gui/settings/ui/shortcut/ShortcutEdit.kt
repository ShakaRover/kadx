package jadx.gui.settings.ui.shortcut

import jadx.gui.settings.JadxSettings
import jadx.gui.settings.ui.JadxSettingsWindow
import jadx.gui.ui.action.ActionModel
import jadx.gui.utils.NLS
import jadx.gui.utils.UiUtils
import jadx.gui.utils.shortcut.Shortcut
import java.awt.AWTEvent
import java.awt.KeyEventDispatcher
import java.awt.KeyboardFocusManager
import java.awt.Toolkit
import java.awt.event.AWTEventListener
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import javax.swing.BoxLayout
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.UIManager

/**
 * 单个快捷键编辑控件：一个只读文本框加一个「清除」按钮。
 *
 * **做什么**：聚焦时监听键盘/鼠标事件，把按下的键或鼠标按钮转换为 [Shortcut]；
 * 失焦时校验冲突并保存。
 *
 * **线程模型**：保持原 AWT 事件监听器与 Swing 单线程模型，未做任何异步改造。
 */
class ShortcutEdit(
	private val actionModel: ActionModel,
	private val settingsWindow: JadxSettingsWindow,
	private val settings: JadxSettings,
) : JPanel() {

	/** 当前快捷键；在 [setShortcut] 之前为 null（与原 Java 字段初始值一致）。 */
	@JvmField
	var shortcut: Shortcut? = null

	private val textField: TextField

	init {
		textField = TextField()
		val clearButton = JButton(CLEAR_ICON)

		layout = BoxLayout(this, BoxLayout.X_AXIS)
		add(textField)
		add(clearButton)

		clearButton.addActionListener {
			setShortcut(Shortcut.none())
			saveShortcut()
		}
	}

	fun setShortcut(shortcut: Shortcut) {
		this.shortcut = shortcut
		textField.reload()
	}

	private fun saveShortcut() {
		settings.shortcuts.put(actionModel, checkNotNull(shortcut))
		settingsWindow.needReload()
	}

	private fun verifyShortcut(shortcut: Shortcut): Boolean {
		var otherAction: ActionModel? = null
		for (a in ActionModel.values()) {
			if (actionModel != a && shortcut == settings.shortcuts.get(a)) {
				otherAction = a
				break
			}
		}

		if (otherAction != null) {
			val message = NLS.str("msg.duplicate_shortcut", shortcut, otherAction.getName(), otherAction.category.getName())
			val dialogResult = JOptionPane.showConfirmDialog(
				this,
				message,
				NLS.str("msg.warning_title"),
				JOptionPane.YES_NO_OPTION,
			)
			if (dialogResult != 0) {
				return false
			}
		}

		return true
	}

	private inner class TextField : JTextField() {

		private var tempShortcut: Shortcut? = null

		init {
			KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(object : KeyEventDispatcher {
				override fun dispatchKeyEvent(ev: KeyEvent): Boolean {
					if (!isListening) {
						return false
					}

					if (ev.id == KeyEvent.KEY_PRESSED) {
						val pressedShortcut = Shortcut.keyboard(ev.keyCode, ev.modifiersEx)
						if (pressedShortcut.isValidKeyboard) {
							tempShortcut = pressedShortcut
							refresh(pressedShortcut)
						} else {
							tempShortcut = null
						}
					} else if (ev.id == KeyEvent.KEY_RELEASED) {
						removeFocus()
					}
					ev.consume()
					return true
				}
			})

			addFocusListener(object : FocusListener {
				override fun focusGained(ev: FocusEvent) {
					// 无需处理
				}

				override fun focusLost(ev: FocusEvent) {
					val sc = tempShortcut
					if (sc != null) {
						if (verifyShortcut(sc)) {
							shortcut = sc
							saveShortcut()
						} else {
							reload()
						}
						tempShortcut = null
					}
				}
			})

			Toolkit.getDefaultToolkit().addAWTEventListener(
				object : AWTEventListener {
					override fun eventDispatched(event: AWTEvent) {
						if (!isListening) {
							return
						}

						if (event is MouseEvent) {
							if (event.id == MouseEvent.MOUSE_PRESSED) {
								val mouseButton = event.button

								if (mouseButton <= MouseEvent.BUTTON1) {
									return
								}

								if (mouseButton <= MouseEvent.BUTTON3) {
									val dialogResult = JOptionPane.showConfirmDialog(
										this@ShortcutEdit,
										NLS.str("msg.common_mouse_shortcut"),
										NLS.str("msg.warning_title"),
										JOptionPane.YES_NO_OPTION,
									)
									if (dialogResult != 0) {
										event.consume()
										tempShortcut = null
										removeFocus()
										return
									}
								}

								event.consume()
								tempShortcut = Shortcut.mouse(mouseButton)
								refresh(checkNotNull(tempShortcut))
								removeFocus()
							}
						}
					}
				},
				AWTEvent.MOUSE_EVENT_MASK,
			)
		}

		fun reload() {
			refresh(shortcut)
		}

		private fun refresh(displayedShortcut: Shortcut?) {
			if (displayedShortcut == null || displayedShortcut.isNone) {
				text = "None"
				foreground = UIManager.getColor("TextArea.inactiveForeground")
				return
			}
			text = displayedShortcut.toString()
			foreground = UIManager.getColor("TextArea.foreground")
		}

		private fun removeFocus() {
			// 触发 focusLost
			rootPane.requestFocus()
		}

		private val isListening: Boolean get() = isFocusOwner
	}

	companion object {
		private val CLEAR_ICON: Icon = UiUtils.openSvgIcon("ui/close")
	}
}

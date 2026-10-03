package jadx.gui.utils.shortcut

import jadx.commons.app.JadxSystemInfo
import java.awt.event.KeyEvent
import java.util.Objects
import javax.swing.KeyStroke

/**
 * 快捷键描述（键盘按键或鼠标按钮）。
 *
 * **做什么**：记录一个快捷键的键码、修饰键或鼠标按钮，并提供合法性校验、
 * 转换为 Swing [KeyStroke]、字符串展示以及相等性比较。
 *
 * **为什么保留自定义 `equals`/`hashCode`**：快捷键要作为 Map 的键并做去重比较，
 * 必须按“键码 + 修饰键 + 鼠标按钮”的值语义判断，且字段可空（[none] 场景）。
 *
 * **为什么用私有构造器 + 静态工厂**：与 Java 侧的 `Shortcut.keyboard(...)` 等
 * 静态调用方式保持完全一致。
 */
class Shortcut private constructor() {

	private var keyCode: Int? = null
	private var modifiers: Int? = null
	private var mouseButton: Int? = null

	fun getKeyCode(): Int? = keyCode

	fun getModifiers(): Int? = modifiers

	fun getMouseButton(): Int? = mouseButton

	fun isKeyboard(): Boolean = keyCode != null

	fun isMouse(): Boolean = mouseButton != null

	fun isNone(): Boolean = !isMouse() && !isKeyboard()

	/** 是否为合法的键盘快捷键（非禁用键、修饰键合法）。 */
	fun isValidKeyboard(): Boolean {
		val kc = keyCode ?: return false
		return !FORBIDDEN_KEY_CODES.contains(kc) && isValidModifiers()
	}

	/** 修饰键是否只包含允许的掩码。 */
	fun isValidModifiers(): Boolean {
		var modifiersTest = modifiers ?: return false
		for (modifier in ALLOWED_MODIFIERS) {
			modifiersTest = modifiersTest and modifier.inv()
		}
		return modifiersTest == 0
	}

	/** 转换为 Swing [KeyStroke]；非键盘快捷键返回 `null`。 */
	fun toKeyStroke(): KeyStroke? {
		if (!isKeyboard()) {
			return null
		}
		val kc = keyCode ?: return null
		val mod = modifiers ?: 0
		return KeyStroke.getKeyStroke(kc, mod, mod != 0 && JadxSystemInfo.IS_MAC)
	}

	override fun toString(): String = when {
		isKeyboard() -> keyToString()
		isMouse() -> mouseToString()
		else -> "NONE"
	}

	/** 返回类型展示字符串（`Keyboard` / `Mouse` / `null`）。 */
	fun getTypeString(): String? = when {
		isKeyboard() -> "Keyboard"
		isMouse() -> "Mouse"
		else -> null
	}

	private fun mouseToString(): String = "MouseButton$mouseButton"

	private fun keyToString(): String {
		val sb = StringBuilder()
		val mod = modifiers
		if (mod != null && mod > 0) {
			sb.append(KeyEvent.getModifiersExText(mod))
			sb.append('+')
		}
		val kc = keyCode
		if (kc != null && kc != 0) {
			sb.append(KeyEvent.getKeyText(kc))
		} else {
			sb.append("UNDEFINED")
		}
		return sb.toString()
	}

	override fun equals(other: Any?): Boolean {
		if (this === other) {
			return true
		}
		if (other == null || javaClass != other.javaClass) {
			return false
		}
		val shortcut = other as Shortcut
		return keyCode == shortcut.keyCode &&
			modifiers == shortcut.modifiers &&
			mouseButton == shortcut.mouseButton
	}

	override fun hashCode(): Int = Objects.hash(keyCode, modifiers, mouseButton)

	companion object {
		/** 不允许作为快捷键主键的按键（纯修饰键等）。 */
		private val FORBIDDEN_KEY_CODES: Set<Int> = HashSet(
			listOf(KeyEvent.VK_UNDEFINED, KeyEvent.VK_SHIFT, KeyEvent.VK_ALT, KeyEvent.VK_META, KeyEvent.VK_ALT_GRAPH),
		)

		/** 允许出现的修饰键掩码。 */
		private val ALLOWED_MODIFIERS: Set<Int> = HashSet(
			listOf(
				KeyEvent.CTRL_DOWN_MASK,
				KeyEvent.META_DOWN_MASK,
				KeyEvent.ALT_DOWN_MASK,
				KeyEvent.ALT_GRAPH_DOWN_MASK,
				KeyEvent.SHIFT_DOWN_MASK,
			),
		)

		@JvmStatic
		fun keyboard(keyCode: Int): Shortcut = keyboard(keyCode, 0)

		@JvmStatic
		fun keyboard(keyCode: Int, modifiers: Int): Shortcut {
			val shortcut = Shortcut()
			shortcut.keyCode = keyCode
			shortcut.modifiers = modifiers
			return shortcut
		}

		@JvmStatic
		fun mouse(mouseButton: Int): Shortcut {
			val shortcut = Shortcut()
			shortcut.mouseButton = mouseButton
			return shortcut
		}

		@JvmStatic
		fun none(): Shortcut {
			val shortcut = Shortcut()
			// 必须至少有一个非空属性才能被序列化，否则会回退到默认快捷键
			shortcut.modifiers = 0
			return shortcut
		}
	}
}

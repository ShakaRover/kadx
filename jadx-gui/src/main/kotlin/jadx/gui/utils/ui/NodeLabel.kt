package jadx.gui.utils.ui

import jadx.gui.treemodel.JNode
import javax.swing.JLabel
import javax.swing.SwingConstants

/**
 * 支持禁用 HTML 渲染的 [JLabel]。
 *
 * **做什么**：Swing 的 [JLabel] 遇到以 `<html>` 开头或含 HTML 标记的文本会自动按 HTML 渲染；
 * 本类通过 `html.disable` 客户端属性控制该行为，避免代码/包名中的 `<`、`>` 被当作标签。
 */
class NodeLabel : JLabel {

	private var htmlDisabled = false

	constructor() {
		disableHtml(true)
	}

	constructor(label: String) {
		disableHtml(true)
		setText(label)
	}

	constructor(label: String, disableHtml: Boolean) {
		disableHtml(disableHtml)
		setText(label)
	}

	/** 设置是否禁用 HTML 渲染。 */
	fun disableHtml(disable: Boolean) {
		if (htmlDisabled != disable) {
			htmlDisabled = disable
			disableHtml(this, disable)
		}
	}

	companion object {
		/** 创建展示节点长名称（含图标）的标签。 */
		@JvmStatic
		fun longName(node: JNode): NodeLabel {
			val label = NodeLabel(node.makeLongStringHtml(), node.disableHtml())
			label.setIcon(node.getIcon())
			label.setHorizontalAlignment(SwingConstants.LEFT)
			return label
		}

		/** 创建禁用 HTML 渲染的纯文本标签。 */
		@JvmStatic
		fun noHtml(label: String): NodeLabel = NodeLabel(label, true)

		/** 通过客户端属性禁用/启用 HTML 渲染。 */
		@JvmStatic
		fun disableHtml(label: JLabel, disable: Boolean) {
			label.putClientProperty("html.disable", disable)
		}
	}
}

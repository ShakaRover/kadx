package jadx.gui.utils.ui

import java.util.function.Consumer
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * 只转发“内容变更”的 [DocumentListener] 适配器。
 *
 * **做什么**：把文档的插入/删除事件统一转成 [Consumer] 回调；
 * `changedUpdate`（属性变更）被忽略，与原 Java 行为一致。
 *
 * **为什么用 Java 的 [Consumer]**：保留 Java 调用方的 SAM 用法（`new DocumentUpdateListener(ev -> ...)`）。
 */
class DocumentUpdateListener(private val listener: Consumer<DocumentEvent>) : DocumentListener {

	override fun insertUpdate(event: DocumentEvent) {
		listener.accept(event)
	}

	override fun removeUpdate(event: DocumentEvent) {
		listener.accept(event)
	}

	override fun changedUpdate(event: DocumentEvent) {
		// 忽略属性变更
	}
}

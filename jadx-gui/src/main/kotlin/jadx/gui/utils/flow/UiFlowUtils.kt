package jadx.gui.utils.flow

import jadx.gui.utils.ui.DocumentUpdateListener
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import java.awt.event.AdjustmentListener
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.KeyListener
import javax.swing.JScrollBar
import javax.swing.JSpinner
import javax.swing.JTextField
import javax.swing.event.ChangeListener

/**
 * Swing 组件事件的 [Flow] 封装工具（N1c：替代原 `utils/rx/RxUtils`）。
 *
 * **做什么**：把文本框、微调框的“内容变化 / 回车确认”事件转成冷 [Flow]，
 * 并做 `distinctUntilChanged` 去重，供搜索对话框做响应式联动。
 *
 * **生命周期**：每个流用 [callbackFlow] 创建，收集取消时通过 [awaitClose] 移除监听器，
 * 因此不需要原来的 `CustomDisposable`。
 */
object UiFlowUtils {

	/** 文本框内容变化（去重）。 */
	fun textFieldChanges(textField: JTextField): Flow<String> = callbackFlow {
		val listener = DocumentUpdateListener { trySend(textField.getText()) }
		textField.getDocument().addDocumentListener(listener)
		awaitClose { textField.getDocument().removeDocumentListener(listener) }
	}.distinctUntilChanged()

	/** 文本框回车确认（去重）。 */
	fun textFieldEnterPress(textField: JTextField): Flow<String> = callbackFlow {
		val listener = enterKeyListener { textField.getText() }
		textField.addKeyListener(listener)
		awaitClose { textField.removeKeyListener(listener) }
	}.distinctUntilChanged()

	/** 微调框数值变化（去重）。 */
	fun spinnerChanges(spinner: JSpinner): Flow<String> = callbackFlow {
		val listener = ChangeListener { trySend(spinner.getValue().toString()) }
		spinner.addChangeListener(listener)
		awaitClose { spinner.removeChangeListener(listener) }
	}.distinctUntilChanged()

	/** 滚动条位置变化（去重）。 */
	fun scrollBarEvents(scrollBar: JScrollBar): Flow<Int> = callbackFlow {
		val listener = AdjustmentListener { e -> trySend(e.value) }
		scrollBar.addAdjustmentListener(listener)
		awaitClose { scrollBar.removeAdjustmentListener(listener) }
	}.distinctUntilChanged()

	/** 微调框回车确认（去重）。 */
	fun spinnerEnterPress(spinner: JSpinner): Flow<String> = callbackFlow {
		val listener = enterKeyListener { spinner.getValue().toString() }
		spinner.addKeyListener(listener)
		awaitClose { spinner.removeKeyListener(listener) }
	}.distinctUntilChanged()

	private fun ProducerScope<String>.enterKeyListener(valueSupplier: () -> String): KeyListener = object : KeyAdapter() {
		override fun keyPressed(ev: KeyEvent) {
			if (ev.keyCode == KeyEvent.VK_ENTER) {
				trySend(valueSupplier())
			}
		}
	}
}

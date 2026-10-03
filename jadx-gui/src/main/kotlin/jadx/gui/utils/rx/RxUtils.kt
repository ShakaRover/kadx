package jadx.gui.utils.rx

import io.reactivex.rxjava3.core.BackpressureStrategy
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.FlowableEmitter
import io.reactivex.rxjava3.core.FlowableOnSubscribe
import jadx.gui.utils.ui.DocumentUpdateListener
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.KeyListener
import java.util.function.Supplier
import javax.swing.JSpinner
import javax.swing.JTextField
import javax.swing.event.ChangeListener

/**
 * Swing 组件事件的 RxJava 封装工具。
 *
 * **做什么**：把文本框、微调框的“内容变化 / 回车确认”事件转成 [Flowable] 流，
 * 并做 `distinctUntilChanged` 去重，供搜索对话框做响应式联动。
 *
 * **为什么不是协程**：本阶段严格保留原 RxJava 线程模型。
 *
 * **为什么用 `object`**：全部是无状态静态工具方法，Kotlin 侧以 `RxUtils.xxx` 直接调用。
 */
object RxUtils {

	@JvmStatic
	fun textFieldChanges(textField: JTextField): Flowable<String> {
		val source = FlowableOnSubscribe<String> { emitter ->
			val listener = DocumentUpdateListener { emitter.onNext(textField.getText()) }
			textField.getDocument().addDocumentListener(listener)
			emitter.setDisposable(CustomDisposable(Runnable { textField.getDocument().removeDocumentListener(listener) }))
		}
		return Flowable.create(source, BackpressureStrategy.LATEST).distinctUntilChanged()
	}

	@JvmStatic
	fun textFieldEnterPress(textField: JTextField): Flowable<String> {
		val source = FlowableOnSubscribe<String> { emitter ->
			val keyListener = enterKeyListener(emitter, Supplier { textField.getText() })
			textField.addKeyListener(keyListener)
			emitter.setDisposable(CustomDisposable(Runnable { textField.removeKeyListener(keyListener) }))
		}
		return Flowable.create(source, BackpressureStrategy.LATEST).distinctUntilChanged()
	}

	@JvmStatic
	fun spinnerChanges(spinner: JSpinner): Flowable<String> {
		val source = FlowableOnSubscribe<String> { emitter ->
			val changeListener = ChangeListener { emitter.onNext(java.lang.String.valueOf(spinner.getValue())) }
			spinner.addChangeListener(changeListener)
			emitter.setDisposable(CustomDisposable(Runnable { spinner.removeChangeListener(changeListener) }))
		}
		return Flowable.create(source, BackpressureStrategy.LATEST).distinctUntilChanged()
	}

	@JvmStatic
	fun spinnerEnterPress(spinner: JSpinner): Flowable<String> {
		val source = FlowableOnSubscribe<String> { emitter ->
			val keyListener = enterKeyListener(emitter, Supplier { java.lang.String.valueOf(spinner.getValue()) })
			spinner.addKeyListener(keyListener)
			emitter.setDisposable(CustomDisposable(Runnable { spinner.removeKeyListener(keyListener) }))
		}
		return Flowable.create(source, BackpressureStrategy.LATEST).distinctUntilChanged()
	}

	private fun enterKeyListener(emitter: FlowableEmitter<String>, supplier: Supplier<String>): KeyListener = object : KeyAdapter() {
		override fun keyPressed(ev: KeyEvent) {
			if (ev.getKeyCode() == KeyEvent.VK_ENTER) {
				emitter.onNext(supplier.get())
			}
		}
	}
}

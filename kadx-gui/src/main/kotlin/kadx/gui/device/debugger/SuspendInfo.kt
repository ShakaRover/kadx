package kadx.gui.device.debugger

/**
 * 一次「JVM 挂起（suspend）」事件的上下文信息。
 *
 * **做什么**：保存挂起时的线程 ID、类 ID、方法 ID、代码偏移；
 * 并区分「值确实变了」与「只是收到新的一轮事件」。
 *
 * **为什么要区分新的一轮**：按 JDWP 规范，同一位置可能同时触发单步与断点两个事件，
 * 此时只应向上层通知一次，因此用 [InfoSetter.changed] 去重。
 */
class SuspendInfo {
	/** 远端 JVM 是否已终止。 */
	private var terminated = false

	/** 标记下一轮事件即使值相同也应视为「新事件」。 */
	private var newRound = false

	/** 真正承载数值的可复用更新器。 */
	private val updater = InfoSetter()

	/** @return 当前挂起线程 ID */
	val threadID: Long get() = updater.thread

	/** @return 当前挂起位置所在类的 ID */
	val classID: Long get() = updater.clazz

	/** @return 当前挂起位置所在方法的 ID */
	val methodID: Long get() = updater.method

	/** @return 当前挂起位置的代码偏移 */
	val offset: Long get() = updater.offset

	/**
	 * 开始新一轮挂起信息的收集，并返回可链式赋值的更新器。
	 */
	internal fun update(): InfoSetter {
		updater.changed = false
		updater.nextRound(newRound)
		newRound = false
		return updater
	}

	/**
	 * 由解码循环调用：即使数值没变，也说明它们来自另一个数据包，应视为新事件。
	 */
	internal fun nextRound() {
		newRound = true
	}

	/**
	 * @return 自上次 [update] 以来是否有任何字段发生变化
	 */
	internal val isAnythingChanged: Boolean get() = updater.changed

	/** @return 远端 JVM 是否已终止 */
	val isTerminated: Boolean get() = terminated

	/** 标记远端 JVM 已终止。 */
	internal fun setTerminated() {
		terminated = true
	}

	/**
	 * 挂起信息的可复用更新器（避免频繁创建对象）。
	 */
	internal class InfoSetter {
		var thread: Long = 0
		var clazz: Long = 0
		var method: Long = 0
		var offset: Long = 0 // 代码偏移
		var changed: Boolean = false

		fun nextRound(newRound: Boolean) {
			if (!changed) {
				changed = newRound
			}
		}

		fun updateThread(thread: Long): InfoSetter {
			if (!changed) {
				changed = this.thread != thread
			}
			this.thread = thread
			return this
		}

		fun updateClass(clazz: Long): InfoSetter {
			if (!changed) {
				changed = this.clazz != clazz
			}
			this.clazz = clazz
			return this
		}

		fun updateMethod(method: Long): InfoSetter {
			if (!changed) {
				changed = this.method != method
			}
			this.method = method
			return this
		}

		fun updateOffset(offset: Long): InfoSetter {
			if (!changed) {
				changed = this.offset != offset
			}
			this.offset = offset
			return this
		}
	}
}

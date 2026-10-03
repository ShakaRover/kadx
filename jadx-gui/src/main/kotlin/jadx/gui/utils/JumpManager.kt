package jadx.gui.utils

/**
 * 跳转历史管理器（前进/后退）。
 *
 * **做什么**：保存访问过的 [JumpPosition] 列表，并维护当前游标。
 * 列表过大时会从头部批量裁剪，避免每次插入都做 O(n) 的搬移。
 *
 * **为什么不是 `data class`**：有状态的可变对象，以身份语义使用。
 */
class JumpManager {

	/** 跳转列表。 */
	private val list: MutableList<JumpPosition> = ArrayList(MAX_JUMPS)

	/** 当前所在位置在 [list] 中的下标。 */
	private var currentPos = 0

	/**
	 * 追加一个跳转位置。
	 *
	 * 若当前处于历史中间位置（后退过），则丢弃后面的「前进历史」再写入。
	 */
	fun addPosition(pos: JumpPosition?) {
		if (pos == null || ignoreJump(pos)) {
			return
		}
		currentPos++
		if (currentPos >= list.size) {
			list.add(pos)
			if (list.size >= MAX_JUMPS) {
				// 批量裁剪头部，摊薄搬移成本
				list.subList(0, LIST_SHRINK_COUNT).clear()
			}
			currentPos = list.size - 1
		} else {
			// 后退后再跳转到新位置：丢弃当前位置之后的前进历史
			list[currentPos] = pos
			list.subList(currentPos + 1, list.size).clear()
		}
	}

	fun size(): Int = list.size

	private fun ignoreJump(pos: JumpPosition): Boolean {
		val current = getCurrent() ?: return false
		return pos == current
	}

	fun getCurrent(): JumpPosition? {
		if (currentPos >= 0 && currentPos < list.size) {
			return list[currentPos]
		}
		return null
	}

	fun getPrev(): JumpPosition? {
		if (currentPos == 0) {
			return null
		}
		currentPos--
		return list[currentPos]
	}

	fun getNext(): JumpPosition? {
		val size = list.size
		if (size == 0) {
			currentPos = 0
			return null
		}
		val newPos = currentPos + 1
		if (newPos >= size) {
			currentPos = size - 1
			return null
		}
		val position = list[newPos]
		currentPos = newPos
		return position
	}

	fun reset() {
		list.clear()
		currentPos = 0
	}

	companion object {
		/** 跳转列表保存的最大元素数。 */
		private const val MAX_JUMPS = 100

		/**
		 * 列表超过 [MAX_JUMPS] 时从头部移除的元素个数。
		 * 列表大多数时候只增不减，批量移除可以减少搬移次数；
		 * 因此实际历史长度会在 (MAX_JUMPS - LIST_SHRINK_COUNT) 到 MAX_JUMPS 之间浮动。
		 */
		private const val LIST_SHRINK_COUNT = 50
	}
}

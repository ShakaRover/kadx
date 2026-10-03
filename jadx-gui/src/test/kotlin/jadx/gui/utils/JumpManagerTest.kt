package jadx.gui.utils

import jadx.gui.treemodel.TextNode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * 跳转历史（前进 / 后退）测试。
 *
 * **做什么**：模拟多次 [JumpManager.addPosition]，校验 [JumpManager.getPrev] /
 * [JumpManager.getNext] 的游标移动与历史裁剪行为。
 */
class JumpManagerTest {

	private lateinit var jm: JumpManager

	@BeforeEach
	fun setup() {
		jm = JumpManager()
	}

	@Test
	fun testEmptyHistory() {
		assertThat(jm.prev).isNull()
		assertThat(jm.next).isNull()
	}

	@Test
	fun testEmptyHistory2() {
		assertThat(jm.prev).isNull()
		assertThat(jm.next).isNull()
		assertThat(jm.prev).isNull()
		assertThat(jm.next).isNull()
		assertThat(jm.prev).isNull()
	}

	@Test
	fun testOneElement() {
		jm.addPosition(makeJumpPos())

		assertThat(jm.prev).isNull()
		assertThat(jm.next).isNull()
	}

	@Test
	fun testTwoElements() {
		val pos1 = makeJumpPos()
		jm.addPosition(pos1)
		val pos2 = makeJumpPos()
		jm.addPosition(pos2)

		assertThat(jm.prev).isSameAs(pos1)
		assertThat(jm.prev).isNull()
		assertThat(jm.next).isSameAs(pos2)
		assertThat(jm.next).isNull()
	}

	@Test
	fun testNavigation() {
		val pos1 = makeJumpPos()
		jm.addPosition(pos1)
		// 1@
		val pos2 = makeJumpPos()
		jm.addPosition(pos2)
		// 1 - 2@
		assertThat(jm.prev).isSameAs(pos1)
		// 1@ - 2
		val pos3 = makeJumpPos()
		jm.addPosition(pos3)
		// 1 - 3@
		assertThat(jm.next).isNull()
		assertThat(jm.prev).isSameAs(pos1)
		// 1@ - 3
		assertThat(jm.next).isSameAs(pos3)
	}

	@Test
	fun testNavigation2() {
		val pos1 = makeJumpPos()
		jm.addPosition(pos1)
		// 1@
		val pos2 = makeJumpPos()
		jm.addPosition(pos2)
		// 1 - 2@
		val pos3 = makeJumpPos()
		jm.addPosition(pos3)
		// 1 - 2 - 3@
		val pos4 = makeJumpPos()
		jm.addPosition(pos4)
		// 1 - 2 - 3 - 4@
		assertThat(jm.prev).isSameAs(pos3)
		// 1 - 2 - 3@ - 4
		assertThat(jm.prev).isSameAs(pos2)
		// 1 - 2@ - 3 - 4
		val pos5 = makeJumpPos()
		jm.addPosition(pos5)
		// 1 - 2 - 5@
		assertThat(jm.next).isNull()
		assertThat(jm.next).isNull()
		assertThat(jm.prev).isSameAs(pos2)
		// 1 - 2@ - 5
		assertThat(jm.prev).isSameAs(pos1)
		// 1@ - 2 - 5
		assertThat(jm.prev).isNull()
		assertThat(jm.next).isSameAs(pos2)
		// 1 - 2@ - 5
		assertThat(jm.next).isSameAs(pos5)
		// 1 - 2 - 5@
		assertThat(jm.next).isNull()
	}

	@Test
	fun addSame() {
		val pos = makeJumpPos()
		jm.addPosition(pos)
		jm.addPosition(pos)

		assertThat(jm.prev).isNull()
		assertThat(jm.next).isNull()
	}

	private fun makeJumpPos(): JumpPosition = JumpPosition(TextNode(""), 0)
}

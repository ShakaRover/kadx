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
		assertThat(jm.getPrev()).isNull()
		assertThat(jm.getNext()).isNull()
	}

	@Test
	fun testEmptyHistory2() {
		assertThat(jm.getPrev()).isNull()
		assertThat(jm.getNext()).isNull()
		assertThat(jm.getPrev()).isNull()
		assertThat(jm.getNext()).isNull()
		assertThat(jm.getPrev()).isNull()
	}

	@Test
	fun testOneElement() {
		jm.addPosition(makeJumpPos())

		assertThat(jm.getPrev()).isNull()
		assertThat(jm.getNext()).isNull()
	}

	@Test
	fun testTwoElements() {
		val pos1 = makeJumpPos()
		jm.addPosition(pos1)
		val pos2 = makeJumpPos()
		jm.addPosition(pos2)

		assertThat(jm.getPrev()).isSameAs(pos1)
		assertThat(jm.getPrev()).isNull()
		assertThat(jm.getNext()).isSameAs(pos2)
		assertThat(jm.getNext()).isNull()
	}

	@Test
	fun testNavigation() {
		val pos1 = makeJumpPos()
		jm.addPosition(pos1)
		// 1@
		val pos2 = makeJumpPos()
		jm.addPosition(pos2)
		// 1 - 2@
		assertThat(jm.getPrev()).isSameAs(pos1)
		// 1@ - 2
		val pos3 = makeJumpPos()
		jm.addPosition(pos3)
		// 1 - 3@
		assertThat(jm.getNext()).isNull()
		assertThat(jm.getPrev()).isSameAs(pos1)
		// 1@ - 3
		assertThat(jm.getNext()).isSameAs(pos3)
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
		assertThat(jm.getPrev()).isSameAs(pos3)
		// 1 - 2 - 3@ - 4
		assertThat(jm.getPrev()).isSameAs(pos2)
		// 1 - 2@ - 3 - 4
		val pos5 = makeJumpPos()
		jm.addPosition(pos5)
		// 1 - 2 - 5@
		assertThat(jm.getNext()).isNull()
		assertThat(jm.getNext()).isNull()
		assertThat(jm.getPrev()).isSameAs(pos2)
		// 1 - 2@ - 5
		assertThat(jm.getPrev()).isSameAs(pos1)
		// 1@ - 2 - 5
		assertThat(jm.getPrev()).isNull()
		assertThat(jm.getNext()).isSameAs(pos2)
		// 1 - 2@ - 5
		assertThat(jm.getNext()).isSameAs(pos5)
		// 1 - 2 - 5@
		assertThat(jm.getNext()).isNull()
	}

	@Test
	fun addSame() {
		val pos = makeJumpPos()
		jm.addPosition(pos)
		jm.addPosition(pos)

		assertThat(jm.getPrev()).isNull()
		assertThat(jm.getNext()).isNull()
	}

	private fun makeJumpPos(): JumpPosition = JumpPosition(TextNode(""), 0)
}

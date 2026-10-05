package kadx.tests.integration.loops

import kadx.api.CommentsLevel
import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 各种非标准索引循环（步长不为 1、方向错误、越界等）都不应被误还原为 for-each。
 * 关闭编译以便只检查生成代码；并移除注释，因为输入文件信息注释总含冒号，会干扰断言。
 */
class TestArrayForEachNegative : IntegrationTest() {

	@Test
	fun test() {
		disableCompilation()
		// Remove all comments - as the comment created by CodeGenUtils.addInputFileInfo always contains a
		// colon
		getArgs().commentsLevel = CommentsLevel.NONE
		assertThat(getClassNode(TestArrayForEachNegativeFixture.TestCls::class.java))
			.code()
			.doesNotContain(":")
	}
}

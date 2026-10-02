package jadx.tests.integration.trycatch

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * try 内的读循环：catch 分支应保留为 IOException。
 */
class TestLoopInTryCatch2 : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestLoopInTryCatch2Fixture.TestCls::class.java))
			.code()
			.containsOne("} catch (IOException e) {")
	}
}

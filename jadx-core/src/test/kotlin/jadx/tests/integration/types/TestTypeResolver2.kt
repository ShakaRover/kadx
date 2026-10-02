package jadx.tests.integration.types

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 空值判断与异常抛出：`obj != null` 的判断应被正确还原。
 */
class TestTypeResolver2 : IntegrationTest() {

	@Test
	fun test() {
		noDebugInfo()
		assertThat(getClassNode(TestTypeResolver2Fixture.TestCls::class.java))
			.code()
			.containsOne("if (obj != null) {")
	}
}

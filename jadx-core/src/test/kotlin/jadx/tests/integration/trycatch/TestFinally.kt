package jadx.tests.integration.trycatch

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * finally 中的 cursor 判空关闭逻辑应被正确还原。
 */
class TestFinally : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestFinallyFixture.TestCls::class.java))
			.code()
			.containsOne("} finally {")
			.containsOne("cursor.getString(columnIndex);")
			.doesNotContain("String str = true;")
	}
}

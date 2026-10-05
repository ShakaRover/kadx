package kadx.tests.integration.trycatch

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * finally 中的 cursor 判空关闭逻辑应被正确还原。
 */
class TestFinally : IntegrationTest() {

	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestFinallyFixture.TestCls::class.java))
			.code()
			.containsOne("} finally {")
			.containsOne("cursor.getString(columnIndex);")
			.doesNotContain("String str = true;")
	}
}

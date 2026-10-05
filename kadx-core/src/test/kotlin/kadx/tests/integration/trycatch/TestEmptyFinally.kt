package kadx.tests.integration.trycatch

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 空的 finally 块应被忽略，只保留 catch 分支。
 */
class TestEmptyFinally : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestEmptyFinallyFixture.TestCls::class.java))
			.code()
			.containsOne("} catch (IOException e) {")
			.doesNotContain("} finally {")
	}
}

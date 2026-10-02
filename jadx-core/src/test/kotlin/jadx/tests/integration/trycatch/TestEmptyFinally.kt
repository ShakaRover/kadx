package jadx.tests.integration.trycatch

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
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

package kadx.tests.integration.inline

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * `System.nanoTime()` 调用不应被错误内联成 `System.nanoTime() - System.nanoTime()`。
 */
class TestInline6 : IntegrationTest() {

	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestInline6Fixture.TestCls::class.java))
			.code()
			.contains("System.out.println(System.nanoTime() - start);")
			.doesNotContain("System.out.println(System.nanoTime() - System.nanoTime());")
	}
}

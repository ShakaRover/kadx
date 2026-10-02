package jadx.tests.integration.inline

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * `System.nanoTime()` 调用不应被错误内联成 `System.nanoTime() - System.nanoTime()`。
 */
class TestInline6 : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestInline6Fixture.TestCls::class.java))
			.code()
			.contains("System.out.println(System.nanoTime() - start);")
			.doesNotContain("System.out.println(System.nanoTime() - System.nanoTime());")
	}
}

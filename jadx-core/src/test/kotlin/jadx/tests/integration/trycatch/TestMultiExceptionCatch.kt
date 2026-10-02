package jadx.tests.integration.trycatch

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * 多异常 catch 应合并为 `A | B e` 形式，不产生临时变量。
 */
class TestMultiExceptionCatch : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestMultiExceptionCatchFixture.TestCls::class.java))
			.code()
			.containsOne("try {")
			.containsOne("} catch (ProviderException | DateTimeException e) {")
			.containsOne("throw new RuntimeException(e);")
			.doesNotContain("RuntimeException e;")
	}
}

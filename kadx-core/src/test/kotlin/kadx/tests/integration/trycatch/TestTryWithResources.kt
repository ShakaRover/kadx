package kadx.tests.integration.trycatch

import kadx.NotYetImplemented
import kadx.tests.api.SmaliTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * try-with-resources 的还原当前尚未实现（已知未实现）。
 */
class TestTryWithResources : SmaliTest() {

	@Test
	@NotYetImplemented
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestTryWithResourcesFixture.TestCls::class.java))
			.code()
			.containsOne("try (")
			.doesNotContain("close()")
	}
}

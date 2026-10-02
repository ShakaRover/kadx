package jadx.tests.integration.trycatch

import jadx.NotYetImplemented
import jadx.tests.api.SmaliTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * try-with-resources 的还原当前尚未实现（已知未实现）。
 */
class TestTryWithResources : SmaliTest() {

	@Test
	@NotYetImplemented
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestTryWithResourcesFixture.TestCls::class.java))
			.code()
			.containsOne("try (")
			.doesNotContain("close()")
	}
}

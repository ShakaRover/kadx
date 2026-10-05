package kadx.tests.integration.inner

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * 内部类的多种样本（静态/非静态、访问静态/实例成员）：不应残留 synthetic 或 `access$` 方法。
 */
class TestInner2Samples : IntegrationTest() {

	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestInner2SamplesFixture.TestInner2::class.java))
			.code()
			.containsOne("setD(\"d\");")
			.doesNotContain("synthetic")
			.doesNotContain("access$")
	}
}

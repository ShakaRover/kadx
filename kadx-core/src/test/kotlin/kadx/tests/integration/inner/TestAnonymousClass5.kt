package kadx.tests.integration.inner

import kadx.NotYetImplemented
import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * 匿名类实现 Iterator/Iterable 并访问外部实例字段（NotYetImplemented）。
 */
class TestAnonymousClass5 : IntegrationTest() {

	@Test
	@NotYetImplemented
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestAnonymousClass5Fixture.TestCls::class.java))
			.code()
			.containsOne("map.get(name);")
			.doesNotContain("access$008")
			.doesNotContain("synthetic")
	}
}

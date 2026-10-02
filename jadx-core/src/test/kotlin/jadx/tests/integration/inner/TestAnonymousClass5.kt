package jadx.tests.integration.inner

import jadx.NotYetImplemented
import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * 匿名类实现 Iterator/Iterable 并访问外部实例字段（NotYetImplemented）。
 */
class TestAnonymousClass5 : IntegrationTest() {

	@Test
	@NotYetImplemented
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestAnonymousClass5Fixture.TestCls::class.java))
			.code()
			.containsOne("map.get(name);")
			.doesNotContain("access$008")
			.doesNotContain("synthetic")
	}
}

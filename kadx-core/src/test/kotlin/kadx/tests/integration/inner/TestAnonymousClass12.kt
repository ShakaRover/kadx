package kadx.tests.integration.inner

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * 嵌套匿名类：内外两层匿名类分别赋值给 outer / inner 字段。
 */
class TestAnonymousClass12 : IntegrationTest() {

	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestAnonymousClass12Fixture.TestCls::class.java))
			.code()
			.containsOne("outer = new BasicAbstract() {")
			.containsOne("inner = new BasicAbstract() {")
			.containsOne("inner = null;")
	}
}

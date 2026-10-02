package jadx.tests.integration.inner

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * 嵌套匿名类：内外两层匿名类分别赋值给 outer / inner 字段。
 */
class TestAnonymousClass12 : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestAnonymousClass12Fixture.TestCls::class.java))
			.code()
			.containsOne("outer = new BasicAbstract() {")
			.containsOne("inner = new BasicAbstract() {")
			.containsOne("inner = null;")
	}
}

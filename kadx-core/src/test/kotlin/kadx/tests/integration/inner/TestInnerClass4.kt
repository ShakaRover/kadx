package kadx.tests.integration.inner

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * 私有构造器的内部类：字段访问应内联为 `new C().c`。
 */
class TestInnerClass4 : IntegrationTest() {

	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestInnerClass4Fixture.TestCls::class.java))
			.code()
			.containsOne("return new C().c;")
	}
}

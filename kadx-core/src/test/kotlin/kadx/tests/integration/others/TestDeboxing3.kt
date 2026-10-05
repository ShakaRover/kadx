package kadx.tests.integration.others

import kadx.NotYetImplemented
import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * 拆箱（deboxing）与泛型传播：包装类型为 null 时赋默认值；完整泛型传播为已知未实现。
 */
class TestDeboxing3 : IntegrationTest() {

	@Test
	fun test() {
		noDebugInfo()

		KadxAssertions.assertThat(getClassNode(TestDeboxing3Fixture.TestCls::class.java))
			.code()
			.containsOne("l = 900000L;")
	}

	@Test
	@NotYetImplemented("Full deboxing and generics propagation")
	fun testFull() {
		noDebugInfo()

		KadxAssertions.assertThat(getClassNode(TestDeboxing3Fixture.TestCls::class.java))
			.code()
			.containsOne("Pair<Long, String> pair = this.cache.get(id);")
			.containsOne("return pair.first + l > System.currentTimeMillis();")
	}
}

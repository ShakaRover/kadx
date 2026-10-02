package jadx.tests.integration.others

import jadx.NotYetImplemented
import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * 拆箱（deboxing）与泛型传播：包装类型为 null 时赋默认值；完整泛型传播为已知未实现。
 */
class TestDeboxing3 : IntegrationTest() {

	@Test
	fun test() {
		noDebugInfo()

		JadxAssertions.assertThat(getClassNode(TestDeboxing3Fixture.TestCls::class.java))
			.code()
			.containsOne("l = 900000L;")
	}

	@Test
	@NotYetImplemented("Full deboxing and generics propagation")
	fun testFull() {
		noDebugInfo()

		JadxAssertions.assertThat(getClassNode(TestDeboxing3Fixture.TestCls::class.java))
			.code()
			.containsOne("Pair<Long, String> pair = this.cache.get(id);")
			.containsOne("return pair.first + l > System.currentTimeMillis();")
	}
}

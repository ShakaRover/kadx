package jadx.tests.integration.generics

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 内部类的泛型构造器参数：应保留类型变量 `T`。
 */
class TestGeneric8 : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestGeneric8Fixture.TestCls::class.java))
			.code()
			.containsOne("public TestNumber(T n")
	}
}

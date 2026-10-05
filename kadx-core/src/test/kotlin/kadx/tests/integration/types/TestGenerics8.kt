package kadx.tests.integration.types

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 交叉类型边界 `S extends I1 & I2` 的方法调用：`s.i1()` / `s.i2()` 均应正确还原。
 */
class TestGenerics8 : IntegrationTest() {

	@Test
	fun test() {
		noDebugInfo()
		assertThat(getClassNode(TestGenerics8Fixture.TestCls::class.java))
			.code()
			.containsOne("S s = get();")
			.containsOne("s.i1();")
			.containsOne("s.i2();")
	}
}

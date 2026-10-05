package kadx.tests.integration.invoke

import kadx.tests.api.SmaliTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 匿名内部类中调用外层重载方法：字符串参数不应被误加类型转换。
 */
class TestCastInOverloadedAccessor : SmaliTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestCastInOverloadedAccessorFixture.X::class.java))
			.code()
			.containsOne("outerMethod(\"\")")
			.containsOne("outerMethod(\"\", \"\")")
	}
}

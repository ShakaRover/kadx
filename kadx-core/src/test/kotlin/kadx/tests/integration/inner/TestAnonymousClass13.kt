package kadx.tests.integration.inner

import kadx.tests.api.IntegrationTest
import org.junit.jupiter.api.Test

/**
 * 匿名子类实例化：仅验证可正常反编译，不抛异常。
 */
class TestAnonymousClass13 : IntegrationTest() {

	@Test
	fun test() {
		getClassNode(TestAnonymousClass13Fixture.TestCls::class.java)
	}
}

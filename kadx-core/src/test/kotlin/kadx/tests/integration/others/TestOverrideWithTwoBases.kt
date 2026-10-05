package kadx.tests.integration.others

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 双基类覆写：一个类同时继承抽象基类并实现接口，两者声明同名方法，
 * 反编译后应合并为一个方法并标记 `@Override`。
 */
class TestOverrideWithTwoBases : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestOverrideWithTwoBasesFixture.TestCls::class.java))
			.code()
			.containsOne("@Override")
	}
}

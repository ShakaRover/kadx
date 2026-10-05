package kadx.tests.integration.others

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 字段初始化搬移的负例（#1599）：依赖其它实例方法的初始化不能被搬移。
 */
class TestFieldInitNegative : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestFieldInitNegativeFixture.TestCls::class.java))
			.code()
			.doesNotContain("int field = initField();")
			.containsOne("this.field = initField();")
	}
}

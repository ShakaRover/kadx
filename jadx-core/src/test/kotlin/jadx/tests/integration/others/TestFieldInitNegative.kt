package jadx.tests.integration.others

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
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

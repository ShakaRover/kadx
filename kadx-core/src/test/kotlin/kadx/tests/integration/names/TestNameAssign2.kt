package kadx.tests.integration.names

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 变量名分配（phi 插入算法）：不应产生冗余的局部变量声明。
 */
class TestNameAssign2 : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestNameAssign2Fixture.TestCls::class.java))
			.code()
			.doesNotContain("int id;")
	}
}

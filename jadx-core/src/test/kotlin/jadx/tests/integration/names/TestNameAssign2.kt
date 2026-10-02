package jadx.tests.integration.names

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
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

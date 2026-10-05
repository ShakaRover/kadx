package kadx.tests.integration.others

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * 字段初始化位置（继承）：父类字段与子类同名字段的初始化应分别输出。
 */
class TestFieldInit3 : IntegrationTest() {

	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestFieldInit3Fixture.TestCls::class.java))
			.code()
			.containsOne("public int field = 4;")
			.containsOne("field = 7;")
			.containsOne("field = 9;")
			.containsOne("public int other = 11;")
	}
}

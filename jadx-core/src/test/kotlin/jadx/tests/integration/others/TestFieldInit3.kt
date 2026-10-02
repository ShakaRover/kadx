package jadx.tests.integration.others

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * 字段初始化位置（继承）：父类字段与子类同名字段的初始化应分别输出。
 */
class TestFieldInit3 : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestFieldInit3Fixture.TestCls::class.java))
			.code()
			.containsOne("public int field = 4;")
			.containsOne("field = 7;")
			.containsOne("field = 9;")
			.containsOne("public int other = 11;")
	}
}

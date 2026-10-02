package jadx.tests.integration.others

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * 字段初始化顺序（匿名类）：匿名内部类字段与构造器初始化应正确输出。
 */
class TestFieldInit2 : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestFieldInit2Fixture.TestCls::class.java))
			.code()
			.containsOne("x = new BasicAbstract() {")
			.containsOne("y = 0;")
			.containsLines(1, "public TestFieldInit2Fixture\$TestCls(int z) {", "}")
	}
}

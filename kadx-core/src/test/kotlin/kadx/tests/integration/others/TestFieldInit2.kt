package kadx.tests.integration.others

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * 字段初始化顺序（匿名类）：匿名内部类字段与构造器初始化应正确输出。
 */
class TestFieldInit2 : IntegrationTest() {

	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestFieldInit2Fixture.TestCls::class.java))
			.code()
			.containsOne("x = new BasicAbstract() {")
			.containsOne("y = 0;")
			.containsLines(1, "public TestFieldInit2Fixture\$TestCls(int z) {", "}")
	}
}

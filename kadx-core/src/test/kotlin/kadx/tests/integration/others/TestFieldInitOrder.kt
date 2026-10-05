package kadx.tests.integration.others

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 实例字段初始化顺序：按声明顺序的链式初始化应正确还原，且无构造器。
 */
class TestFieldInitOrder : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestFieldInitOrderFixture.TestCls::class.java))
			.code()
			.doesNotContain("TestCls() {") // constructor removed
			.doesNotContain("String result;")
			.containsOne("String result = this.sb.toString();")
	}
}

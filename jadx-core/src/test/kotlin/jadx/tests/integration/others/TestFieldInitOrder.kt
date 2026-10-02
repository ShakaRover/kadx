package jadx.tests.integration.others

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
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

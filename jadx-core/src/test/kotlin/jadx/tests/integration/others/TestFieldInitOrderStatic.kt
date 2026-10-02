package jadx.tests.integration.others

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 静态字段初始化顺序：static 块应被还原为字段初始化表达式。
 */
class TestFieldInitOrderStatic : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestFieldInitOrderStaticFixture.TestCls::class.java))
			.code()
			.doesNotContain("static {")
			.doesNotContain("String result;")
			.containsOne("String result = sb.toString();")
	}
}

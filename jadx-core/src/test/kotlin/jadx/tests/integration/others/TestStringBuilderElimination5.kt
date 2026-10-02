package jadx.tests.integration.others

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 循环内字符串拼接：应被还原为 `+` 表达式，输出中不应残留 `.append(`。
 */
class TestStringBuilderElimination5 : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestStringBuilderElimination5Fixture.TestCls::class.java))
			.code()
			.doesNotContain(".append(")
	}
}

package jadx.tests.integration.conditions

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 内联赋值：`||` 右侧的赋值表达式应保持为内联赋值而非拆分。
 */
class TestInnerAssign2 : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestInnerAssign2Fixture.TestCls::class.java))
			.code()
			.containsOne("sub = this.field")
			.containsOne("return call(str) || ((sub = this.field) != null && sub.isEmpty());")
	}
}

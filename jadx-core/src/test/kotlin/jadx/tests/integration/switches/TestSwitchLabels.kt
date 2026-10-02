package jadx.tests.integration.switches

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * switch case 使用常量标签：应还原常量名而非字面量。
 */
class TestSwitchLabels : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestSwitchLabelsFixture.TestCls::class.java))
			.code()
			.contains("case CONST_ABC")
			.contains("return CONST_CDE;")
			.doesNotContain("case CONST_CDE_PRIVATE")
			.contains(".CONST_ABC;")
	}

	@Test
	fun testWithDisabledConstReplace() {
		getArgs().isReplaceConsts = false
		assertThat(getClassNode(TestSwitchLabelsFixture.TestCls::class.java))
			.code()
			.doesNotContain("case CONST_ABC")
			.contains("case 2748")
			.doesNotContain("return CONST_CDE;")
			.contains("return 3294;")
			.doesNotContain("case CONST_CDE_PRIVATE")
			.doesNotContain(".CONST_ABC;")
	}
}

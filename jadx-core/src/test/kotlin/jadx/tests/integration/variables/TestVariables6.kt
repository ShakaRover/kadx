package jadx.tests.integration.variables

import jadx.tests.api.SmaliTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * smali 测试：寄存器不应泄漏为 `r4`/`r1v1`，
 * 而应还原为有意义的变量名与字段访问链。
 */
class TestVariables6 : SmaliTest() {

	@Test
	fun test() {
		disableCompilation()
		assertThat(getClassNodeFromSmaliWithPath("variables", "TestVariables6"))
			.code()
			.doesNotContain("r4")
			.doesNotContain("r1v1")
			.contains("DateStringParser dateStringParser")
			.contains(
				"FinancialInstrumentMetadataAttribute startYear =" +
					" this.mFinancialInstrumentMetadataDefinition.getStartYear();",
			)
	}
}

package kadx.tests.integration.conditions

import kadx.tests.api.SmaliTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * 当 IF 谓词的两部分之间存在无法内联的中间指令时，优化后的 dex 无法把两个 IF 块合并为同一区域。
 * 两个 IF 块会共享同一个 ELSE 块，因此反编译结果不再是单个 if-else，但优于反编译失败。
 */
class TestIfElseAndConditionIntermediateInstruction : SmaliTest() {

	// @formatter:off
	/*
		private boolean bool;
		private float num;
		private static final float CONST = 342;

		public void function() {
			if (bool && num < 1) {
				num += CONST;
			} else {
				nothing2();
			}
			nothing1();
		}

		private void nothing1() {

		}

		private void nothing2() {

		}
	 */
	// @formatter:on

	@Test
	fun test() {
		allowWarnInCode()
		KadxAssertions.assertThat(getClassNodeFromSmali())
			.code()
			.countString(2, "else")
			.countString(2, "nothing2();")
			.countString(1, "nothing1();")
			.countString(1, "if (this.bool)")
			.countString(1, "if (f < 1.0f)")
	}
}

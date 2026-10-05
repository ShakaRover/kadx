package kadx.tests.integration.conditions

import kadx.tests.api.SmaliTest
import kadx.tests.api.utils.TestUtils.Companion.indent
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Issue #1455
 */
class TestIfCodeStyle : SmaliTest() {

	@Test
	fun test() {
		noDebugInfo()
		assertThat(getClassNode(TestIfCodeStyleFixture.TestCls::class.java))
			.code()
			// allow one last 'else'
			.oneOf(
				{ c -> c.doesNotContain("else").countString(8, "return;") },
				{ c -> c.countString(1, "else").countString(7, "return;") },
			)
			.containsLines(
				2,
				"if (i < 0) {",
				indent() + "if (iDataPosition > Integer.MAX_VALUE - i) {",
				indent(2) + "throw new RuntimeException(\"Overflow in the size of parcelable\");",
				indent() + "}",
				indent() + "parcel.setDataPosition(iDataPosition + i);",
				indent() + "return;",
				"}",
			)
	}

	@Test
	fun testSmali() {
		disableCompilation()
		assertThat(getClassNodeFromSmali())
			.code()
			// allow one last 'else'
			.oneOf(
				{ c -> c.doesNotContain("else").countString(8, "return;") },
				{ c -> c.countString(1, "else").countString(7, "return;") },
			)
			.containsLines(
				2,
				"if (_aidl_parcelable_size < 0) {",
				indent() + "if (_aidl_start_pos > Integer.MAX_VALUE - _aidl_parcelable_size) {",
				indent(2) + "throw new RuntimeException(\"Overflow in the size of parcelable\");",
				indent() + "}",
				indent() + "_aidl_parcel.setDataPosition(_aidl_start_pos + _aidl_parcelable_size);",
				indent() + "return;",
				"}",
			)
	}
}

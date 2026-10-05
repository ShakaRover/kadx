package kadx.tests.integration.invoke

import kadx.tests.api.SmaliTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 构造器调用中寄存器多次搬移：应还原为单条 `new Boolean("test")` 调用。
 */
class TestConstructorWithMoves : SmaliTest() {
	// @formatter:off
	/*
		public boolean test() {
				java.lang.Boolean r5 = new java.lang.Boolean
				r8 = r5
				r5 = r8
				r6 = r8
				java.lang.String r7 = "test"
				r6.<init>(r7)
				java.lang.Boolean r5 = (java.lang.Boolean) r5
				boolean r5 = r5.booleanValue()
				r3 = r5
				return r3
		}
	 */
	// @formatter:on

	@Test
	fun test() {
		assertThat(getClassNodeFromSmali())
			.code()
			.containsOne("return new Boolean(\"test\").booleanValue();")
	}
}

package jadx.tests.integration.types

import jadx.tests.api.SmaliTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 基本类型转换：`boolean` 转 `byte` 参数时应补回 `(byte) 1 : (byte) 0` 的三元强转。
 */
class TestPrimitiveConversion : SmaliTest() {
	// @formatter:off
	/*
		public void test(long j, boolean z) {
			putByte(j, z ? (byte) 1 : (byte) 0);
		}

		private static void putByte(long j, byte z) {
		}
	 */
	// @formatter:on

	@Test
	fun test() {
		assertThat(getClassNodeFromSmali())
			.code()
			.doesNotContain("putByte(j, z);")
			.containsOne("putByte(j, z ? (byte) 1 : (byte) 0);")
	}
}

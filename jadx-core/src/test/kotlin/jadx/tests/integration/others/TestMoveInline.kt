package jadx.tests.integration.others

import jadx.tests.api.SmaliTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 内联指令移动：位运算与自增的顺序必须保持 `i >>>= 7;` 在 `i2++;` 之前。
 */
@Suppress("CommentedOutCode")
class TestMoveInline : SmaliTest() {
	// @formatter:off
	/*
		public final void Y(int i) throws k {
			int i2 = 0;
			while ((i & (-128)) != 0) {
				this.h[i2] = (byte) ((i & 127) | 128);
				i >>>= 7;
				i2++;
			}
			byte[] bArr = this.h;
			bArr[i2] = (byte) i;
			this.a.k(bArr, 0, i2 + 1);
		}
	 */
	// @formatter:on

	@Test
	fun test() {
		assertThat(getClassNodeFromSmali())
			.code()
			// check operations order
			.containsLines(
				3,
				"i >>>= 7;",
				"i2++;",
			)
	}
}

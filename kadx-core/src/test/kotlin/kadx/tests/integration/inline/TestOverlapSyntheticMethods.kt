package kadx.tests.integration.inline

import kadx.tests.api.SmaliTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test
import java.util.Collections

/**
 * 桥接方法重名：默认会重命名其中一个（`m0a`），关闭重命名后两个同名方法并存。
 */
class TestOverlapSyntheticMethods : SmaliTest() {
	// @formatter:off
	/*
		public String test(int i) {
			return a(i) + "|" + a(i);
		}

		public int a(int i) {
			return i;
		}

		public String a(int i) {
			return "i:" + i;
		}
	 */
	// @formatter:on

	@Test
	fun testSmali() {
		assertThat(getClassNodeFromSmali())
			.code()
			.containsOne("int a(int i) {")
			.containsOne("String m0a(int i) {")
	}

	@Test
	fun testSmaliNoRename() {
		getArgs().renameFlags = Collections.emptySet()
		disableCompilation()
		assertThat(getClassNodeFromSmali())
			.code()
			.containsOne("int a(int i) {")
			.containsOne("String a(int i) {")
			.containsOne("return a(i) + \"|\" + a(i);")
	}
}

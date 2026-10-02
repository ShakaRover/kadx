package jadx.tests.integration.enums

import jadx.tests.api.SmaliTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 枚举 switch：应在顶层类生成重映射数组（`synthetic`），
 * 并支持 Java 21 直接按 ordinal 分派的 smali 形式。
 */
class TestSwitchOverEnum : SmaliTest() {

	@Test
	fun test() {
		// remapping array placed in top class, place test also in top class
		assertThat(getClassNode(TestSwitchOverEnumFixture::class.java))
			.code()
			.doesNotContain("synthetic")
			.countString(1, "switch (c) {")
			.countString(1, "case ONE:")
	}

	/**
	 * Java 21 compiler can omit a remapping array and use switch over ordinal directly
	 */
	@Test
	fun testSmaliDirect() {
		assertThat(getClassNodeFromSmaliFiles())
			.code()
			.containsOne("switch (v) {")
			.containsOne("case ONE:")
	}
}

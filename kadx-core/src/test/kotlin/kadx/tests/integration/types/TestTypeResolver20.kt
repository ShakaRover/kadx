package kadx.tests.integration.types

import kadx.tests.api.SmaliTest
import kadx.tests.api.extensions.profiles.TestProfile
import kadx.tests.api.extensions.profiles.TestWithProfiles
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Issue 1238：泛型迭代器取值应还原为 `T next = it.next();`，不出现 `next = next;` 这类自赋值。
 */
class TestTypeResolver20 : SmaliTest() {

	@TestWithProfiles(TestProfile.DX_J8, TestProfile.JAVA8)
	fun test() {
		noDebugInfo()
		assertThat(getClassNode(TestTypeResolver20Fixture.TestCls::class.java))
			.code()
			.doesNotContain("next = next;")
			.containsOne("T next = it.next();")
	}

	@Test
	fun testSmali() {
		assertThat(getClassNodeFromSmaliFiles())
			.code()
			.containsOne("T next = it.next();")
			.containsOne("T next2 = it.next();")
	}
}

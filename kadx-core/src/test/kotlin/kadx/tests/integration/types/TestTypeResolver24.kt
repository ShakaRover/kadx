package kadx.tests.integration.types

import kadx.tests.api.SmaliTest
import kadx.tests.api.extensions.profiles.TestProfile
import kadx.tests.api.extensions.profiles.TestWithProfiles
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 对 `null` 的强转调用不应产生临时变量：直接还原为 `((T1) null).foo1();` 等形式。
 */
class TestTypeResolver24 : SmaliTest() {

	@TestWithProfiles(TestProfile.DX_J8, TestProfile.JAVA8)
	fun test() {
		noDebugInfo()
		assertThat(getClassNode(TestTypeResolver24Fixture.TestCls::class.java))
			.code()
			.containsOne("((T1) null).foo1();")
			.containsOne("((T2) null).foo2();")
	}

	@Test
	fun testSmali() {
		assertThat(searchCls(loadFromSmaliFiles(), "Test1"))
			.code()
			.containsOne("((T1) null).foo1();")
			.containsOne("((T2) null).foo2();")
			.doesNotContain("T1 ")
			.doesNotContain("T2 ")
	}
}

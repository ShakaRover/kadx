package kadx.tests.integration.others

import kadx.tests.api.SmaliTest
import kadx.tests.api.extensions.profiles.TestProfile
import kadx.tests.api.extensions.profiles.TestWithProfiles
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 字段使用搬移：instanceof 分支中的 println 字符串拼接不应被错误搬移。
 */
class TestFieldUsageMove : SmaliTest() {

	@TestWithProfiles(TestProfile.D8_J11)
	fun test() {
		noDebugInfo()
		assertThat(getClassNode(TestFieldUsageMoveFixture.TestCls::class.java))
			.code()
			.containsOne("System.out.println(\"Boolean: \" +")
			.containsOne("System.out.println(\"Float: \" +")
	}

	@Test
	fun testSmali() {
		assertThat(getClassNodeFromSmali())
			.code()
			.containsOne("System.out.println(\"Boolean: \" +")
			.containsOne("System.out.println(\"Float: \" +")
	}
}

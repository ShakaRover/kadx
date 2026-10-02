package jadx.tests.integration.others

import jadx.tests.api.SmaliTest
import jadx.tests.api.extensions.profiles.TestProfile
import jadx.tests.api.extensions.profiles.TestWithProfiles
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
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

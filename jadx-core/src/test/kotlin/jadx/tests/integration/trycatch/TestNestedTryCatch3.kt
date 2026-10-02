package jadx.tests.integration.trycatch

import jadx.tests.api.IntegrationTest
import jadx.tests.api.extensions.profiles.TestProfile
import jadx.tests.api.extensions.profiles.TestWithProfiles
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat

/**
 * 嵌套 try/catch 中各自返回不同的实现类，应保持各自的 return 分支。
 */
class TestNestedTryCatch3 : IntegrationTest() {

	@TestWithProfiles(TestProfile.JAVA8, TestProfile.DX_J8)
	fun test() {
		assertThat(getClassNode(TestNestedTryCatch3Fixture.TestCls::class.java))
			.code()
			.containsOne("return new A();")
			.containsOne("return new B();")
			.containsOne("return new C();")
			.countString(2, "} catch (Throwable ")
	}
}

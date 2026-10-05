package kadx.tests.integration.trycatch

import kadx.tests.api.IntegrationTest
import kadx.tests.api.extensions.profiles.TestProfile
import kadx.tests.api.extensions.profiles.TestWithProfiles
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat

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

package jadx.tests.integration.invoke

import jadx.tests.api.IntegrationTest
import jadx.tests.api.extensions.profiles.TestProfile
import jadx.tests.api.extensions.profiles.TestWithProfiles
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat

/**
 * 数组维度不同的重载方法调用：应选择正确重载并补足必要转换。
 */
class TestOverloadedInvoke : IntegrationTest() {

	@TestWithProfiles(TestProfile.DX_J8, TestProfile.D8_J11, TestProfile.JAVA8)
	fun test() {
		noDebugInfo()
		assertThat(getClassNode(TestOverloadedInvokeFixture.TestCls::class.java))
			.code()
			.containsOne("use(iArr[0], -2);")
			.containsOne("use((Object[][]) iArr, (Object) (-1));")
		// TODO: don't add unnecessary casts
		// .containsOne("use(iArr, -1);");
		// TODO: replace call `Array.newInstance` with new array creation: `new int[N][N][N]`
		// .containsOne("new int[10][10][10];");
	}
}

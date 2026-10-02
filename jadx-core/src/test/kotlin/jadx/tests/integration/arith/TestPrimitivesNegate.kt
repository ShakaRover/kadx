package jadx.tests.integration.arith

import jadx.tests.api.IntegrationTest
import jadx.tests.api.extensions.profiles.TestProfile
import jadx.tests.api.extensions.profiles.TestWithProfiles
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat

/**
 * 原始类型取负：数组元素赋负数与 `-=` 复合赋值应保留负号与 `d` 后缀。
 */
class TestPrimitivesNegate : IntegrationTest() {

	@TestWithProfiles(TestProfile.DX_J8, TestProfile.JAVA8)
	fun test() {
		noDebugInfo()
		assertThat(getClassNode(TestPrimitivesNegateFixture.TestCls::class.java))
			.code()
			.containsOne("dArr[0] = -20.0d;")
			.containsOne("dArr[0] = dArr[0] - 79.0d;")
	}
}

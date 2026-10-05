package kadx.tests.integration.arrays

import kadx.tests.api.IntegrationTest
import kadx.tests.api.extensions.profiles.TestProfile
import kadx.tests.api.extensions.profiles.TestWithProfiles
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat

/**
 * byte 数组填充在 ECJ 各 profile 下都应还原为数组字面量。
 */
class TestArrayFill3 : IntegrationTest() {

	@TestWithProfiles(TestProfile.ECJ_J8, TestProfile.ECJ_DX_J8)
	fun test() {
		assertThat(getClassNode(TestArrayFill3Fixture.TestCls::class.java))
			.code()
			.containsOne("return new byte[]{0, 1, 2}")
	}
}

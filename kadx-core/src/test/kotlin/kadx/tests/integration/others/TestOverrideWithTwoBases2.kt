package kadx.tests.integration.others

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 双基类覆写：同一接口经父类与直接实现两条路径，仍应标记 `@Override`。
 */
class TestOverrideWithTwoBases2 : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestOverrideWithTwoBases2Fixture.TestCls::class.java))
			.code()
			.containsOne("@Override")
	}
}

package jadx.tests.integration.others

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
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

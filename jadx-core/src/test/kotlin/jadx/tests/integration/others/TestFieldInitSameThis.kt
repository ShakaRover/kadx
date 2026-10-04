package jadx.tests.integration.others

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 构造器字段初始化使用各自 `this` 时，视为相同初始化，可提取为字段初始化。
 */
class TestFieldInitSameThis : IntegrationTest() {

	@Test
	fun test() {
		getArgs().isDebugInfo = false
		assertThat(getClassNode(TestFieldInitSameThisFixture.TestCls::class.java))
			.code()
			.containsOne("final int value = System.identityHashCode(this);")
	}
}

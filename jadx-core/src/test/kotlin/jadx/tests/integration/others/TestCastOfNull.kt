package jadx.tests.integration.others

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 验证对 null 的强制类型转换能被保留：重载方法调用需要靠 cast 区分目标类型。
 */
class TestCastOfNull : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestCastOfNullFixture.TestCls::class.java))
			.code()
			.containsOne("m((long[]) null);")
			.containsOne("m((String) null);")
			.containsOne("m((List<String>) null);")
	}
}

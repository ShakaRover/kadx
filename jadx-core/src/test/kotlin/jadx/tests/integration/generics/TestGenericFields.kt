package jadx.tests.integration.generics

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 嵌套泛型字段访问：应推断出 `Amount amount =` 而不是裸类型变量 `T t =`。
 */
class TestGenericFields : IntegrationTest() {

	@Test
	fun test() {
		noDebugInfo()
		assertThat(getClassNode(TestGenericFieldsFixture.TestCls::class.java))
			.code()
			.doesNotContain("T t = ")
			.containsOne("Amount amount =")
	}
}

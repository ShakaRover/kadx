package kadx.tests.integration.generics

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
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

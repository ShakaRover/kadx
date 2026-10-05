package kadx.tests.integration.generics

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * 泛型数组类型：`Class<?>[]` 不应被擦除为 `Class[]`。
 */
class TestGenerics4 : IntegrationTest() {

	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestGenerics4Fixture.TestCls::class.java))
			.code()
			.contains("Class<?>[] a =")
			.doesNotContain("Class[] a =")
	}
}

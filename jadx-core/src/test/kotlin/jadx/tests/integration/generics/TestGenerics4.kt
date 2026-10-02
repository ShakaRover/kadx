package jadx.tests.integration.generics

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * 泛型数组类型：`Class<?>[]` 不应被擦除为 `Class[]`。
 */
class TestGenerics4 : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestGenerics4Fixture.TestCls::class.java))
			.code()
			.contains("Class<?>[] a =")
			.doesNotContain("Class[] a =")
	}
}

package kadx.tests.integration.generics

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * 通配符边界：`? extends` 与 `? super` 都应保留（含基本类型数组）。
 */
class TestGenerics3 : IntegrationTest() {

	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestGenerics3Fixture.TestCls::class.java))
			.code()
			.contains("mthExtendsArray(List<? extends byte[]> list)")
			.contains("mthSuperArray(List<? super int[]> list)")
			.contains("mthSuperInteger(List<? super Integer> list)")
			.contains("mthExtendsString(List<? super String> list)")
	}
}

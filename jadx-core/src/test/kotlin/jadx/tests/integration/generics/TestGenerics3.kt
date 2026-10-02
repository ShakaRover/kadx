package jadx.tests.integration.generics

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * 通配符边界：`? extends` 与 `? super` 都应保留（含基本类型数组）。
 */
class TestGenerics3 : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestGenerics3Fixture.TestCls::class.java))
			.code()
			.contains("mthExtendsArray(List<? extends byte[]> list)")
			.contains("mthSuperArray(List<? super int[]> list)")
			.contains("mthSuperInteger(List<? super Integer> list)")
			.contains("mthExtendsString(List<? super String> list)")
	}
}

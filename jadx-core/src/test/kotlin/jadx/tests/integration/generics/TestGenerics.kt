package jadx.tests.integration.generics

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * 方法参数中的通配符：无界、`extends`、`super` 三种形式都应保留。
 */
class TestGenerics : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestGenericsFixture.TestCls::class.java))
			.code()
			.contains("mthWildcard(List<?> list)")
			.contains("mthExtends(List<? extends A> list)")
			.contains("mthSuper(List<? super A> list)")
	}
}

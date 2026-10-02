package jadx.tests.integration.types

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 构造器 `this(...)` 调用中移动寄存器的还原：应保留 `this(b1, b2, 0, 0, 0);`，不出现 `= this;`。
 */
class TestTypeResolver : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestTypeResolverFixture.TestCls::class.java))
			.code()
			.contains("this(b1, b2, 0, 0, 0);")
			.doesNotContain("= this;")
	}
}

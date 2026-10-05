package kadx.tests.integration.types

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
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

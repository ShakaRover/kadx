package jadx.tests.integration.enums

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 枚举内使用 `EnumSet`/`ArrayList` 静态字段：反编译时不应插入多余的 `(Enum)` 强转。
 */
class TestEnums9 : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestEnums9Fixture.TestCls::class.java))
			.code()
			.doesNotContain("EnumSet.of((Enum) INT,")
	}
}

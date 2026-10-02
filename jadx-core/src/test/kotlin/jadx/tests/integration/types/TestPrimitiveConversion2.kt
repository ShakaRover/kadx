package jadx.tests.integration.types

import jadx.tests.api.SmaliTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * boolean 参与位运算时的原始类型还原：应生成三元表达式而非 `z2 == 0` / `z2 | 2` 之类的错误形式。
 */
class TestPrimitiveConversion2 : SmaliTest() {

	@Test
	fun test() {
		disableCompilation()
		assertThat(getClassNodeFromSmali())
			.code()
			.containsOne("boolean z2 = !convertedPrice2.code.equals(itemCurrency.code);")
			.doesNotContain("z2 == 0")
			.doesNotContain("z2 | 2")
			.containsOne("(z2 ? 1 : 0) | 2")
			.containsOne("if (z2 && currency != null) {")
			.containsOne("i = 1;")
	}
}

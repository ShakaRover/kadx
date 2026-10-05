package kadx.tests.integration.trycatch

import kadx.tests.api.SmaliTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 枚举 switch 重映射构建中的空 catch 块：应生成 5 个 try 与 5 个 NoSuchFieldError 的 catch。
 */
class TestEmptyCatch : SmaliTest() {

	@Test
	fun test() {
		disableCompilation()
		assertThat(getClassNodeFromSmali())
			.code()
			.countString(5, "try {")
			.countString(5, "} catch (NoSuchFieldError unused")
	}
}

package jadx.tests.integration.trycatch

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * 嵌套 try/catch：内外 catch 变量应重命名为 e/e2，且不引入多余 return。
 */
class TestNestedTryCatch : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestNestedTryCatchFixture.TestCls::class.java))
			.code()
			.contains("try {")
			.contains("Thread.sleep(1L);")
			.contains("Thread.sleep(2L);")
			.contains("} catch (InterruptedException e) {")
			.contains("} catch (Exception e2) {")
			.doesNotContain("return")
	}
}

package jadx.tests.integration.trycatch

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * catch 块中内联的变量判空与资源释放逻辑应保持可读结构。
 */
class TestInlineInCatch : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestInlineInCatchFixture.TestCls::class.java))
			.code()
			.containsOne("File output = null;")
			.containsOne("output = File.createTempFile(\"f\", \"a\", ")
			.containsOne("return 0;")
			.containsOne("} catch (Exception e) {")
			.containsOne("if (output != null) {")
			.containsOne("output.delete();")
			.containsOne("return 2;")
	}
}

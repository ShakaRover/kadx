package jadx.tests.integration.others

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 异常构造器中的字符串拼接：`super("msg:" + str, e)` 应还原为 `+`，
 * 方法体内的拼接同理。
 */
class TestStringBuilderElimination : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestStringBuilderEliminationFixture.MyException::class.java))
			.code()
			.contains("MyException(String str, Exception e) {")
			.contains("super(\"msg:\" + str, e);")
			.doesNotContain("new StringBuilder")
			.contains("System.out.println(\"k=\" + k);")
	}
}

package kadx.tests.integration.others

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
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

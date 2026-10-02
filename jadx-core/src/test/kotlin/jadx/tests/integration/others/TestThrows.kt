package jadx.tests.integration.others

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * throws 声明：仅当方法体可能抛出受检异常时才应保留 `throws`，
 * 运行时异常与 `Error` 不应出现在 throws 列表中。
 */
class TestThrows : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestThrowsFixture.MissingThrowsTest::class.java))
			.code()
			.containsOne("throwCustomException() throws TestThrowsFixture\$MissingThrowsTest {")
			.containsOne("throwException() throws Exception {")
			.containsOne("throwRuntimeException1() {")
			.containsOne("throwRuntimeException2() {")
			.containsOne("throwError() {")
			.containsOne("throwError2() {")
			.containsOne("throwThrowable() throws Throwable {")
			.containsOne("exceptionSource() throws FileNotFoundException {")
			.containsOne("mergeThrownExceptions() throws IOException {")
			.containsOne("rethrowThrowable() {")
			.containsOne("noThrownExceptions1(InputStream i1) {")
			.containsOne("noThrownExceptions2() {")
			.containsOne("noThrownExceptions3() {")
	}
}

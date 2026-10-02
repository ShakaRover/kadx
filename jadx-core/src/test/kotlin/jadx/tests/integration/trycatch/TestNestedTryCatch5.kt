package jadx.tests.integration.trycatch

import jadx.NotYetImplemented
import jadx.tests.api.SmaliTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 循环推进中的 finally 提取（已知未实现）；关闭 finally 提取后 throw 数量应增加。
 */
class TestNestedTryCatch5 : SmaliTest() {

	@Test
	@NotYetImplemented("Extracting finally on loop advancement")
	fun test() {
		disableCompilation()
		assertThat(getClassNodeFromSmali())
			.code()
			.doesNotContain("?? ")
			.containsOne("} finally")
			.containsOne("endTransaction")
			.countString(1, "throw ") // 1 real throws, 1 implicit throw on finally handler and 1 implicit throw on empty ALL handler
	}

	@Test
	fun testNoFinally() {
		args.isExtractFinally = false
		disableCompilation()
		assertThat(getClassNodeFromSmali())
			.code()
			.doesNotContain("?? ")
			.countString(3, "throw ")
	}
}

package kadx.tests.integration.generics

import kadx.tests.api.SmaliTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 类签名非法（父类与自身相同）时：应输出告警信息，而不是栈溢出。
 */
class TestClassSignature : SmaliTest() {
	// @formatter:off
	/*
		Incorrect class signature, super class is equals to this class: <T:Ljava/lang/Object;>Lgenerics/TestClassSignature<TT;>;
	 */
	// @formatter:on

	@Test
	fun test() {
		allowWarnInCode()
		assertThat(getClassNodeFromSmali())
			.code()
			.containsOne("Incorrect class signature")
			.doesNotContain("StackOverflowError")
	}
}

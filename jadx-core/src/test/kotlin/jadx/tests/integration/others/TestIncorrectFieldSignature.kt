package jadx.tests.integration.others

import jadx.tests.api.SmaliTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 错误的字段签名：签名与实际类型不符时应输出修正类型并给出提示。
 */
class TestIncorrectFieldSignature : SmaliTest() {

	@Test
	fun test() {
		assertThat(getClassNodeFromSmali())
			.code()
			.containsOne("public static boolean A;")
			.containsOne("public static Boolean B;")
			.countString(2, "/* JADX INFO: Incorrect field signature:")
	}
}

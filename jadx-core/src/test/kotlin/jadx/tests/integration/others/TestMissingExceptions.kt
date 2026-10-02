package jadx.tests.integration.others

import jadx.tests.api.SmaliTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 缺失异常声明：catch 块中的 FileNotFoundException 应出现 6 次。
 */
class TestMissingExceptions : SmaliTest() {

	@Test
	fun test() {
		assertThat(getClassNodeFromSmali())
			.code()
			.countString(6, "FileNotFoundException")
	}
}

package kadx.tests.integration.trycatch

import kadx.tests.api.SmaliTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 嵌套 try-with-resources 产生不可达 catch：不应生成 `break;`，且两处 IOException catch 保留。
 */
class TestUnreachableCatch2 : SmaliTest() {

	@Test
	fun test() {
		// TODO: result code not compilable because 'try' block split into 2 block and 'fis' var become
		// uninitialized
		disableCompilation()
		assertThat(getClassNode(TestUnreachableCatch2Fixture.UnusedExceptionHandlers1::class.java))
			.code()
			.doesNotContain("break;")
			.countString(2, "} catch (IOException e")
	}
}

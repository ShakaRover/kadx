package kadx.tests.integration.names

import kadx.api.CommentsLevel
import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 类名与 `java.util.List` 冲突：内部类 `List` 与 java.util.List 同名时，
 * 字段类型与参数类型应分别使用短名和全限定名。
 */
class TestClassNamesCollision2 : IntegrationTest() {

	@Test
	fun test() {
		args.commentsLevel = CommentsLevel.WARN
		assertThat(getClassNode(TestClassNamesCollision2Fixture.TestCls::class.java))
			.code()
			.containsOne("static class List {")
			.containsOne("protected void clearList(java.util.List l) {")
	}
}

package jadx.tests.integration.names

import jadx.api.CommentsLevel
import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import jadx.tests.integration.names.pkg.a
import jadx.tests.integration.names.pkg.b
import org.junit.jupiter.api.Test

/**
 * 类名与包名冲突：包 `pkg` 中的类 `a` 与另一个类 `b` 的内部类 `a` 同名，
 * 反编译 `b` 时字段类型必须使用全限定名。
 */
class TestClassNamesCollision : IntegrationTest() {

	@Test
	fun test() {
		args.commentsLevel = CommentsLevel.WARN
		val classNodes = getClassNodes(a::class.java, b::class.java)

		assertThat(searchCls(classNodes, "a"))
			.code()
			.containsOne("public class a {")
			.containsOne("public static a a() {")

		assertThat(searchCls(classNodes, "b"))
			.code()
			.containsOne("class a {")
			.containsOne("jadx.tests.integration.names.pkg.a a = jadx.tests.integration.names.pkg.a.a();")
	}
}

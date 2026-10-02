package jadx.tests.integration.types

import jadx.api.CommentsLevel
import jadx.tests.api.SmaliTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 泛型出现在完整内部类（非静态）中时的类型还原，以及混淆重命名 / 全限定名模式下的编译通过性。
 */
class TestGenericsInFullInnerCls : SmaliTest() {

	@Test
	fun test() {
		getArgs().commentsLevel = CommentsLevel.WARN
		val classNodes = loadFromSmaliFiles()

		assertThat(searchCls(classNodes, "types.FieldCls"))
			.code()
			.containsOne("private ba<n>.bb<n, n> a;")

		assertThat(searchCls(classNodes, "types.test.ba"))
			.code()
			.containsOne("public final class ba<S> {")
			.containsOne("public final class bb<T, V extends n> {")
			.containsOne("private ba<S> b;")
			.containsOne("private ba<S>.bb<T, V>.bc<T, V> c;")
			.containsOne("public final class bc<T, V extends n> {")
			.containsOne("private ba<S> a;")
	}

	@Test
	fun testWithDeobf() {
		enableDeobfuscation()
		args.deobfuscationMinLength = 100 // rename everything

		getArgs().commentsLevel = CommentsLevel.WARN
		loadFromSmaliFiles()
		// compilation should pass
	}

	@Test
	fun testWithFullNames() {
		getArgs().isUseImports = false
		getArgs().commentsLevel = CommentsLevel.WARN
		loadFromSmaliFiles()
		// compilation should pass
	}
}

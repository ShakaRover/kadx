package kadx.tests.integration.names

import kadx.tests.api.SmaliTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test
import java.util.Collections

/**
 * 默认包重命名：位于默认包的类 A 会被移动到 `defpackage`，
 * 引用它的类 B 需生成正确的 import（关闭 import 时使用全限定名）。
 */
class TestDefPkgRename : SmaliTest() {

	@Test
	fun test() {
		val clsList = loadFromSmaliFiles()
		// class A moved to 'defpackage'
		assertThat(searchCls(clsList, "A"))
			.code()
			.containsOne("package defpackage;")
		assertThat(searchCls(clsList, "pkg.B"))
			.code()
			.containsOne("import defpackage.A;")
			.containsOne("public A a;")
	}

	@Test
	fun testNoImports() {
		args.isUseImports = false
		val clsList = loadFromSmaliFiles()
		// class A moved to 'defpackage', but use full names
		assertThat(searchCls(clsList, "A"))
			.code()
			.containsOne("package defpackage;")
		assertThat(searchCls(clsList, "pkg.B"))
			.code()
			.doesNotContain("import")
			.containsOne("public defpackage.A a;")
	}

	@Test
	fun testDeobf() {
		enableDeobfuscation()
		val clsList = loadFromSmaliFiles()
		// package for class A deobfuscated
		assertThat(searchCls(clsList, "pkg.B"))
			.code()
			.containsOne("import p000.C0000A;")
			.containsOne("public C0000A f0a;")
	}

	@Test
	fun testRenameDisabled() {
		disableCompilation()
		args.renameFlags = Collections.emptySet()
		val clsList = loadFromSmaliFiles()
		// no renaming, code will not compile
		assertThat(searchCls(clsList, "A"))
			.code()
			.containsOne("// default package")
		assertThat(searchCls(clsList, "pkg.B"))
			.code()
			.doesNotContain("import") // omit import
			.containsOne("public A a;")
	}
}

package kadx.tests.integration.special

import kadx.core.dex.nodes.ClassNode
import kadx.tests.api.SmaliTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test
import java.util.function.Consumer

/**
 * package-info 支持：包注解与包声明应保留，且 `package-info` 类不应被重命名。
 */
class TestPackageInfoSupport : SmaliTest() {

	@Test
	fun test() {
		disableCompilation()
		val classes: List<ClassNode> = loadFromSmaliFiles()
		assertThat(searchCls(classes, "special.pkg1.package-info"))
			.satisfies(Consumer<ClassNode> { cls -> assertThat(cls.alias).isEqualTo("package-info") }) // shouldn't be renamed
			.code()
			.containsLines(
				"@Deprecated",
				"package special.pkg1;",
			)
		assertThat(searchCls(classes, "special.pkg2.package-info"))
			.code()
			.containsLines(
				"@ApiStatus.Internal",
				"package special.pkg2;",
				"",
				"import org.jetbrains.annotations.ApiStatus;",
			)
		assertThat(searchCls(classes, "special.pkg3.package-info"))
			.code().isEqualTo("\npackage special.pkg3;\n")
	}
}

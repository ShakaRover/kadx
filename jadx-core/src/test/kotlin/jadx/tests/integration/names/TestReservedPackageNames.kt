package jadx.tests.integration.names

import jadx.tests.api.SmaliTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test
import java.util.Collections

/**
 * 保留字包名：`do.if` 含 Java 保留字，默认应被重命名；
 * 关闭重命名标志时应保留原始包名（即使代码无法编译）。
 */
class TestReservedPackageNames : SmaliTest() {

	// @formatter:off
	/*
		package do.if;

		public class A {}
	 */
	// @formatter:on

	@Test
	fun test() {
		val clsList = loadFromSmaliFiles()
		for (cls in clsList) {
			assertThat(cls).code().doesNotContain("package do.if;")
		}
	}

	@Test
	fun testDeobf() {
		enableDeobfuscation()
		val clsList = loadFromSmaliFiles()
		for (cls in clsList) {
			assertThat(cls).code().doesNotContain("package do.if;")
		}
	}

	@Test
	fun testRenameDisabled() {
		disableCompilation()
		args.renameFlags = Collections.emptySet()
		for (cls in loadFromSmaliFiles()) {
			if (cls.alias == "A") {
				assertThat(cls).code().contains("package do.if;")
			}
		}
	}
}

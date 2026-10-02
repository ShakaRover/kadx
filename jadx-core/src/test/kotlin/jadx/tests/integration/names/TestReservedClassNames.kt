package jadx.tests.integration.names

import jadx.tests.api.SmaliTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File

/**
 * 保留字类名：名为 `do` 的类无法作为合法 Java 标识符，反编译时应被重命名。
 */
class TestReservedClassNames : SmaliTest() {
	/*
	 * public class do {
	 * }
	 */

	@Test
	fun test() {
		assertThat(getClassNodeFromSmali("names" + File.separatorChar + "TestReservedClassNames", "do"))
			.code()
			.doesNotContain("public class do")
	}
}

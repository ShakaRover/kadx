package jadx.tests.integration.deobf.a

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test
import java.util.Collections

/**
 * 关闭所有重命名选项后，短名类 / 接口不应被重命名。
 */
class TestNegativeRenameCondition : IntegrationTest() {

	@Test
	fun test() {
		noDebugInfo()
		enableDeobfuscation()
		// disable rename by length
		args.deobfuscationMinLength = 0
		args.deobfuscationMaxLength = 999
		// disable all renaming options
		args.renameFlags = Collections.emptySet()

		assertThat(getClassNode(TestNegativeRenameConditionFixture.TestCls::class.java))
			.code()
			.doesNotContain("renamed from")
			.containsOne("package jadx.tests.integration.deobf.a;")
			.containsOne("public interface a {")
	}
}

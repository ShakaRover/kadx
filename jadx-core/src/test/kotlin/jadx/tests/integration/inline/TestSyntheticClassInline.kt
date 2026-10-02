package jadx.tests.integration.inline

import jadx.core.dex.nodes.ClassNode
import jadx.tests.api.SmaliTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 合成类内联：lambda 合成类应被内联为匿名 `Supplier`。
 */
class TestSyntheticClassInline : SmaliTest() {

	@Test
	fun test() {
		val classes: List<ClassNode> = loadFromSmaliFiles()
		assertThat(searchCls(classes, "inline.A"))
			.code()
			.containsOne("static Supplier<Long> test(final long x1, final long x2) {")
			.containsOne("return new Supplier() {")
			.containsOne("return A.lambda\$test\$0(x1, x2);")
	}
}

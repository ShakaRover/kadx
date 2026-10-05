package kadx.tests.integration.inner

import kadx.core.dex.nodes.ClassNode
import kadx.tests.api.SmaliTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test
import java.util.Collections

/**
 * 嵌套匿名类：Java 输入与 smali 输入都应还原出两层匿名类。
 */
class TestNestedAnonymousClass : SmaliTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestNestedAnonymousClassFixture.TestCls::class.java))
			.code()
			.containsOne("use(new Callable<Runnable>() {")
			.containsOne("return new Runnable() {")
	}

	@Test
	fun testSmali() {
		getArgs().renameFlags = Collections.emptySet()
		val classes: List<ClassNode> = loadFromSmaliFiles()
		assertThat(searchCls(classes, "A"))
			.code()
			.containsOne("use(new Callable<Runnable>() {")
			.containsOne("return new Runnable() {")
	}
}

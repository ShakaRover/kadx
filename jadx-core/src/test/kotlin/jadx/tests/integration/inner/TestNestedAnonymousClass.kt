package jadx.tests.integration.inner

import jadx.core.dex.nodes.ClassNode
import jadx.tests.api.SmaliTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
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

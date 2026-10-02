package jadx.tests.integration.inline

import jadx.core.dex.attributes.AFlag
import jadx.core.dex.nodes.ClassNode
import jadx.core.utils.ListUtils
import jadx.tests.api.SmaliTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 实例方法引用的 lambda（`Lambda$1.INSTANCE`）：默认应内联为匿名 `Function`，
 * 关闭匿名类内联后则还原成具名 `Lambda$1` 类。
 */
class TestInstanceLambda : SmaliTest() {

	@Test
	fun test() {
		useJavaInput()
		noDebugInfo()
		assertThat(getClassNode(TestInstanceLambdaFixture.TestCls::class.java))
			.code()
	}

	@Test
	fun testSmaliDisableInline() {
		getArgs().isInlineAnonymousClasses = false
		val classNodes: List<ClassNode> = loadFromSmaliFiles()
		assertThat(searchTestCls(classNodes, "Lambda\$1"))
			.code()
			.containsOne("class Lambda\$1<T> implements Function<T, T> {")
		assertThat(searchTestCls(classNodes, "TestCls"))
			.code()
			.containsOne("Lambda\$1.INSTANCE")
	}

	@Test
	fun testSmali() {
		val classNodes: List<ClassNode> = loadFromSmaliFiles()
		assertThat(ListUtils.filter(classNodes) { c -> !c.contains(AFlag.DONT_GENERATE) })
			.describedAs("Expect lambda to be inlined")
			.hasSize(1)
		assertThat(searchTestCls(classNodes, "TestCls"))
			.code()
			.doesNotContain("Lambda\$1.INSTANCE")
			.containsOne("toMap(list, new Function<T, T>() {")
	}
}

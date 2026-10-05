package kadx.tests.integration.inner

import kadx.api.CommentsLevel
import kadx.core.dex.nodes.ClassNode
import kadx.core.dex.nodes.MethodNode
import kadx.core.utils.ListUtils
import kadx.tests.api.SmaliTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * smali 用例：外层类实现 Runnable，内部同时存在匿名类和普通内部类，
 * 构造函数应各保留一个且不残留 synthetic 标记。
 */
class TestAnonymousClass14 : SmaliTest() {
	// @formatter:off
	/*
		public class OuterCls implements Runnable {

			class TestCls {
				private TestCls() {
					new ArrayList();
				}
			}

			public void makeAnonymousCls() {
				use(new Thread(this) {
				/ * class inner.OuterCls.AnonymousClass1 * /

					public void someMethod() {
					}
				});
			}

			public void makeTestCls() {
				new TestCls();
			}

			public void run() {
			}

			public void use(Thread thread) {
			}
		}
	 */
	// @formatter:on

	@Test
	fun test() {
		getArgs().commentsLevel = CommentsLevel.WARN
		val outerCls: ClassNode = getClassNodeFromSmaliFiles("OuterCls")
		assertThat(outerCls).code()
			.doesNotContain("synthetic", "AnonymousClass1")
			.describedAs("only one constructor").containsOne("private TestCls(")
			.describedAs("constructor without args").containsOne("private TestCls() {")

		val makeTestClsMth: MethodNode = checkNotNull(outerCls.searchMethodByShortName("makeTestCls"))
		assertThat(makeTestClsMth).isNotNull()

		val testCls = searchCls(outerCls.innerClasses, "TestCls")
		val ctrMth = checkNotNull(
			ListUtils.filterOnlyOne(testCls.methods) { m ->
				m.isConstructor() && !m.accessFlags.isSynthetic()
			},
		)
		assertThat(ctrMth).isNotNull()
		assertThat(ctrMth.useIn).hasSize(1)
		assertThat(ctrMth.useIn[0]).isEqualTo(makeTestClsMth)

		assertThat(outerCls).checkCodeAnnotationFor("new TestCls();", 4, ctrMth)
	}
}

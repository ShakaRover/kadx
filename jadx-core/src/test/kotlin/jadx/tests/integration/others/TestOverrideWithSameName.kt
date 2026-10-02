package jadx.tests.integration.others

import jadx.core.dex.attributes.AType
import jadx.core.dex.nodes.IMethodDetails
import jadx.tests.api.SmaliTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test
import java.util.function.Consumer

/**
 * 同名方法覆写：协变返回类型导致方法重命名后，`@Override` 与覆写列表仍应正确。
 */
@Suppress("CommentedOutCode")
class TestOverrideWithSameName : SmaliTest() {

	// @formatter:off
	/*
		interface A {
			B a();
			C a();
		}

		abstract class B implements A {
			@Override
			public C a() {
				return null;
			}
		}

		public class C extends B {
			@Override
			public B a() {
				return null;
			}
		}
	 */
	// @formatter:on

	@Test
	fun test() {
		val clsNodes = loadFromSmaliFiles()
		assertThat(searchCls(clsNodes, "test.A"))
			.code()
			.containsOne("C mo0a();") // assume second method was renamed
			.doesNotContain("@Override")

		val bCls = searchCls(clsNodes, "test.B")
		assertThat(bCls)
			.code()
			.containsOne("C mo0a() {")
			.containsOne("@Override")

		assertThat(checkNotNull(getMethod(bCls, "a").get(AType.METHOD_OVERRIDE)).overrideList)
			.singleElement()
			.satisfies(Consumer<IMethodDetails> { mth -> assertThat(mth.getMethodInfo().declClass.shortName).isEqualTo("A") })

		val cCls = searchCls(clsNodes, "test.C")
		assertThat(cCls)
			.code()
			.containsOne("B a() {")
			.containsOne("@Override")

		assertThat(checkNotNull(getMethod(cCls, "a").get(AType.METHOD_OVERRIDE)).overrideList)
			.singleElement()
			.satisfies(Consumer<IMethodDetails> { mth -> assertThat(mth.getMethodInfo().declClass.shortName).isEqualTo("A") })
	}
}

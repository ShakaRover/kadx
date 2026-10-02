package jadx.tests.integration.generics

import jadx.core.dex.nodes.ClassNode
import jadx.tests.api.SmaliTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 桥接方法与真实方法签名冲突时：应重命名为 `invoke`/`invoke2` 加以区分。
 */
class TestSyntheticOverride : SmaliTest() {
	// @formatter:off
	/*
		final class TestSyntheticOverride extends Lambda implements Function1<String, Unit> {

			// fixing method types to match interface (i.e Unit invoke(String str))
			// make duplicate methods signatures
			public bridge synthetic Object invoke(Object str) {
				invoke(str);
				return Unit.INSTANCE;
			}

			public final void invoke(String str) {
				...
			}
		}
	 */
	// @formatter:on

	@Test
	fun test() {
		allowWarnInCode()
		disableCompilation()
		val classNodes: List<ClassNode> = loadFromSmaliFiles()
		assertThat(searchCls(classNodes, "TestSyntheticOverride"))
			.code()
			.containsOne("invoke(String str)")
			.containsOne("invoke2(String str)")
	}
}

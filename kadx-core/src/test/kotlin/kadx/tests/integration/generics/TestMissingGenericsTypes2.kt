package kadx.tests.integration.generics

import kadx.tests.api.SmaliTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * smali 中丢失泛型信息时：迭代器变量不应显示为裸 `Iterator i`，
 * 启用循环区域优化后应还原为 for-each，禁用后保留 `Iterator<String>` 类型。
 */
class TestMissingGenericsTypes2 : SmaliTest() {
	// @formatter:off
	/*
	package generics;

	import java.util.Iterator;

	public class TestMissingGenericsTypes2<T> implements Iterable<T> {

		@Override
		public Iterator<T> iterator() {
			return null;
		}

		public void test(TestMissingGenericsTypes2<String> l) {
			Iterator<String> i = l.iterator(); // <-- This generics type was removed in smali
			while (i.hasNext()) {
				String s = i.next();
				doSomething(s);
			}
		}

		private void doSomething(String s) {
		}
	}
	 */
	// @formatter:on

	@Test
	fun test() {
		assertThat(getClassNodeFromSmali())
			.code()
			.doesNotContain("Iterator i")
			.containsOne("for (String s : l) {")
	}

	@Test
	fun testTypes() {
		// prevent loop from converting to 'for-each' to keep iterator variable type in code
		getArgs().disabledPasses.add("LoopRegionVisitor")
		assertThat(getClassNodeFromSmali())
			.code()
			.doesNotContain("Iterator i")
			.containsOne("Iterator<String> it = ") // variable name reject along with type
	}
}

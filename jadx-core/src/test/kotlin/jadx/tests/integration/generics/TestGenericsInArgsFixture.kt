package jadx.tests.integration.generics

object TestGenericsInArgsFixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.generics;

import java.util.List;
import java.util.Set;

public class TestGenericsInArgsFixture {

	public static class TestCls {
		public static <T> void test(List<? super T> genericList, Set<T> set) {
			if (genericList == null) {
				throw new RuntimeException("list is null");
			}
			if (set == null) {
				throw new RuntimeException("set is null");
			}
			genericList.clear();
			use(genericList);
			set.clear();
		}

		private static void use(List<?> l) {
		}
	}
}
"""
}

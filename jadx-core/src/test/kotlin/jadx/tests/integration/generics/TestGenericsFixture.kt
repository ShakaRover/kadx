package jadx.tests.integration.generics

object TestGenericsFixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.generics;

import java.util.List;

public class TestGenericsFixture {

	public static class TestCls {
		class A {
		}

		public static void mthWildcard(List<?> list) {
		}

		public static void mthExtends(List<? extends A> list) {
		}

		public static void mthSuper(List<? super A> list) {
		}
	}
}
"""
}

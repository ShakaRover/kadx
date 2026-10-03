package jadx.tests.integration.generics

object TestGenerics6Fixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.generics;

import java.util.Collection;

public class TestGenerics6Fixture {

	public static class TestCls {
		public void test1(Collection<? extends A> as) {
			for (A a : as) {
				a.f();
			}
		}

		public void test2(Collection<? extends A> is) {
			for (I i : is) {
				i.f();
			}
		}

		private interface I {
			void f();
		}

		private class A implements I {
			public void f() {
			}
		}
	}
}
"""
}

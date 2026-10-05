package kadx.tests.integration.generics

object TestOuterGenericFixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.generics;

import java.util.Set;

public class TestOuterGenericFixture {

	public static class TestCls {
		public static class A<T> {
			public class B<V> {
			}

			public class C {
			}
		}

		public static class D {
			public class E {
			}
		}

		public void test1() {
			A<String> a = new A<>();
			use(a);
			A<String>.B<Exception> b = a.new B<Exception>();
			use(b);
			use(b);
			A<String>.C c = a.new C();
			use(c);
			use(c);

			use(new A<Set<String>>().new C());
		}

		public void test2() {
			D d = new D();
			D.E e = d.new E();
			use(e);
			use(e);
		}

		public void test3() {
			use(A.class);
			use(A.B.class);
			use(A.C.class);
		}

		private void use(Object obj) {
		}
	}
}
"""
}

package jadx.tests.integration.generics

object TestGenericsMthOverrideFixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.generics;

public class TestGenericsMthOverrideFixture {

	public static class TestCls {
		public interface I<X, Y> {
			Y method(X x);
		}

		public static class A<X, Y> implements I<X, Y> {
			@Override
			public Y method(X x) {
				return null;
			}
		}

		public static class B<X, Y> implements I<X, Y> {
			@Override
			public Y method(Object x) {
				return null;
			}
		}

		public static class C<X extends Exception, Y> implements I<X, Y> {
			@Override
			public Y method(Exception x) {
				return null;
			}
		}

		@SuppressWarnings("unchecked")
		public static class D<X, Y> implements I<X, Y> {
			@Override
			public Object method(Object x) {
				return null;
			}
		}
	}
}
"""
}

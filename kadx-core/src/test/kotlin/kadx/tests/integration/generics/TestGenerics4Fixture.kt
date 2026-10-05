package kadx.tests.integration.generics

object TestGenerics4Fixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.generics;

public class TestGenerics4Fixture {

	public static class TestCls {

		public static Class<?> method(int i) {
			Class<?>[] a = new Class<?>[0];
			return a[a.length - i];
		}
	}
}
"""
}

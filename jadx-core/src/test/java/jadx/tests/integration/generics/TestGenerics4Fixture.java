package jadx.tests.integration.generics;

public class TestGenerics4Fixture {

	public static class TestCls {

		public static Class<?> method(int i) {
			Class<?>[] a = new Class<?>[0];
			return a[a.length - i];
		}
	}
}

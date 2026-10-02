package jadx.tests.integration.generics;

public class TestGenerics7Fixture {

	public static class TestCls {

		public void test() {
			declare(String.class);
		}

		public <T> T declare(Class<T> cls) {
			return null;
		}

		public void declare(Object cls) {
		}
	}
}

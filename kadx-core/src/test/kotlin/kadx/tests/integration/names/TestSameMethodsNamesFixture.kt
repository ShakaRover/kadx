package kadx.tests.integration.names

object TestSameMethodsNamesFixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.names;

public class TestSameMethodsNamesFixture {

	public static class TestCls<V> {

		public static void test() {
			new Bug().Bug();
		}

		public static class Bug {
			public Bug() {
				System.out.println("constructor");
			}

			@SuppressWarnings({ "MethodName", "MethodNameSameAsClassName" })
			void Bug() {
				System.out.println("Bug");
			}
		}
	}
}
"""
}

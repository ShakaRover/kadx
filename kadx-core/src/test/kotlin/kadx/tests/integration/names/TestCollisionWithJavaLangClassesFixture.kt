package kadx.tests.integration.names

object TestCollisionWithJavaLangClassesFixture {
	class TestCls1
	class TestCls2

	const val JAVA_SOURCE = """package kadx.tests.integration.names;

public class TestCollisionWithJavaLangClassesFixture {

	public static class TestCls1 {
		public static class System {
			public static void main(String[] args) {
				java.lang.System.out.println("Hello world");
			}
		}
	}

	public static class TestCls2 {
		public void doSomething() {
			System.doSomething();
			java.lang.System.out.println("Hello World");
		}

		public static class System {
			public static void doSomething() {
			}
		}
	}
}
"""
}

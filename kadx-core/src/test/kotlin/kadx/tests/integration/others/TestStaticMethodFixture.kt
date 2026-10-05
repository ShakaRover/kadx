package kadx.tests.integration.others

object TestStaticMethodFixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.others;

public class TestStaticMethodFixture {

	public static class TestCls {
		static {
			f();
		}

		private static void f() {
		}
	}
}
"""
}

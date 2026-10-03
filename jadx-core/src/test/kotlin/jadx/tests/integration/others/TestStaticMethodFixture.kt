package jadx.tests.integration.others

object TestStaticMethodFixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.others;

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

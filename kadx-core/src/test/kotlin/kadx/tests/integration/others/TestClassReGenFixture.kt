package kadx.tests.integration.others

object TestClassReGenFixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.others;

public class TestClassReGenFixture {

	public static class TestCls {
		private int intField = 5;

		public static class A {
		}

		public int test() {
			return 0;
		}
	}
}
"""
}

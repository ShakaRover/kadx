package kadx.tests.integration.conditions

object TestTernaryInIfFixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.conditions;

public class TestTernaryInIfFixture {

	public static class TestCls {
		public boolean test1(boolean a, boolean b, boolean c) {
			return a ? b : c;
		}

		public int test2(boolean a, boolean b, boolean c) {
			return (!a ? c : b) ? 1 : 2;
		}
	}
}
"""
}

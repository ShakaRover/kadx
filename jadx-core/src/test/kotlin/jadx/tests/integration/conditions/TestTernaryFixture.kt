package jadx.tests.integration.conditions

object TestTernaryFixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.conditions;

public class TestTernaryFixture {

	public static class TestCls {
		public boolean test1(int a) {
			return a != 2;
		}

		public void test2(int a) {
			checkTrue(a == 3);
		}

		public int test3(int a) {
			return a > 0 ? a : (a + 2) * 3;
		}

		private static void checkTrue(boolean v) {
		}
	}
}
"""
}

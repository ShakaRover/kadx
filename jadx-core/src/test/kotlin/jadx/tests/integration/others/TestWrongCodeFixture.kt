package jadx.tests.integration.others

object TestWrongCodeFixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.others;

public class TestWrongCodeFixture {

	public static class TestCls {
		@SuppressWarnings("null")
		public int test() {
			int[] a = null;
			return a.length;
		}

		public int test2(int a) {
			if (a == 0) {
			}
			return a;
		}
	}
}
"""
}

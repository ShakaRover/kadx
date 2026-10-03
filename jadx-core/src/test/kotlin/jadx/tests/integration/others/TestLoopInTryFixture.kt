package jadx.tests.integration.others

object TestLoopInTryFixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.others;

public class TestLoopInTryFixture {

	public static class TestCls {
		private static boolean b = true;

		public int test() {
			try {
				if (b) {
					throw new Exception();
				}
				while (f()) {
					s();
				}
			} catch (Exception e) {
				System.out.println("exception");
				return 1;
			}
			return 0;
		}

		private static void s() {
		}

		private static boolean f() {
			return false;
		}
	}
}
"""
}

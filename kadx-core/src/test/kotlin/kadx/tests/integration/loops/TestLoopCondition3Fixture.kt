package kadx.tests.integration.loops

object TestLoopCondition3Fixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.loops;

public class TestLoopCondition3Fixture {

	public static class TestCls {

		public static void test(int a, int b, int c) {
			while (a < 12) {
				if (b + a < 9 && b < 8) {
					if (b >= 2 && a > -1 && b < 6) {
						System.out.println("OK");
						c = b + 1;
					}
					b = a;
				}
				c = b;
				b++;
				b = c;
				a++;
			}
		}
	}
}
"""
}

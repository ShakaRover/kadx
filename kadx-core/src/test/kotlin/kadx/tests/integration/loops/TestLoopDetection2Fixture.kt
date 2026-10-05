package kadx.tests.integration.loops

object TestLoopDetection2Fixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.loops;

public class TestLoopDetection2Fixture {

	public static class TestCls {

		public int test(int a, int b) {
			int c = a + b;
			for (int i = a; i < b; i++) {
				if (i == 7) {
					c += 2;
				} else {
					c *= 2;
				}
			}
			c--;
			return c;
		}
	}
}
"""
}

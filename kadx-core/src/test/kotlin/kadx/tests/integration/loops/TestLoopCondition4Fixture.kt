package kadx.tests.integration.loops

object TestLoopCondition4Fixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.loops;

public class TestLoopCondition4Fixture {

	public static class TestCls {
		public static void test() {
			int n = -1;
			while (n < 0) {
				n += 12;
			}
			while (n > 11) {
				n -= 12;
			}
			System.out.println(n);
		}
	}
}
"""
}

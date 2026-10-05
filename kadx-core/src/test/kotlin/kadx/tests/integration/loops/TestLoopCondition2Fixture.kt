package kadx.tests.integration.loops

object TestLoopCondition2Fixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.loops;

public class TestLoopCondition2Fixture {

	public static class TestCls {

		public int test(boolean a) {
			int i = 0;
			while (a && i < 10) {
				i++;
			}
			return i;
		}
	}
}
"""
}

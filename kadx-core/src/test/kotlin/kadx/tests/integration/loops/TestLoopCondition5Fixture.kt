package kadx.tests.integration.loops

object TestLoopCondition5Fixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.loops;

public class TestLoopCondition5Fixture {

	public static class TestCls {
		public static int lastIndexOf(int[] array, int target, int start, int end) {
			for (int i = end - 1; i >= start; i--) {
				if (array[i] == target) {
					return i;
				}
			}
			return -1;
		}
	}
}
"""
}

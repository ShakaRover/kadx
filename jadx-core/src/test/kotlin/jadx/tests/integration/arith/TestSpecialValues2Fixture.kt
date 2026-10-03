package jadx.tests.integration.arith

object TestSpecialValues2Fixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.arith;

public class TestSpecialValues2Fixture {

	public static class TestCls {
		private static int compareUnsigned(final int x, final int y) {
			return Integer.compare(x + Integer.MIN_VALUE, y + Integer.MIN_VALUE);
		}
	}
}
"""
}

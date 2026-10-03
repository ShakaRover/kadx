package jadx.tests.integration.conditions

object TestCmpOp2Fixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.conditions;

public class TestCmpOp2Fixture {

	public static class TestCls {
		public boolean testGT(float a, float b) {
			return a > b;
		}

		public boolean testLT(float c, double d) {
			return c < d;
		}
	}
}
"""
}

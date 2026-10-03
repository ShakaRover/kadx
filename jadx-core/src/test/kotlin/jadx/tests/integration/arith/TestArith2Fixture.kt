package jadx.tests.integration.arith

object TestArith2Fixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.arith;

public class TestArith2Fixture {

	public static class TestCls {

		public int test1(int a) {
			return (a + 2) * 3;
		}

		public int test2(int a, int b, int c) {
			return a + b + c;
		}

		public boolean test3(boolean a, boolean b, boolean c) {
			return a | b | c;
		}

		public boolean test4(boolean a, boolean b, boolean c) {
			return a & b & c;
		}

		public int substract(int a, int b, int c) {
			return a - (b - c);
		}

		public int divide(int a, int b, int c) {
			return a / (b / c);
		}
	}
}
"""
}

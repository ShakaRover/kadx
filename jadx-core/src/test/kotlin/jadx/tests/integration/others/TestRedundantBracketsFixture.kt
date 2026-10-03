package jadx.tests.integration.others

object TestRedundantBracketsFixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.others;

public class TestRedundantBracketsFixture {

	public static class TestCls {
		public boolean method(String str) {
			return str.indexOf('a') != -1;
		}

		public int method2(Object obj) {
			if (obj instanceof String) {
				return ((String) obj).length();
			}
			return 0;
		}

		public int method3(int a, int b) {
			if (a + b < 10) {
				return a;
			}
			if ((a & b) != 0) {
				return a * b;
			}
			return b;
		}

		public void method4(int num) {
			if (num == 4 || num == 6 || num == 8 || num == 10) {
				method2(null);
			}
		}

		public void method5(int[] a, int n) {
			a[1] = n * 2;
			a[n - 1] = 1;
		}
	}
}
"""
}

package jadx.tests.integration.arith;

public class TestArithFixture {

	public static class TestCls {

		public static final int F = 7;

		public int test(int a) {
			a += 2;
			use(a);
			return a;
		}

		public int test2(int a) {
			a++;
			use(a);
			return a;
		}

		private static void use(int i) {
		}
	}
}

package jadx.tests.integration.arith;

public class TestFieldIncrement2Fixture {

	public static class TestCls {
		private static class A {
			int f = 5;
		}

		public A a;

		public void test1(int n) {
			this.a.f = this.a.f + n;
		}

		public void test2(int n) {
			this.a.f *= n;
		}
	}
}

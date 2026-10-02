package jadx.tests.integration.inline;

public class TestInline6Fixture {

	public static class TestCls {
		public void f() {
		}

		public void test(int a, int b) {
			long start = System.nanoTime();
			f();
			System.out.println(System.nanoTime() - start);
		}
	}
}

package jadx.tests.integration.others;

public class TestStaticMethodFixture {

	public static class TestCls {
		static {
			f();
		}

		private static void f() {
		}
	}
}

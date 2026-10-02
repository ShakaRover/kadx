package jadx.tests.integration.types;

public class TestTypeResolverFixture {

	public static class TestCls {
		public TestCls(int b1, int b2) {
			// test 'this' move and constructor invocation on moved register
			this(b1, b2, 0, 0, 0);
		}

		public TestCls(int a1, int a2, int a3, int a4, int a5) {
		}
	}
}

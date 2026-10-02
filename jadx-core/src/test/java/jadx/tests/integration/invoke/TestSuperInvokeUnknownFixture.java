package jadx.tests.integration.invoke;

public class TestSuperInvokeUnknownFixture {

	public static class TestCls {
		public static class BaseClass {
			public int doSomething() {
				return 0;
			}
		}

		public static class NestedClass extends BaseClass {
			@Override
			public int doSomething() {
				return super.doSomething();
			}
		}
	}
}

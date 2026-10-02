package jadx.tests.integration.inner;

public class TestAnonymousClass12Fixture {

	public static class TestCls {

		public abstract static class BasicAbstract {
			public abstract void doSomething();
		}

		public BasicAbstract outer;
		public BasicAbstract inner;

		public void test() {
			outer = new BasicAbstract() {
				@Override
				public void doSomething() {
					inner = new BasicAbstract() {
						@Override
						public void doSomething() {
							inner = null;
						}
					};
				}
			};
		}
	}
}
